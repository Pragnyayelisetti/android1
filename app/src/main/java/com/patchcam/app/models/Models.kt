package com.patchcam.app.models

import android.graphics.Rect

/**
 * Single source of truth for thresholds shared between the pipeline
 * (MainActivity) and the UI (PatchCamScreens), so they can't drift apart.
 */
object PatchCamThresholds {
    /**
     * Below this OCR reliability score (0-100, see
     * OcrLineClassifier.estimateOcrReliability), PatchCam will not
     * confidently state a diagnosis or offer a patch built from the scan.
     */
    const val OCR_RELIABILITY_FLOOR = 45
}

enum class ScreenStep {
    SCAN,
    ANALYZING,
    RESULT,
    HISTORY,
    KNOWLEDGE,
    FLOW
}

enum class LanguageTrack {
    PYTHON_VERIFIED,
    OTHER_UNVERIFIED
}

enum class MatchMethod {
    LOCAL_TABLE,
    ON_DEVICE_LLM,
    HEURISTIC
}

data class CropBoxes(
    val codeRect: Rect,
    val errorRect: Rect
)

data class OcrLineItem(
    val text: String,
    val boundingBox: Rect,
    val indentLevel: Int = 0
)

/*
 * What kind of text an OCR'd line actually is, per OcrLineClassifier.
 *
 * Only CODE and COMMENT are eligible to enter the reconstructed source.
 * Only TRACEBACK and ERROR_MESSAGE are eligible to enter the error text
 * used for diagnosis and the LLM prompt. UI_TEXT and UNKNOWN are excluded
 * from both — this is what stops the app's own buttons/labels/questions
 * from being treated as source code.
 */
enum class LineClass {
    CODE,
    COMMENT,
    TRACEBACK,
    ERROR_MESSAGE,
    UI_TEXT,
    UNKNOWN
}

/*
 * A single OCR line plus enough metadata to decide whether it can be
 * trusted, and for what purpose.
 */
data class ClassifiedLine(
    val item: OcrLineItem,
    val classification: LineClass,
    val confidence: Int,
    val reasons: List<String> = emptyList()
)

/*
 * Lifecycle/failure state of the on-device LLM, so a chat or patch
 * failure can be explained honestly instead of collapsing into one
 * generic "couldn't answer" string.
 */
sealed class ModelState {
    object NotLoaded : ModelState()
    object Loading : ModelState()
    object Ready : ModelState()
    object Busy : ModelState()
    data class OutOfMemory(val detail: String? = null) : ModelState()
    data class GenerationFailed(val detail: String? = null) : ModelState()
    object EmptyResponse : ModelState()
    data class ContextTooLarge(val approxTokens: Int? = null) : ModelState()

    fun userMessage(): String = when (this) {
        is NotLoaded ->
            "PatchCam's offline language model is not ready yet. It hasn't been installed on this device."
        is Loading ->
            "PatchCam's offline language model is still starting up. Please try again in a moment."
        is Ready ->
            "PatchCam's offline language model is ready."
        is Busy ->
            "PatchCam is still finishing the previous request. Please wait a moment and try again."
        is OutOfMemory ->
            "The local model could not run because this device does not have enough available memory right now."
        is GenerationFailed ->
            "PatchCam couldn't generate an explanation this time. Your diagnosis above is still available."
        is EmptyResponse ->
            "PatchCam's model didn't return an answer for that question. Please try rephrasing it."
        is ContextTooLarge ->
            "This debugging session has too much context for PatchCam's on-device model to process at once. Try starting a new scan."
    }
}

data class PatchCandidate(
    val targetLine: Int,
    val oldCode: String,
    val newCode: String,
    val description: String,
    val verified: Boolean,
    val matchMethod: MatchMethod,
    val ruleId: String? = null
)

data class AstValidationResult(
    val valid: Boolean,
    val errorMessage: String? = null,
    val errorLine: Int? = null,
    val errorOffset: Int? = null,
    val sourceLine: String? = null,
    val attemptNumber: Int = 1
)

data class StageTimings(
    val captureOcrMs: Long = 0,
    val indentationReconstructionMs: Long = 0,
    val languageDetectionMs: Long = 0,
    val fixGenerationMs: Long = 0,
    val astValidationMs: Long = 0,
    val totalMs: Long = 0
)

/*
 * Human-readable explanation shown to the user.
 *
 * This is deliberately separate from the raw compiler/parser message.
 * The raw message is technical evidence; these fields are what the
 * normal user should read first.
 */
data class ErrorExplanation(
    val whatWentWrong: String,
    val whyItHappened: String,
    val howToFix: String,
    val correctedExample: String,
    val technicalDetails: String,

    /*
     * Extra plain-language material used by the offline chat.
     * Empty when PatchCam has no specific wording for this error kind.
     */
    val beginnerExplanation: String = "",
    val preventionTip: String = ""
)

/*
 * The kind of error PatchCam recognised. Drives the specific
 * "why it happened" wording, the offline chat answers and the
 * Knowledge topic that is linked to the scan.
 */
enum class ErrorKind {
    MISSING_COLON,
    UNCLOSED_BRACKET,
    UNTERMINATED_STRING,
    PRINT_PARENS,
    SINGLE_EQUALS,
    INVALID_SYNTAX,
    EXPECTED_INDENT,
    UNEXPECTED_INDENT,
    UNINDENT_MISMATCH,
    TAB_ERROR,
    NAME_ERROR,
    MODULE_NOT_FOUND,
    ATTRIBUTE_ERROR,
    INDEX_ERROR,
    KEY_ERROR,
    ZERO_DIVISION,
    TYPE_ERROR,
    VALUE_ERROR,
    UNRESOLVED_REFERENCE,
    OTHER
}

/*
 * Exactly where the problem is.
 *
 * `line` is the line number the USER sees in their own file. When the
 * scanned screen contained a traceback it is taken from there, because
 * the reconstructed source can be missing blank lines and so its own
 * line numbers can be off. `codeIndex` is the position inside the
 * reconstructed source and is only used internally.
 */
data class ErrorLocation(
    val line: Int,
    val column: Int?,
    val lineText: String,
    val codeIndex: Int?,
    val lineFromTraceback: Boolean
) {
    fun label(): String =
        if (column != null) "Line $line, column $column" else "Line $line"
}

/*
 * The user's verdict on a proposed fix. This is what closes the
 * Observe -> Diagnose -> Update feedback loop.
 */
enum class FeedbackVote {
    WORKED,
    PARTLY,
    DIDNT_WORK
}

data class FeedbackStats(
    val worked: Int = 0,
    val partly: Int = 0,
    val failed: Int = 0
) {
    val total: Int get() = worked + partly + failed
}

/*
 * Conversation item used by the PatchCam assistant.
 *
 * The conversation can remain attached to the current diagnosis,
 * so the user can ask follow-up questions without scanning again.
 */
data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String
)

enum class ChatRole {
    USER,
    PATCHCAM
}

/*
 * Complete diagnosis produced after a scan.
 */
data class Diagnosis(
    val category: String,
    val sourceFile: String?,
    val lineNumber: Int?,
    val errorText: String,
    val rootCause: String,

    /*
     * This confidence is an evidence score, not a claim that the
     * AI is mathematically certain.
     */
    val confidence: Int,

    val code: String,
    val errorBlock: String,

    val patch: PatchCandidate?,
    val validation: AstValidationResult?,

    val frameCount: Int,
    val lineCount: Int,

    /*
     * OCR reliability for the lines that were actually used to build
     * `code`/`errorBlock` (0-100). Separate from `confidence`, which also
     * folds in parser/patch evidence — this is specifically "could we read
     * the screen well enough to trust what follows".
     */
    val ocrReliability: Int = 100,

    /*
     * User-facing explanation.
     */
    val explanation: ErrorExplanation,

    /*
     * Conversation for this particular diagnosis.
     */
    val chat: List<ChatMessage> = emptyList(),

    /*
     * What kind of error this is, and exactly where it is.
     */
    val errorKind: ErrorKind = ErrorKind.OTHER,
    val location: ErrorLocation? = null,

    /*
     * The error text exactly as OCR read it (without the appended parser
     * evidence), and the parser result for the ORIGINAL source. Kept so
     * the Flow screen can show before/after and so the user can correct
     * the code and re-analyze.
     */
    val ocrErrorText: String = "",
    val sourceValidation: AstValidationResult? = null,

    /*
     * True when the user corrected the reconstructed code by hand.
     */
    val userEdited: Boolean = false
)

data class HistoryItem(
    val id: Long,
    val title: String,
    val category: String,
    val confidence: Int,
    val timestamp: Long
)

/*
 * The three real, distinct ways PatchCam hands work to the laptop through
 * iQOO Office Kit (there is no public Office Kit SDK to call into — these
 * are the same three surfaces a person would use by hand: Office Kit's
 * clipboard sync, its file EasyShare, and its screen mirroring).
 * See OfficeKitSessionTracker, which counts real taps on each of these.
 */
enum class OfficeKitChannel {
    CLIPBOARD_SYNC,
    FILE_SHARE,
    SCREEN_MIRROR
}

data class OfficeKitStats(
    val clipboardSyncs: Int = 0,
    val fileShares: Int = 0,
    val mirrorSessions: Int = 0,
    val firstEventAt: Long = 0L,
    val lastEventAt: Long = 0L
) {
    val total: Int get() = clipboardSyncs + fileShares + mirrorSessions
}