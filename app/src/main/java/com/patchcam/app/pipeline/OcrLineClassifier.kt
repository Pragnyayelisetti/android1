package com.patchcam.app.pipeline

import com.patchcam.app.models.ClassifiedLine
import com.patchcam.app.models.LineClass
import com.patchcam.app.models.OcrLineItem

/**
 * Stage 1.5 — OCR line classification.
 *
 * PROBLEM THIS SOLVES:
 * The previous pipeline treated every OCR'd line as source code UNLESS it
 * happened to match a small "looks like an error" regex. That is backwards:
 * it means any UI text, question, or label that PatchCam's camera happens to
 * pick up (a button, a suggested question, a nav label, chrome from another
 * app on screen, etc.) was silently folded into the "reconstructed source"
 * and handed to the parser and the LLM as if it were real code.
 *
 * This object flips that default. A line is only classified as CODE when it
 * shows positive, structural evidence of being source code (keywords, block
 * syntax, assignment, call syntax, statement terminators, symbol density).
 * Everything else falls back to COMMENT / TRACEBACK / ERROR_MESSAGE / UI_TEXT
 * / UNKNOWN based on its own positive evidence — never on a blacklist of
 * specific app strings. That is deliberate: hard-coding "Ask Gemini" (or any
 * other literal phrase) would only patch today's symptom. The signals below
 * (question-word/aux-verb sentence shape, short imperative phrasing, English
 * stopword density, presence/absence of code punctuation) generalize to any
 * UI text PatchCam might ever see, from any app.
 *
 * The classification is a best-effort heuristic over noisy OCR text, not a
 * guarantee. Callers should still treat anything that isn't clearly CODE or
 * COMMENT as ineligible for code reconstruction, and anything that isn't
 * clearly TRACEBACK or ERROR_MESSAGE as ineligible for the "error text" used
 * in diagnosis and prompts. That hard separation is what actually prevents
 * UI text from reaching the parser, the diagnosis engine, or the LLM.
 */
object OcrLineClassifier {

    // A line needs at least this much positive code evidence to be trusted
    // as CODE. Below this, it is never treated as code, no matter what else
    // it might be.
    private const val CODE_ACCEPT_THRESHOLD = 35

    // A line needs at least this much positive error-message evidence
    // (CamelCase Exception name + colon, a known compiler phrase, or a
    // file:line reference) to be trusted as ERROR_MESSAGE.
    private const val ERROR_ACCEPT_THRESHOLD = 50

    // A line needs at least this much positive natural-language / UI
    // evidence to be labeled UI_TEXT rather than left as UNKNOWN.
    private const val UI_ACCEPT_THRESHOLD = 20

    private val tracebackHeader =
        Regex("""^Traceback \(most recent call last\):\s*$""")

    private val tracebackFileLine =
        Regex("""^\s*File "[^"]+",\s*line\s*\d+""")

    private val pythonCommentStart = Regex("""^#""")
    private val cFamilyLineComment = Regex("""^//""")
    private val cFamilyBlockCommentStart = Regex("""^/\*""")
    private val cFamilyBlockCommentEnd = Regex("""\*/\s*$""")

    private val codeKeywordStart = Regex(
        """^\s*(def|class|if|elif|else|for|while|try|except|finally|with|return|import|from|pass|break|continue|raise|yield|lambda|async\s+def|global|nonlocal|print)\b"""
    )

    private val blockColonEnd = Regex(""":\s*$""")

    private val assignmentPattern = Regex(
        """^\s*[A-Za-z_][A-Za-z0-9_.\[\]]*\s*(==|!=|<=|>=|\+=|-=|\*=|/=|//=|%=|\*\*=|=)\s*\S"""
    )

    private val functionCallPattern = Regex("""[A-Za-z_]\w*\s*\([^)]*\)""")
    private val statementSemicolonEnd = Regex(""";\s*$""")
    private val codeSymbolCharset = "(){}[]=;:_<>+-*/%."
    private val uiExclusionSymbols = Regex("""[{}\[\];=<>]""")

    private val exceptionNameColon = Regex(
        """\b[A-Za-z_][A-Za-z0-9_]*(Error|Exception|Warning)\b\s*:"""
    )
    private val knownCompilerPhrases = Regex(
        """\b(unresolved reference|cannot find symbol|invalid syntax|compilation failed|expecting\s|fatal error)\b""",
        RegexOption.IGNORE_CASE
    )
    private val fileLineReference = Regex(
        """[\w./\\-]+\.(?:kt|kts|java|py|ts|js|tsx|cpp|c|h):\d+"""
    )

    private val trailingQuestionMark = Regex("""\?\s*$""")
    private val whWordOrAuxStart = Regex(
        """^(why|how|what|when|where|who|which|is|are|do|does|did|can|could|should|would|will|has|have)\b""",
        RegexOption.IGNORE_CASE
    )
    private val trailingEllipsis = Regex("""\.\.\.\s*$""")

    // General English function words. This is a linguistic stopword list,
    // not a blacklist of any particular application's vocabulary — it is
    // the same handful of words that make ANY short instruction or question
    // read as natural language rather than code, in any app.
    private val englishStopwords = setOf(
        "the", "is", "are", "this", "that", "and", "or", "but", "with", "for",
        "about", "more", "like", "your", "you", "please", "still", "above",
        "did", "there", "how", "what", "why", "when", "where", "who", "which",
        "can", "could", "should", "would", "will", "another", "down", "to",
        "of", "in", "on", "a", "an", "it", "its", "be", "been", "was", "were",
        "do", "does", "not", "no", "yes"
    )

    fun classify(item: OcrLineItem): ClassifiedLine {
        val text = item.text.trim()

        if (text.isEmpty()) {
            return ClassifiedLine(item, LineClass.UNKNOWN, 0, listOf("empty"))
        }

        if (tracebackHeader.containsMatchIn(text) || tracebackFileLine.containsMatchIn(text)) {
            return ClassifiedLine(item, LineClass.TRACEBACK, 95, listOf("traceback format"))
        }

        if (pythonCommentStart.containsMatchIn(text) ||
            cFamilyLineComment.containsMatchIn(text) ||
            cFamilyBlockCommentStart.containsMatchIn(text) ||
            cFamilyBlockCommentEnd.containsMatchIn(text)
        ) {
            return ClassifiedLine(item, LineClass.COMMENT, 90, listOf("comment marker"))
        }

        val errorScore = scoreErrorMessage(text)
        val codeScore = scoreCode(text)
        val uiScore = scoreUiText(text)

        return when {
            errorScore >= ERROR_ACCEPT_THRESHOLD && errorScore >= codeScore ->
                ClassifiedLine(item, LineClass.ERROR_MESSAGE, errorScore, listOf("compiler/exception format"))

            codeScore >= CODE_ACCEPT_THRESHOLD && codeScore > uiScore ->
                ClassifiedLine(item, LineClass.CODE, codeScore, listOf("code syntax evidence"))

            uiScore >= UI_ACCEPT_THRESHOLD ->
                ClassifiedLine(item, LineClass.UI_TEXT, uiScore, listOf("natural language / label shape"))

            else ->
                ClassifiedLine(
                    item,
                    LineClass.UNKNOWN,
                    maxOf(codeScore, uiScore, errorScore),
                    listOf("insufficient evidence for any category")
                )
        }
    }

    fun classifyAll(items: List<OcrLineItem>): List<ClassifiedLine> {
        return items.map { classify(it) }
    }

    private fun scoreErrorMessage(t: String): Int {
        var score = 0
        if (exceptionNameColon.containsMatchIn(t)) score += 60
        if (knownCompilerPhrases.containsMatchIn(t)) score += 50
        if (fileLineReference.containsMatchIn(t)) score += 40
        return score.coerceIn(0, 100)
    }

    private fun scoreCode(t: String): Int {
        var score = 0

        if (codeKeywordStart.containsMatchIn(t)) score += 35
        if (blockColonEnd.containsMatchIn(t) && !trailingEllipsis.containsMatchIn(t)) score += 25
        if (assignmentPattern.containsMatchIn(t) && !trailingQuestionMark.containsMatchIn(t)) score += 25
        if (functionCallPattern.containsMatchIn(t)) score += 15
        if (statementSemicolonEnd.containsMatchIn(t)) score += 20
        if (t.contains("{") || t.contains("}")) score += 15

        val symbolChars = t.count { it in codeSymbolCharset }
        val density = if (t.isNotEmpty()) symbolChars.toDouble() / t.length else 0.0
        if (density > 0.12) score += 15

        // A trailing '?' or a natural-sentence shape strongly argues AGAINST
        // this being a code statement, even if it happens to contain a
        // stray symbol.
        if (trailingQuestionMark.containsMatchIn(t)) score -= 40
        if (looksLikeNaturalSentence(t)) score -= 25

        return score.coerceIn(0, 100)
    }

    private fun scoreUiText(t: String): Int {
        var score = 0
        val words = t.trim().split(Regex("""\s+"""))

        if (trailingQuestionMark.containsMatchIn(t)) score += 30
        if (whWordOrAuxStart.containsMatchIn(t)) score += 25
        if (words.size in 1..6) score += 10
        if (looksLikeNaturalSentence(t)) score += 25
        if (!uiExclusionSymbols.containsMatchIn(t) && !blockColonEnd.containsMatchIn(t)) score += 10
        if (trailingEllipsis.containsMatchIn(t)) score += 10

        return score.coerceIn(0, 100)
    }

    private fun looksLikeNaturalSentence(t: String): Boolean {
        val words = t.trim().split(Regex("""\s+"""))
        if (words.size < 2 || words.size > 12) return false

        val stopwordCount = words.count { word ->
            word.lowercase().trim(',', '.', '?', '!', ':', ';') in englishStopwords
        }

        val hasCodeSymbols = uiExclusionSymbols.containsMatchIn(t)

        return stopwordCount >= 1 && !hasCodeSymbols
    }

    /**
     * A heuristic OCR text-quality signal for a single line.
     *
     * NOTE: ML Kit's on-device Latin text recognizer does not populate a
     * reliable per-element confidence value (Text.Element#getConfidence()
     * returns 0 for the on-device/unbundled recognizer used by this app in
     * most Play services versions). Rather than surface a confidence number
     * that is usually just zero and would be misleading, this function
     * estimates text quality from the shape of the recognized string itself:
     * stray single-character tokens and unusual/non-ASCII glyphs are the
     * most common signatures of a bad OCR read.
     */
    fun textQualityScore(text: String): Int {
        val t = text.trim()
        if (t.isEmpty()) return 0

        val tokens = t.split(Regex("""\s+"""))
        val noiseTokens = tokens.count { tok ->
            tok.length <= 1 && !tok.matches(Regex("""[A-Za-z0-9]"""))
        }

        val weirdCharCount = t.count { ch -> ch.code > 127 || ch == '\uFFFD' }
        val weirdCharRatio = weirdCharCount.toDouble() / t.length

        var score = 100
        score -= noiseTokens * 15
        score -= (weirdCharRatio * 100).toInt()

        return score.coerceIn(0, 100)
    }

    /**
     * Overall OCR reliability for a merged scan, in the range 0-100.
     *
     * This blends:
     *  - average per-line text quality (garbled/noisy OCR signatures)
     *  - average classifier confidence (how decisively each line could be
     *    categorized at all)
     *  - the fraction of lines the classifier could NOT confidently place
     *    in any category (a high UNKNOWN ratio usually means the OCR read
     *    was too noisy to interpret reliably, independent of what it means)
     *
     * This is a proxy for reliability, not a measured probability. It exists
     * so PatchCam can refuse to confidently diagnose against a scan it can't
     * actually read well, per the "never invent a diagnosis" requirement.
     */
    fun estimateOcrReliability(classified: List<ClassifiedLine>): Int {
        if (classified.isEmpty()) return 0

        val avgQuality = classified.map { textQualityScore(it.item.text) }.average()
        val avgClassConfidence = classified.map { it.confidence }.average()
        val unknownRatio =
            classified.count { it.classification == LineClass.UNKNOWN }.toDouble() / classified.size

        val score = avgQuality * 0.4 + avgClassConfidence * 0.4 + (1.0 - unknownRatio) * 100.0 * 0.2

        return score.toInt().coerceIn(0, 100)
    }
}
