package com.patchcam.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.patchcam.app.models.AstValidationResult
import com.patchcam.app.models.ChatMessage
import com.patchcam.app.models.ChatRole
import com.patchcam.app.models.Diagnosis
import com.patchcam.app.models.ErrorExplanation
import com.patchcam.app.models.ErrorKind
import com.patchcam.app.models.ErrorLocation
import com.patchcam.app.models.FeedbackStats
import com.patchcam.app.models.FeedbackVote
import com.patchcam.app.models.HistoryItem
import com.patchcam.app.models.LanguageTrack
import com.patchcam.app.models.MatchMethod
import com.patchcam.app.models.OcrLineItem
import com.patchcam.app.models.PatchCandidate
import com.patchcam.app.models.ScreenStep
import android.util.Log
import com.patchcam.app.models.ClassifiedLine
import com.patchcam.app.models.LineClass
import com.patchcam.app.models.ModelState
import com.patchcam.app.models.OfficeKitChannel
import com.patchcam.app.models.OfficeKitStats
import com.patchcam.app.network.LaptopBridgeWebSocket
import com.patchcam.app.network.LaptopDiscovery
import com.patchcam.app.network.OfficeKitShare
import com.patchcam.app.network.OfficeKitSessionTracker
import com.patchcam.app.pipeline.ChaquopyAstValidator
import com.patchcam.app.pipeline.ErrorKindDetector
import com.patchcam.app.pipeline.ErrorLocator
import com.patchcam.app.pipeline.FixFeedbackStore
import com.patchcam.app.pipeline.IndentationReconstructor
import com.patchcam.app.pipeline.KnownFixTable
import com.patchcam.app.pipeline.LanguageDetector
import com.patchcam.app.pipeline.LocalDebugAnswerer
import com.patchcam.app.pipeline.LocalRetriever
import com.patchcam.app.pipeline.ModelLocator
import com.patchcam.app.pipeline.MediaPipeLlmInference
import com.patchcam.app.pipeline.OcrLineClassifier
import com.patchcam.app.pipeline.PlainLanguageExplainer
import com.patchcam.app.ui.PatchCamApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PatchCamPipeline"

// See com.patchcam.app.models.PatchCamThresholds for the shared definition
// (used identically by the ResultScreen UI banner).
private val OCR_RELIABILITY_FLOOR = com.patchcam.app.models.PatchCamThresholds.OCR_RELIABILITY_FLOOR

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(ScreenStep.SCAN)

    private var scanFrames by mutableStateOf<List<List<OcrLineItem>>>(
        emptyList()
    )

    private var liveLines by mutableStateOf<List<OcrLineItem>>(
        emptyList()
    )

    private var diagnosis by mutableStateOf<Diagnosis?>(null)

    private var pairingCode by mutableStateOf("")

    /*
     * Blank = find the laptop automatically from the pairing code
     * (see LaptopDiscovery). Only filled in when the network blocks
     * discovery and the user types the address shown on the laptop.
     */
    private var laptopHost by mutableStateOf("")

    /*
     * On-device LLM status. The model file is far too large to ship in
     * the APK, so the user can import it from the phone's storage.
     */
    private var modelReady by mutableStateOf(false)

    private var modelStatus by mutableStateOf<String?>(null)

    private val pickModel =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importModel(uri)
            }
        }

    private var sendMessage by mutableStateOf<String?>(null)

    private var chatLoading by mutableStateOf(false)

    private var history by mutableStateOf<List<HistoryItem>>(
        emptyList()
    )

    /*
     * Feedback loop: the user's verdict on the current fix, what they
     * have said about this kind of fix before, and everything they have
     * ever told PatchCam.
     */
    private val feedbackStore by lazy { FixFeedbackStore(this) }

    private var feedbackVote by mutableStateOf<FeedbackVote?>(null)

    private var ruleStats by mutableStateOf(FeedbackStats())

    private var totalStats by mutableStateOf(FeedbackStats())

    /*
     * Real, on-device counts of the three Office Kit touchpoints PatchCam
     * actually uses (clipboard sync, file EasyShare, screen mirroring) —
     * see OfficeKitSessionTracker for why this exists instead of one
     * unverifiable "sent" toast.
     */
    private val officeKitTracker by lazy { OfficeKitSessionTracker(this) }

    private var officeKitStats by mutableStateOf(OfficeKitStats())

    /*
     * "Ask PatchCam" by voice, via the system speech recognizer (no
     * RECORD_AUDIO permission needed — it's a delegated system activity,
     * same as any app's mic-to-text field).
     */
    private val voiceAsk =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val heard =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()

                if (!heard.isNullOrBlank()) {
                    askPatchCam(heard)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelReady = ModelLocator.isModelAvailable(this)

        officeKitStats = officeKitTracker.stats()

        /*
         * Load the model into memory in the background so the first
         * question or patch does not wait for it.
         */
        if (modelReady) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    MediaPipeLlmInference.getInstance(this@MainActivity)
                }.onFailure { e ->
                    Log.e(TAG, "Model warm-up failed", e)
                }
            }
        }

        setContent {
            PatchCamApp(
                screen = screen,

                frames = scanFrames,

                liveLines = liveLines,

                diagnosis = diagnosis,

                history = history,

                pairingCode = pairingCode,

                onPairingCodeChange = {
                    pairingCode =
                        it.filter(Char::isDigit).take(6)
                },

                onLiveLines = { lines ->
                    liveLines = lines
                },

                onCaptureFrame = { capturedLines ->
                    if (capturedLines.isNotEmpty()) {
                        liveLines = capturedLines

                        scanFrames =
                            scanFrames + listOf(capturedLines)
                    }
                },

                onGalleryFrame = { lines ->
                    if (lines.isNotEmpty()) {
                        liveLines = lines

                        scanFrames =
                            scanFrames + listOf(lines)
                    }
                },

                onAnalyze = {
                    analyzeSession()
                },

                onNewScan = {
                    resetScan()
                },

                onHistory = {
                    screen = ScreenStep.HISTORY
                },

                onKnowledge = {
                    screen = ScreenStep.KNOWLEDGE
                },

                onFlow = {
                    screen = ScreenStep.FLOW
                },

                onScan = {
                    screen = ScreenStep.SCAN
                },

                onSend = {
                    sendToLaptop()
                },

                laptopHost = laptopHost,

                onLaptopHostChange = {
                    laptopHost = it.trim()
                },

                onOfficeKit = {
                    shareViaOfficeKit()
                },

                onOfficeKitClipboard = {
                    copyPatchToClipboard()
                },

                onOfficeKitMirror = {
                    markScreenMirrorUsed()
                },

                officeKitStats = officeKitStats,

                onVoiceAsk = {
                    launchVoiceAsk()
                },

                modelReady = modelReady,

                modelStatus = modelStatus,

                onLoadModel = {
                    pickModel.launch(arrayOf("*/*"))
                },

                onChatSend = { question ->
                    askPatchCam(question)
                },

                chatLoading = chatLoading,

                sendMessage = sendMessage,

                onDismissMessage = {
                    sendMessage = null
                },

                feedbackVote = feedbackVote,

                ruleStats = ruleStats,

                totalStats = totalStats,

                onFeedback = { vote ->
                    recordFeedback(vote)
                },

                onResetFeedback = {
                    resetFeedback()
                },

                onReanalyze = { editedCode ->
                    reanalyzeEdited(editedCode)
                },

                onOpenResult = {
                    if (diagnosis != null) {
                        screen = ScreenStep.RESULT
                    }
                }
            )
        }
    }

    private fun resetScan() {
        screen = ScreenStep.SCAN

        scanFrames = emptyList()

        liveLines = emptyList()

        diagnosis = null

        chatLoading = false

        sendMessage = null

        feedbackVote = null
    }

    private fun analyzeSession() {

        if (scanFrames.isEmpty()) {
            return
        }

        screen = ScreenStep.ANALYZING

        lifecycleScope.launch(Dispatchers.Default) {

            val merged =
                mergeFrames(scanFrames)

            if (merged.isEmpty()) {

                withContext(Dispatchers.Main) {
                    screen = ScreenStep.SCAN
                }

                return@launch
            }

            /*
             * Stage 1.5 — classify every OCR line before it is allowed to
             * become source code, error text, or LLM context.
             *
             * This replaces the old "everything that doesn't look like an
             * error must be code" assumption, which is what let PatchCam's
             * own UI text ("Ask Gemini", "Why did this happen?", suggested
             * questions, nav labels, etc.) leak into the reconstructed
             * source. Only CODE/COMMENT lines are eligible to become
             * source; only TRACEBACK/ERROR_MESSAGE lines are eligible to
             * become error text. UI_TEXT and UNKNOWN lines are dropped
             * from both — never fed to the parser, the diagnosis logic,
             * or the LLM.
             */
            val classified: List<ClassifiedLine> =
                OcrLineClassifier.classifyAll(merged)

            val ocrReliability =
                OcrLineClassifier.estimateOcrReliability(classified)

            Log.d(
                TAG,
                "OCR: ${merged.size} lines, reliability=$ocrReliability, " +
                        classified.groupingBy { it.classification }
                            .eachCount()
                            .toString()
            )

            val codeLines =
                classified
                    .filter {
                        it.classification == LineClass.CODE ||
                                it.classification == LineClass.COMMENT
                    }
                    .map { it.item }

            val errorLines =
                classified
                    .filter {
                        it.classification == LineClass.TRACEBACK ||
                                it.classification == LineClass.ERROR_MESSAGE
                    }
                    .map { it.item }

            /*
             * If classification found no CODE/COMMENT lines at all (e.g. a
             * very short or very noisy scan), fall back to every line the
             * classifier did NOT actively identify as UI_TEXT, rather than
             * blindly trusting the full merged OCR output (which is what
             * let UI chrome back in previously).
             */
            val codeLinesForReconstruction =
                codeLines.ifEmpty {
                    classified
                        .filterNot { it.classification == LineClass.UI_TEXT }
                        .map { it.item }
                }

            val reconstructed =
                IndentationReconstructor.reconstruct(
                    codeLinesForReconstruction
                )

            val ocrErrorText =
                errorLines.joinToString("\n") {
                    it.text
                }

            val result =
                buildDiagnosis(
                    reconstructed = reconstructed,
                    ocrErrorText = ocrErrorText,
                    ocrReliability = ocrReliability,
                    frameCount = scanFrames.size,
                    lineCount = merged.size,
                    userEdited = false
                )

            withContext(Dispatchers.Main) {
                publishDiagnosis(result)
            }
        }
    }

    /*
     * Feedback-loop "Observe" correction: the user fixed a misread line
     * on the Flow screen, so the pipeline runs again from the corrected
     * source. The user has read the code with their own eyes, so the
     * source is no longer treated as an unreliable OCR read.
     */
    private fun reanalyzeEdited(editedCode: String) {

        val current = diagnosis ?: return

        val cleaned =
            editedCode
                .replace("\r", "")
                .trimEnd()

        if (cleaned.isBlank()) {
            return
        }

        screen = ScreenStep.ANALYZING

        lifecycleScope.launch(Dispatchers.Default) {

            val result =
                buildDiagnosis(
                    reconstructed = cleaned,
                    ocrErrorText = current.ocrErrorText,
                    ocrReliability = 100,
                    frameCount = current.frameCount,
                    lineCount = current.lineCount,
                    userEdited = true
                )

            withContext(Dispatchers.Main) {
                publishDiagnosis(result)
            }
        }
    }

    private fun publishDiagnosis(result: Diagnosis) {

        diagnosis = result

        history =
            listOf(
                HistoryItem(
                    id = System.currentTimeMillis(),
                    title = result.category,
                    category =
                        result.sourceFile
                            ?: "visual patch",
                    confidence =
                        result.confidence,
                    timestamp =
                        System.currentTimeMillis()
                )
            ) +
                    history.take(19)

        feedbackVote = null

        chatLoading = false

        refreshFeedbackStats()

        screen = ScreenStep.RESULT
    }

    /*
     * Feedback is kept per fix rule (or per error kind when no patch was
     * produced), so "this kind of fix worked for me" carries over to the
     * next scan.
     */
    private fun feedbackKey(d: Diagnosis): String =
        d.patch?.ruleId ?: "KIND_${d.errorKind.name}"

    private fun refreshFeedbackStats() {
        val current = diagnosis

        ruleStats =
            if (current != null) {
                feedbackStore.stats(feedbackKey(current))
            } else {
                FeedbackStats()
            }

        totalStats = feedbackStore.totals()
    }

    private fun recordFeedback(vote: FeedbackVote) {

        val current = diagnosis ?: return

        val key = feedbackKey(current)

        feedbackVote?.let { previous ->
            feedbackStore.undo(key, previous)
        }

        feedbackStore.record(key, vote)

        feedbackVote = vote

        refreshFeedbackStats()
    }

    private fun resetFeedback() {

        feedbackStore.clear()

        feedbackVote = null

        refreshFeedbackStats()
    }

    /*
     * The diagnosis pipeline from source text onward. Shared by a normal
     * scan and by "re-analyze after the user corrected the code".
     * Runs off the main thread.
     */
    private fun buildDiagnosis(
        reconstructed: String,
        ocrErrorText: String,
        ocrReliability: Int,
        frameCount: Int,
        lineCount: Int,
        userEdited: Boolean
    ): Diagnosis {

        var errorText =
            ocrErrorText
                .ifBlank {
                    "No explicit compiler error text was detected. PatchCam analyzed the visible source for a likely problem."
                }

        val isPython =
            LanguageDetector.isPython(
                reconstructed
            )

        /*
         * Do not trust OCR alone for Python syntax.
         * Validate the ORIGINAL reconstructed source before generating
         * a patch. This gives the explanation engine real parser
         * evidence such as "expected ':'", line and column.
         */
        val sourceValidation =
            if (isPython) {
                runCatching {
                    ChaquopyAstValidator.validate(
                        reconstructed,
                        attemptNumber = 0
                    )
                }.getOrNull()
            } else {
                null
            }

        if (
            isPython &&
            sourceValidation?.valid == false &&
            !sourceValidation.errorMessage.isNullOrBlank()
        ) {
            val parserDetail = buildString {
                append("Python parser: ")
                append(sourceValidation.errorMessage)
                sourceValidation.errorLine?.let {
                    append(" (line ")
                    append(it)
                    append(")")
                }
                sourceValidation.errorOffset?.let {
                    append(", column ")
                    append(it)
                }
            }

            /*
             * Preserve the OCR error, but append authoritative parser
             * evidence. The LLM and root-cause logic can now explain
             * the exact failure instead of seeing only "SyntaxError:
             * expected".
             */
            errorText = if (errorText.isBlank()) {
                parserDetail
            } else {
                "$errorText\n$parserDetail"
            }
        }

        val languageTrack =
            if (isPython) {
                LanguageTrack.PYTHON_VERIFIED
            } else {
                LanguageTrack.OTHER_UNVERIFIED
            }

        /*
         * Fix priority:
         *
         * 1. Known local rule
         * 2. On-device LLM
         * 3. Safe heuristic
         *
         * None of this runs when the OCR read itself is too unreliable
         * to trust — proposing a patch against text we can't confidently
         * read is exactly the "false correction" this app must avoid.
         */
        var patch: PatchCandidate? = null

        if (isPython && ocrReliability >= OCR_RELIABILITY_FLOOR) {

            patch =
                runCatching {
                    KnownFixTable.findMatch(
                        reconstructed,
                        errorText
                    )
                }.getOrNull()
        }

        if (patch == null && ocrReliability >= OCR_RELIABILITY_FLOOR) {

            patch =
                safeLlmFallback(
                    reconstructed,
                    errorText,
                    isPython
                )
        }

        if (patch == null && ocrReliability >= OCR_RELIABILITY_FLOOR) {

            patch =
                heuristicPatch(
                    reconstructed,
                    errorText
                )
        }

        /*
         * Only Python patches that are explicitly verified
         * should go through AST validation.
         */
        var validation: AstValidationResult? =
            null

        if (
            isPython &&
            patch != null &&
            patch.newCode.isNotBlank()
        ) {

            val patchedSource =
                applyPatch(
                    reconstructed,
                    patch
                )

            validation =
                runCatching {
                    ChaquopyAstValidator.validate(
                        patchedSource
                    )
                }.getOrNull()

            /*
             * Verification is based on actual validation,
             * not simply on the fact that the LLM generated it.
             */
            if (validation != null) {

                patch =
                    patch.copy(
                        verified = validation.valid
                    )
            }
        }

        val category =
            classify(
                errorText,
                reconstructed
            )

        val source =
            Regex(
                """([A-Za-z0-9_./\\-]+\.(?:kt|kts|java|py|js|ts|tsx|cpp|c|h))(?::(\d+))?"""
            ).find(errorText)

        /*
         * Exactly where the problem is: the kind of error, and the line
         * (and column) as the USER sees it. The line number printed in
         * the on-screen traceback wins over the reconstructed source's
         * own numbering, which can miss blank lines.
         */
        val errorKind: ErrorKind =
            ErrorKindDetector.detect(
                errorText,
                patch?.ruleId
            )

        val location: ErrorLocation? =
            ErrorLocator.locate(
                ocrErrorText,
                reconstructed,
                sourceValidation,
                patch,
                errorKind
            )

        /*
         * Keep the patch's line number in the user's numbering too, so
         * what is shown on screen and what is sent to the laptop agree.
         */
        val locatedPatch = patch

        if (
            locatedPatch != null &&
            location != null &&
            locatedPatch.oldCode.isNotBlank() &&
            locatedPatch.oldCode.trim() == location.lineText
        ) {
            patch =
                locatedPatch.copy(
                    targetLine = location.line
                )
        }

        /*
         * What the user has told PatchCam about this kind of fix before
         * (Flow tab) nudges the confidence.
         */
        val feedbackAdjustment =
            FixFeedbackStore.confidenceAdjustment(
                feedbackStore.stats(
                    patch?.ruleId ?: "KIND_${errorKind.name}"
                )
            )

        val confidence =
            confidenceFor(
                errorText,
                patch,
                lineCount,
                validation,
                ocrReliability,
                feedbackAdjustment
            )

        val lowReliability =
            ocrReliability < OCR_RELIABILITY_FLOOR

        /*
         * Specific, plain-language wording for the recognised error
         * (uses the real line, column and code). Errors PatchCam has no
         * specific wording for keep the existing explanation.
         */
        val plain =
            if (lowReliability) {
                null
            } else {
                PlainLanguageExplainer.explain(
                    errorKind,
                    errorText,
                    location,
                    patch
                )
            }

        /*
         * Never invent a diagnosis: if the scan itself was too
         * unreliable to trust, say so instead of confidently
         * explaining a root cause built on a bad read.
         */
        val rootCause =
            if (lowReliability) {
                "I detected something that looks like a programming error, but part of the scanned text could not be read reliably " +
                        "(OCR reliability: $ocrReliability%). I don't want to give you a false explanation from an unreliable read. " +
                        "Try scanning this section again with better lighting or a steadier frame."
            } else {
                plain?.why
                    ?: rootCauseFor(
                        errorText,
                        reconstructed,
                        patch
                    )
            }

        val explanation =
            if (lowReliability) {
                ErrorExplanation(
                    whatWentWrong = "PatchCam could not confidently read this scan.",
                    whyItHappened = rootCause,
                    howToFix = "Scan this section again — try holding the camera steadier, filling the frame with the error text, and making sure the screen isn't glared or blurry.",
                    correctedExample = "No correction is offered until the source can be read reliably.",
                    technicalDetails = errorText
                )
            } else if (plain != null) {
                ErrorExplanation(
                    whatWentWrong = plain.headline,
                    whyItHappened = plain.why,
                    howToFix = plain.howToFix,
                    correctedExample = plain.example,
                    technicalDetails = errorText,
                    beginnerExplanation = plain.beginner,
                    preventionTip = plain.tip
                )
            } else {
                buildExplanation(
                    category = category,
                    errorText = errorText,
                    rootCause = rootCause,
                    code = reconstructed,
                    patch = patch,
                    validation = validation,
                    isPython = isPython
                )
            }

        val initialChat =
            ChatMessage(
                id = System.currentTimeMillis(),
                role = ChatRole.PATCHCAM,
                text = buildInitialAssistantMessage(
                    explanation,
                    lowReliability
                )
            )

        return Diagnosis(
            category = category,

            sourceFile =
                source
                    ?.groupValues
                    ?.getOrNull(1),

            lineNumber =
                location?.line
                    ?: source
                        ?.groupValues
                        ?.getOrNull(2)
                        ?.toIntOrNull(),

            errorText = errorText,

            rootCause = rootCause,

            confidence = confidence,

            code = reconstructed,

            errorBlock = errorText,

            patch = patch,

            validation = validation,

            frameCount = frameCount,

            lineCount = lineCount,

            ocrReliability = ocrReliability,

            explanation = explanation,

            chat = listOf(initialChat),

            errorKind = errorKind,

            location = location,

            ocrErrorText = ocrErrorText,

            sourceValidation = sourceValidation,

            userEdited = userEdited
        )
    }

    private fun safeLlmFallback(
        code: String,
        error: String,
        isPython: Boolean
    ): PatchCandidate? {

        if (!ModelLocator.isModelAvailable(this@MainActivity)) {
            return null
        }

        return runCatching {

            val llm = MediaPipeLlmInference.getInstance(this@MainActivity)

            // Local RAG: similar solved cases from PatchCam's own knowledge.
            val retrieved = LocalRetriever.contextFor(error, code)

            var candidate =
                llm.generatePatch(
                    codeText = code,
                    errorText = error,
                    isPython = isPython,
                    retrievedContext = retrieved
                )

            /*
             * Self-repair loop — the model proposes, Python's parser
             * disposes. If the parser rejects the proposal, its exact
             * complaint goes back to the model for one more try, so a
             * small on-device model is corrected by evidence rather than
             * trusted blindly.
             */
            if (isPython && candidate.newCode.isNotBlank()) {
                var attempt = 0

                while (attempt < 2) {
                    val check =
                        runCatching {
                            ChaquopyAstValidator.validate(
                                applyPatch(code, candidate),
                                attemptNumber = attempt + 1
                            )
                        }.getOrNull()

                    if (check == null || check.valid) {
                        break
                    }

                    attempt++

                    candidate =
                        llm.generatePatch(
                            codeText = code,
                            errorText = error,
                            isPython = true,
                            retrievedContext = retrieved,
                            parserFeedback =
                                "${check.errorMessage.orEmpty()} (line ${check.errorLine ?: "?"})"
                        )
                }
            }

            candidate

        }.getOrElse { e ->
            Log.e(TAG, "safeLlmFallback: on-device patch generation failed", e)
            null
        }
    }

    private fun heuristicPatch(
        code: String,
        error: String
    ): PatchCandidate {

        val lower =
            error.lowercase()

        if (
            lower.contains("unresolved reference") &&
            code.contains("TextButton")
        ) {

            return PatchCandidate(
                targetLine = 1,
                oldCode = "",
                newCode =
                    "import androidx.compose.material3.TextButton",
                description =
                    "TextButton is being used, but its Material 3 import is missing.",
                verified = false,
                matchMethod = MatchMethod.HEURISTIC,
                ruleId =
                    "ANDROIDX_MISSING_TEXTBUTTON_IMPORT"
            )
        }

        if (
            lower.contains("unresolved reference") &&
            code.contains("SmallTopAppBar")
        ) {

            return PatchCandidate(
                targetLine = 1,
                oldCode = "",
                newCode =
                    "Replace SmallTopAppBar with the TopAppBar API supported by the project's Material 3 version.",
                description =
                    "The project is using a Material 3 API that is not available in the current dependency version.",
                verified = false,
                matchMethod = MatchMethod.HEURISTIC,
                ruleId =
                    "MATERIAL3_TOP_APP_BAR"
            )
        }

        if (
            lower.contains("indentationerror") ||
            lower.contains("taberror")
        ) {

            return PatchCandidate(
                targetLine = 1,
                oldCode = "",
                newCode =
                    "Replace tabs with consistent 4-space indentation.",
                description =
                    "Python requires consistent indentation. Use four spaces for each indentation level.",
                verified = false,
                matchMethod = MatchMethod.HEURISTIC,
                ruleId =
                    "PYTHON_INDENTATION"
            )
        }

        val first =
            code
                .lineSequence()
                .firstOrNull {
                    it.isNotBlank()
                }
                ?: ""

        return PatchCandidate(
            targetLine = 1,
            oldCode = first,
            newCode = first,
            description =
                "PatchCam detected a problem but could not safely determine an automatic correction. Review the explanation before changing the code.",
            verified = false,
            matchMethod = MatchMethod.HEURISTIC,
            ruleId = "SAFE_REVIEW_REQUIRED"
        )
    }

    private fun buildExplanation(
        category: String,
        errorText: String,
        rootCause: String,
        code: String,
        patch: PatchCandidate?,
        validation: AstValidationResult?,
        isPython: Boolean
    ): ErrorExplanation {

        val lower =
            errorText.lowercase()

        return when {

            lower.contains("indentationerror") ||
                    lower.contains("taberror") -> {

                ErrorExplanation(
                    whatWentWrong =
                        "PatchCam found an indentation problem in your Python code.",

                    whyItHappened =
                        "Python uses indentation to understand which statements belong to the same block. Your code appears to use inconsistent indentation or tabs and spaces together.",

                    howToFix =
                        "Use four spaces for every indentation level and keep the indentation consistent throughout the affected block.",

                    correctedExample =
                        patch?.newCode?.takeIf {
                            it.isNotBlank()
                        }
                            ?: "Use 4 spaces instead of tabs.",

                    technicalDetails =
                        errorText
                )
            }

            lower.contains("syntaxerror") ||
                    lower.contains("expected ':'") -> {

                ErrorExplanation(
                    whatWentWrong =
                        "PatchCam found a Python syntax error.",

                    whyItHappened =
                        "Python could not understand the structure of the statement. This is commonly caused by a missing symbol such as ':', ')', ']', ',' or an incorrectly written statement.",

                    howToFix =
                        patch?.description
                            ?: "Check the reported line and the line immediately before it for a missing or incorrectly placed symbol.",

                    correctedExample =
                        patch?.newCode?.takeIf {
                            it.isNotBlank()
                        }
                            ?: "Review the statement around the reported line.",

                    technicalDetails =
                        errorText
                )
            }

            lower.contains("unresolved reference") -> {

                ErrorExplanation(
                    whatWentWrong =
                        "PatchCam found a name that the project does not currently recognize.",

                    whyItHappened =
                        rootCause,

                    howToFix =
                        patch?.description
                            ?: "Check whether the name is misspelled, imported correctly, or available in the project's current dependency version.",

                    correctedExample =
                        patch?.newCode?.takeIf {
                            it.isNotBlank()
                        }
                            ?: "Add the required import or replace the unavailable API.",

                    technicalDetails =
                        errorText
                )
            }

            lower.contains("type mismatch") -> {

                ErrorExplanation(
                    whatWentWrong =
                        "PatchCam found two values whose types do not match.",

                    whyItHappened =
                        rootCause,

                    howToFix =
                        patch?.description
                            ?: "Make the value and the function parameter use compatible types.",

                    correctedExample =
                        patch?.newCode?.takeIf {
                            it.isNotBlank()
                        }
                            ?: "Convert the value to the expected type or change the receiving type.",

                    technicalDetails =
                        errorText
                )
            }

            else -> {

                ErrorExplanation(
                    whatWentWrong =
                        "PatchCam detected a $category.",

                    whyItHappened =
                        rootCause,

                    howToFix =
                        patch?.description
                            ?: "Review the highlighted error and the surrounding source code.",

                    correctedExample =
                        patch?.newCode?.takeIf {
                            it.isNotBlank()
                        }
                            ?: "No safe automatic correction was generated.",

                    technicalDetails =
                        errorText
                )
            }
        }
    }

    private fun buildInitialAssistantMessage(
        explanation: ErrorExplanation,
        lowReliability: Boolean
    ): String {

        /*
         * Deliberately short: the full explanation is already on the
         * cards above, so the chat only needs to say what it found and
         * what it can be asked.
         */
        if (lowReliability) {
            return "I couldn't read this scan clearly enough to be sure. " +
                    "Try scanning it again — or ask me what to do."
        }

        return "Found it: ${explanation.whatWentWrong.trimEnd('.')}.\n\n" +
                "Ask me why it happened, exactly where to fix it, or whether anything else is wrong."
    }

    private fun rootCauseFor(
        error: String,
        code: String,
        patch: PatchCandidate?
    ): String {

        val lower =
            error.lowercase()

        return when {

            lower.contains("unresolved reference") &&
                    code.contains("TextButton") ->

                "TextButton is referenced without the matching Material 3 import."

            lower.contains("smalltopappbar") ->

                "The code uses a Material 3 API that is not available in the project's current dependency/API version."

            lower.contains("expected ':'") ->

                "Python reached the end of the statement but expected ':' to mark the start of the indented block."

            lower.contains("expected '") && lower.contains("syntaxerror") ->

                "Python's parser reached the reported location while expecting a required syntax token. The parser evidence above identifies the missing token when available."

            lower.contains("syntaxerror") ->

                "Python could not parse the statement structure at the reported location. The parser evidence should be used to identify the exact missing or misplaced token."

            lower.contains("indentationerror") ->

                "Python indentation is inconsistent or a block body is missing."

            lower.contains("taberror") ->

                "The Python file mixes tabs and spaces for indentation."

            lower.contains("type mismatch") ->

                "A value of one type is being passed where another type is expected."

            lower.contains("cannot find symbol") ->

                "The compiler cannot resolve the referenced class, method, variable, or symbol."

            patch?.description?.isNotBlank() == true ->

                patch.description

            else ->

                "PatchCam detected an error pattern but needs more source context for a completely reliable root-cause decision."
        }
    }

    private fun classify(
        error: String,
        code: String
    ): String {

        val e =
            error.lowercase()

        return when {

            e.contains("unresolved reference") ||
                    e.contains("cannot find symbol") ->

                "Compilation error"

            e.contains("syntaxerror") ||
                    (
                            e.contains("expected") &&
                                    code.contains("def ")
                            ) ->

                "Syntax error"

            e.contains("indentationerror") ||
                    e.contains("taberror") ->

                "Indentation error"

            e.contains("type mismatch") ->

                "Type mismatch"

            e.contains("nullpointer") ||
                    e.contains("null pointer") ->

                "Runtime null error"

            e.contains("exception") ->

                "Runtime exception"

            else ->

                "Code problem"
        }
    }

    private fun confidenceFor(
        error: String,
        patch: PatchCandidate?,
        lines: Int,
        validation: AstValidationResult?,
        ocrReliability: Int,
        feedbackAdjustment: Int = 0
    ): Int {

        /*
         * OCR reliability is a hard cap, not just one more additive
         * signal: no amount of parser/patch evidence should push the
         * reported confidence above what we could actually read.
         */
        if (ocrReliability < OCR_RELIABILITY_FLOOR) {
            return ocrReliability.coerceIn(5, OCR_RELIABILITY_FLOOR - 1)
        }

        var score = 50

        if (error.contains(":")) {
            score += 8
        }

        if (
            Regex(
                """\b(?:error|exception|unresolved|syntax|mismatch)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(error)
        ) {
            score += 12
        }

        if (
            patch?.matchMethod ==
            MatchMethod.LOCAL_TABLE
        ) {
            score += 15
        }

        if (
            patch?.matchMethod ==
            MatchMethod.ON_DEVICE_LLM
        ) {
            score += 8
        }

        if (lines >= 3) {
            score += 5
        }

        if (validation?.valid == true) {
            score += 15
        }

        if (validation?.valid == false) {
            score -= 10
        }

        /*
         * What the user has confirmed (or rejected) for this kind of fix
         * on the Flow screen.
         */
        score += feedbackAdjustment

        return score.coerceIn(40, 98)
    }

    private fun applyPatch(
        code: String,
        patch: PatchCandidate
    ): String {

        if (patch.newCode.isBlank()) {
            return code
        }

        if (patch.oldCode.isBlank()) {
            return patch.newCode +
                    "\n" +
                    code
        }

        return code.replaceFirst(
            patch.oldCode,
            patch.newCode
        )
    }

    private fun mergeFrames(
        frames: List<List<OcrLineItem>>
    ): List<OcrLineItem> {

        if (frames.isEmpty()) {
            return emptyList()
        }

        val result =
            frames.first().toMutableList()

        for (frame in frames.drop(1)) {

            val a =
                result.map {
                    normalize(it.text)
                }

            val b =
                frame.map {
                    normalize(it.text)
                }

            var overlap = 0

            val maxOverlap =
                minOf(
                    a.size,
                    b.size,
                    12
                )

            for (n in maxOverlap downTo 1) {

                if (
                    a.takeLast(n) ==
                    b.take(n)
                ) {
                    overlap = n
                    break
                }
            }

            result +=
                frame.drop(overlap)
        }

        return result
    }

    private fun normalize(
        text: String
    ): String {

        return text
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
            .lowercase()
    }


    private fun askPatchCam(question: String) {
        val trimmed = question.trim()

        if (trimmed.isBlank() || diagnosis == null || chatLoading) {
            return
        }

        val current = diagnosis ?: return

        val userMessage = ChatMessage(
            id = System.currentTimeMillis(),
            role = ChatRole.USER,
            text = trimmed
        )

        diagnosis = current.copy(
            chat = current.chat + userMessage
        )

        chatLoading = true

        lifecycleScope.launch(Dispatchers.Default) {

            val snapshot = current.copy(
                chat = current.chat + userMessage
            )

            /*
             * 1. Answer from the facts PatchCam already established
             *    (line, parser evidence, patch, validation). This works
             *    offline and does not need the language model installed.
             *    "Are there more errors?" really re-runs Python's parser.
             */
            val local =
                runCatching {
                    LocalDebugAnswerer.answer(
                        diagnosis = snapshot,
                        question = trimmed,
                        validate = { code ->
                            runCatching {
                                ChaquopyAstValidator.validate(code)
                            }.getOrNull()
                        }
                    )
                }.onFailure { e ->
                    Log.e(TAG, "askPatchCam: offline answer failed", e)
                }.getOrNull()

            /*
             * 2. Only for open-ended questions the offline engine does
             *    not cover: use the on-device model when it is installed.
             *    A missing or failing model is never shown to the user as
             *    the answer — the offline answer is used instead.
             */
            val answer =
                when {
                    local != null && local.handled ->
                        local.text

                    else ->
                        askLlmOrNull(snapshot, trimmed)
                            ?: local?.text
                            ?: "Something went wrong while I was working that out. " +
                            "Try asking again, for example: \"Which line is wrong?\""
                }

            withContext(Dispatchers.Main) {
                val latest = diagnosis

                if (latest != null) {
                    diagnosis = latest.copy(
                        chat = latest.chat + ChatMessage(
                            id = System.currentTimeMillis() + 1,
                            role = ChatRole.PATCHCAM,
                            text = answer
                        )
                    )
                }

                chatLoading = false
            }
        }
    }

    private fun askLlmOrNull(
        current: Diagnosis,
        question: String
    ): String? {

        if (!ModelLocator.isModelAvailable(this@MainActivity)) {
            return null
        }

        val answer =
            runCatching {
                MediaPipeLlmInference
                    .getInstance(this@MainActivity)
                    .chatOrNull(
                        diagnosis = current,
                        question = question
                    )
            }.onFailure { e ->
                // Real cause stays in Logcat; the user gets the offline answer.
                Log.e(TAG, "askPatchCam: on-device model unavailable", e)
            }.getOrNull()
                ?: return null

        /*
         * Show which knowledge the answer was grounded on (local RAG).
         */
        val sources =
            LocalRetriever
                .retrieve(question + " " + current.errorText, 2)
                .joinToString("; ") { it.citation }

        return if (sources.isBlank()) {
            answer
        } else {
            "$answer\n\nSources: $sources"
        }
    }

    private fun sendToLaptop() {

        val current =
            diagnosis
                ?: return

        val patch =
            current.patch
                ?: return

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            val result =
                runCatching {

                    /*
                     * No address typed: find the laptop from the pairing
                     * code alone (the bridge answers only to its own code).
                     */
                    val host =
                        laptopHost.ifBlank {
                            LaptopDiscovery.find(pairingCode)
                                ?: throw IllegalStateException(
                                    "Couldn't find the laptop on this Wi-Fi. " +
                                            "Check that the bridge is running and both devices " +
                                            "are on the same network — or type the laptop " +
                                            "address shown on its screen."
                                )
                        }

                    LaptopBridgeWebSocket(
                        host,
                        pairingCode
                    )
                        .sendPatch(patch, current.sourceFile)
                        .getOrThrow()
                }

            withContext(
                Dispatchers.Main
            ) {

                sendMessage =
                    result.fold(
                        onSuccess = {
                            it
                        },
                        onFailure = {
                            "Laptop bridge failed: ${
                                it.message
                                    ?: "connection failed"
                            }"
                        }
                    )
            }
        }
    }

    /*
     * Office Kit, path 1 of 3: file EasyShare. Hands the patch to Office
     * Kit's file transfer through the share sheet (opening Office Kit
     * directly when it's installed — see OfficeKitShare.shareIntent). The
     * laptop bridge watches the folder Office Kit saves into and applies
     * the patch from there.
     */
    private fun shareViaOfficeKit() {

        val current =
            diagnosis
                ?: return

        val patch =
            current.patch
                ?: return

        runCatching {
            val file =
                OfficeKitShare.buildPatchFile(
                    this,
                    patch,
                    current.sourceFile
                )

            startActivity(
                OfficeKitShare.shareIntent(this, file)
            )

            officeKitTracker.record(OfficeKitChannel.FILE_SHARE)
            officeKitStats = officeKitTracker.stats()
        }.onFailure { e ->
            Log.e(TAG, "Office Kit share failed", e)
            sendMessage = "Couldn't prepare the patch file: ${e.message ?: "unknown error"}"
        }
    }

    /*
     * Office Kit, path 2 of 3: clipboard sync. Copies the patch as a plain
     * diff onto the system clipboard, which Office Kit's clipboard sync
     * mirrors to the laptop — the fastest path when only one hunk needs to
     * land in the editor, no file transfer round trip needed.
     */
    private fun copyPatchToClipboard() {

        val current =
            diagnosis
                ?: return

        val patch =
            current.patch
                ?: return

        runCatching {
            OfficeKitShare.copyToClipboard(this, patch, current.sourceFile)

            officeKitTracker.record(OfficeKitChannel.CLIPBOARD_SYNC)
            officeKitStats = officeKitTracker.stats()
        }.onSuccess {
            sendMessage = "Patch copied — paste it on the laptop once Office Kit's clipboard sync catches up."
        }.onFailure { e ->
            Log.e(TAG, "Clipboard copy failed", e)
            sendMessage = "Couldn't copy the patch: ${e.message ?: "unknown error"}"
        }
    }

    /*
     * Office Kit, path 3 of 3: screen mirroring, used live during the
     * demo/pitch. PatchCam can't detect mirroring from inside the app
     * (that's OriginOS's own system UI, outside any app's reach), so this
     * simply records that the presenter confirmed it's on, for the same
     * honest usage log as the other two paths.
     */
    private fun markScreenMirrorUsed() {
        officeKitTracker.record(OfficeKitChannel.SCREEN_MIRROR)
        officeKitStats = officeKitTracker.stats()
        sendMessage = "Noted — mirroring via Office Kit for this session."
    }

    /*
     * "Ask PatchCam" by voice: the system speech recognizer's own UI does
     * the listening, PatchCam just reads back the top transcription and
     * feeds it into the same askPatchCam(question) path as typed text.
     */
    private fun launchVoiceAsk() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask PatchCam...")
        }

        runCatching {
            voiceAsk.launch(intent)
        }.onFailure {
            Toast.makeText(
                this,
                "No speech recognizer available on this device.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*
     * Copies the model the user picked into PatchCam's own folder and
     * loads it. Multi-GB copy, so it runs on the IO dispatcher and
     * reports progress.
     */
    private fun importModel(uri: Uri) {

        lifecycleScope.launch(Dispatchers.IO) {

            try {
                val target = ModelLocator.preferredModelFile(this@MainActivity)
                val partial = File(target.parentFile, target.name + ".part")

                var copied = 0L
                var lastReported = 0L

                val input =
                    contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Couldn't open the selected file.")

                input.use { source ->
                    partial.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 20)

                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break

                            out.write(buffer, 0, n)
                            copied += n

                            if (copied - lastReported >= (64L shl 20)) {
                                lastReported = copied
                                withContext(Dispatchers.Main) {
                                    modelStatus = "Copying model… ${copied shr 20} MB"
                                }
                            }
                        }
                    }
                }

                if (copied < (50L shl 20)) {
                    partial.delete()
                    throw IllegalStateException(
                        "That file is too small to be a model (${copied shr 20} MB)."
                    )
                }

                if (target.exists()) {
                    target.delete()
                }
                partial.renameTo(target)

                withContext(Dispatchers.Main) {
                    modelStatus = "Loading the model into memory…"
                }

                MediaPipeLlmInference.getInstance(this@MainActivity)

                withContext(Dispatchers.Main) {
                    modelReady = true
                    modelStatus = null
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Model import failed", e)

                withContext(Dispatchers.Main) {
                    modelReady = ModelLocator.isModelAvailable(this@MainActivity)
                    modelStatus = "Model import failed: ${e.message ?: "unknown error"}"
                }
            }
        }
    }
}
