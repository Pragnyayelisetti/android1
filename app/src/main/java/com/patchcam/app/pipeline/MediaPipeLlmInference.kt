package com.patchcam.app.pipeline

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.patchcam.app.models.Diagnosis
import com.patchcam.app.models.MatchMethod
import com.patchcam.app.models.ModelState
import com.patchcam.app.models.PatchCandidate
import org.json.JSONObject

private const val TAG = "PatchCamLLM"

/**
 * Thrown so callers can tell "model isn't provisioned on this device" apart
 * from every other kind of inference failure, instead of catching a bare
 * generic exception for both.
 */
class ModelNotProvisionedException(message: String) : Exception(message)

/**
 * On-device LLM fallback for PatchCam.
 *
 * Important:
 * - Uses only APIs available in the current MediaPipe LLM Inference setup.
 * - Does NOT use setTemperature(), because that API is not available in
 *   the dependency used by this project.
 * - LLM-generated patches are never automatically marked as verified.
 * - Invalid/malformed model output is converted into a safe PatchCandidate
 *   instead of crashing the application.
 */
class MediaPipeLlmInference private constructor(
    private val llm: LlmInference,
    private val appContext: Context
) {

    companion object {

        @Volatile
        private var INSTANCE: MediaPipeLlmInference? = null

        @Volatile
        private var loading: Boolean = false

        /**
         * Resolves the model file via ModelLocator (app-owned directory
         * first, then the legacy /data/local/tmp debug path) unless an
         * explicit modelPath override is given, and creates the MediaPipe
         * inference session from it.
         *
         * Throws ModelNotProvisionedException — instead of letting a
         * generic RuntimeException from MediaPipe surface — when the model
         * file cannot be found anywhere PatchCam knows to look, so callers
         * can show ModelState.NotLoaded rather than a mystery failure.
         */
        fun getInstance(
            context: Context,
            modelPath: String? = null
        ): MediaPipeLlmInference {

            INSTANCE?.let { return it }

            return synchronized(this) {
                INSTANCE?.let { return@synchronized it }

                val resolvedPath = modelPath
                    ?: ModelLocator.resolveModelFile(context)?.absolutePath
                    ?: throw ModelNotProvisionedException(
                        "No on-device model file was found at " +
                                "${ModelLocator.preferredModelFile(context).absolutePath} " +
                                "or the legacy debug path. Provision the model before using " +
                                "offline chat or patch generation."
                    )

                loading = true
                try {
                    val options =
                        LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(resolvedPath)
                            .setMaxTokens(512)
                            .build()

                    val inference =
                        LlmInference.createFromOptions(
                            context.applicationContext,
                            options
                        )

                    MediaPipeLlmInference(inference, context.applicationContext).also {
                        INSTANCE = it
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize on-device LLM from $resolvedPath", e)
                    throw e
                } finally {
                    loading = false
                }
            }
        }

        /**
         * Best-effort model state without forcing initialization, for the
         * UI to show something more honest than a raw exception message.
         */
        fun currentModelState(context: Context): ModelState {
            if (INSTANCE != null) return ModelState.Ready
            if (loading) return ModelState.Loading
            if (!ModelLocator.isModelAvailable(context)) return ModelState.NotLoaded
            return ModelState.NotLoaded
        }

        /**
         * Classifies an inference failure into a ModelState, logging the
         * real underlying exception rather than discarding it. This is
         * what replaces the previous single generic catch-all message.
         */
        fun classifyFailure(context: Context, error: Throwable): ModelState {
            Log.e(TAG, "On-device LLM failure", error)

            if (error is ModelNotProvisionedException || !ModelLocator.isModelAvailable(context)) {
                return ModelState.NotLoaded
            }

            val message = (error.message ?: "").lowercase()
            return when {
                message.contains("out of memory") ||
                        message.contains("oom") ||
                        error is OutOfMemoryError ->
                    ModelState.OutOfMemory(error.message)

                message.contains("context") &&
                        (message.contains("too long") || message.contains("exceed") || message.contains("token")) ->
                    ModelState.ContextTooLarge()

                else ->
                    ModelState.GenerationFailed(error.message)
            }
        }
    }


    /**
     * Contextual debugging chat.
     *
     * Unlike generatePatch(), this method is not asked to invent a
     * correction. It receives the already-detected diagnosis and answers
     * the user's follow-up question using that evidence.
     */
    fun chat(
        diagnosis: Diagnosis,
        question: String
    ): String {
        val prompt = buildChatPrompt(diagnosis, question)

        val result = runCatching {
            llm.generateResponse(prompt).trim()
        }

        val text = result.getOrElse { error ->
            // Log + classify instead of collapsing every failure into the
            // same generic sentence. The real exception is preserved in
            // Logcat (tag "PatchCamLLM") for diagnosis.
            return MediaPipeLlmInference.classifyFailure(appContext, error).userMessage()
        }

        if (text.isBlank()) {
            Log.w(TAG, "On-device LLM returned an empty response for question: $question")
            return ModelState.EmptyResponse.userMessage()
        }

        return text
    }

    /**
     * Same as [chat], but returns null instead of an error sentence when
     * the model fails or answers with nothing. Used by the app so that a
     * model problem falls back to PatchCam's offline answer rather than
     * being shown to the user as if it were the answer.
     */
    fun chatOrNull(
        diagnosis: Diagnosis,
        question: String
    ): String? {
        val prompt = buildChatPrompt(diagnosis, question)

        val text = runCatching {
            llm.generateResponse(prompt).trim()
        }.onFailure { error ->
            Log.e(TAG, "On-device LLM chat failed", error)
        }.getOrNull()

        return text?.takeIf { it.isNotBlank() }
    }

    private fun buildChatPrompt(
        diagnosis: Diagnosis,
        question: String
    ): String {
        val history = diagnosis.chat
            .takeLast(8)
            .joinToString("\n") {
                val speaker = if (it.role.name == "USER") "USER" else "PATCHCAM"
                "$speaker: ${it.text}"
            }

        val patchText = diagnosis.patch?.let {
            "old=${it.oldCode}\nnew=${it.newCode}\ndescription=${it.description}\nverified=${it.verified}"
        } ?: "No patch was generated."

        val validationText = diagnosis.validation?.let {
            "valid=${it.valid}, error=${it.errorMessage}, line=${it.errorLine}, sourceLine=${it.sourceLine}"
        } ?: "No patch validation was performed."

        // Local RAG: solved cases retrieved from PatchCam's own knowledge base.
        val retrieved = LocalRetriever.contextFor(
            question + " " + diagnosis.errorText,
            diagnosis.code
        )

        return """
            You are PatchCam, a careful programming-debugging assistant.

            Your job is to answer the user's question about the CURRENT
            debugging session. You are not starting a new scan.

            RULES:
            1. Use the supplied source, parser evidence, diagnosis and conversation.
            2. Never invent code that is not present in the supplied source.
            3. Clearly distinguish confirmed facts from likely explanations.
            4. Explain technical terms in simple language when useful.
            5. If the user asks about a fix, explain the existing proposed patch first.
            6. Do not claim a patch is verified unless verified=true.
            7. If the supplied source is insufficient, say exactly what is missing.
            8. Answer the user's actual question directly.
            9. Do not output JSON. Use normal helpful text.
            10. Keep the answer focused, but provide enough detail for a developer
                to understand what is happening.

            CURRENT DIAGNOSIS
            Category: ${diagnosis.category}
            Error: ${diagnosis.errorText}
            Root cause: ${diagnosis.rootCause}
            Confidence: ${diagnosis.confidence}%
            Source:
            ${diagnosis.code}

            EXPLANATION
            What went wrong: ${diagnosis.explanation.whatWentWrong}
            Why it happened: ${diagnosis.explanation.whyItHappened}
            How to fix: ${diagnosis.explanation.howToFix}

            PATCH
            $patchText

            PATCH VALIDATION
            $validationText

            ${retrieved.ifBlank { "No similar solved cases were retrieved." }}

            PREVIOUS CONVERSATION
            ${history.ifBlank { "No previous conversation." }}

            USER QUESTION
            $question
        """.trimIndent()
    }

    /**
     * Generates a suggested patch.
     *
     * Python:
     *   Used as a candidate for later AST validation.
     *
     * Other languages:
     *   Returned as an unverified suggestion.
     */
    fun generatePatch(
        codeText: String,
        errorText: String,
        isPython: Boolean,
        /** Similar solved cases retrieved locally (see LocalRetriever). */
        retrievedContext: String = "",
        /** When a previous attempt was rejected by the parser, why. */
        parserFeedback: String = ""
    ): PatchCandidate {

        val prompt = buildPrompt(
            codeText = codeText,
            errorText = errorText,
            isPython = isPython,
            retrievedContext = retrievedContext,
            parserFeedback = parserFeedback
        )

        val rawResponse = runCatching {
            llm.generateResponse(prompt)
        }.getOrElse { exception ->
            val state = classifyFailure(appContext, exception)
            return safeFailurePatch(
                isPython = isPython,
                reason = state.userMessage()
            )
        }

        return parseResponse(
            rawResponse = rawResponse,
            isPython = isPython
        )
    }

    /**
     * Builds a deliberately constrained prompt.
     *
     * The model is asked for JSON only so the Android layer can parse
     * the response deterministically.
     */
    private fun buildPrompt(
        codeText: String,
        errorText: String,
        isPython: Boolean,
        retrievedContext: String = "",
        parserFeedback: String = ""
    ): String {

        val languageInstruction =
            if (isPython) {
                """
                Language: Python.

                Rules:
                1. Identify the most likely root cause.
                2. Produce the smallest safe code change.
                3. Preserve the user's existing logic.
                4. Never use tabs.
                5. Use four spaces for Python indentation.
                6. Do not rewrite unrelated code.
                7. Return only JSON.
                """.trimIndent()
            } else {
                """
                Language: Other programming language.

                Rules:
                1. Identify the most likely root cause.
                2. Produce the smallest reasonable correction.
                3. Do not rewrite unrelated code.
                4. This suggestion cannot be considered compiler-verified.
                5. Return only JSON.
                """.trimIndent()
            }

        return """
            You are PatchCam, a programming-error analysis assistant.

            $languageInstruction

            Required JSON format:

            {
              "targetLine": 1,
              "oldCode": "existing code",
              "newCode": "corrected code",
              "description": "simple explanation of the correction"
            }

            Requirements:
            - targetLine must be an integer.
            - oldCode must contain the exact code that should be replaced when possible.
            - newCode must contain the corrected code.
            - description must explain the fix in simple language.
            - Do not use Markdown.
            - Do not use code fences.
            - Do not add text before or after the JSON.

            ${retrievedContext.ifBlank { "" }}
            ${
                if (parserFeedback.isBlank()) ""
                else "YOUR PREVIOUS ATTEMPT WAS REJECTED by Python's parser: $parserFeedback\n            Fix that problem in your new answer."
            }

            CODE:
            $codeText

            ERROR:
            $errorText
        """.trimIndent()
    }

    /**
     * Parses the model response safely.
     *
     * Gemma can occasionally return explanatory text around the JSON.
     * This method extracts the JSON object without allowing malformed
     * output to crash the application.
     */
    private fun parseResponse(
        rawResponse: String,
        isPython: Boolean
    ): PatchCandidate {

        val jsonText = extractJsonObject(rawResponse)

        if (jsonText == null) {
            return safeFailurePatch(
                isPython = isPython,
                reason = "The on-device model did not return a valid JSON patch."
            )
        }

        val json = runCatching {
            JSONObject(jsonText)
        }.getOrNull()

        if (json == null) {
            return safeFailurePatch(
                isPython = isPython,
                reason = "The on-device model returned malformed patch data."
            )
        }

        val targetLine =
            json.optInt("targetLine", 1).coerceAtLeast(1)

        val oldCode =
            json.optString("oldCode", "").trim()

        val newCode =
            json.optString("newCode", "").trim()

        val description =
            json.optString(
                "description",
                "PatchCam generated a possible correction. Review it before applying."
            ).trim()

        /*
         * An empty correction is not useful and should never be presented
         * as a real patch.
         */
        if (newCode.isBlank()) {
            return safeFailurePatch(
                isPython = isPython,
                reason = "The model identified a problem but did not provide corrected code."
            )
        }

        return PatchCandidate(
            targetLine = targetLine,
            oldCode = oldCode,
            newCode = newCode,
            description = description.ifBlank {
                "PatchCam generated a possible correction."
            },
            /*
             * LLM output itself is NOT verification.
             *
             * Python will only become verified later if the AST validator
             * successfully validates the resulting source.
             */
            verified = false,
            matchMethod = MatchMethod.ON_DEVICE_LLM,
            ruleId = if (isPython) {
                "ON_DEVICE_PYTHON_LLM"
            } else {
                "ON_DEVICE_GENERAL_LLM"
            }
        )
    }

    /**
     * Extracts the first balanced JSON object from the model response.
     */
    private fun extractJsonObject(response: String): String? {

        val start = response.indexOf('{')

        if (start < 0) {
            return null
        }

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until response.length) {

            val character = response[index]

            if (escaped) {
                escaped = false
                continue
            }

            if (character == '\\' && inString) {
                escaped = true
                continue
            }

            if (character == '"') {
                inString = !inString
                continue
            }

            if (inString) {
                continue
            }

            when (character) {
                '{' -> depth++

                '}' -> {
                    depth--

                    if (depth == 0) {
                        return response.substring(
                            start,
                            index + 1
                        )
                    }
                }
            }
        }

        return null
    }

    /**
     * Safe result when the LLM cannot produce a usable patch.
     *
     * This is intentionally NOT presented as a successful automatic fix.
     */
    private fun safeFailurePatch(
        isPython: Boolean,
        reason: String
    ): PatchCandidate {

        return PatchCandidate(
            targetLine = 1,
            oldCode = "",
            newCode = "",
            description = reason,
            verified = false,
            matchMethod = MatchMethod.ON_DEVICE_LLM,
            ruleId = if (isPython) {
                "ON_DEVICE_PYTHON_LLM_FAILED"
            } else {
                "ON_DEVICE_GENERAL_LLM_FAILED"
            }
        )
    }
}