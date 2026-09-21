package com.patchcam.app.pipeline

import com.patchcam.app.models.AstValidationResult
import com.patchcam.app.models.Diagnosis
import com.patchcam.app.models.MatchMethod
import com.patchcam.app.models.PatchCandidate

enum class ChatIntent {
    WHY,
    WHERE,
    FIX,
    MORE_ERRORS,
    BEGINNER,
    FIXED_CODE,
    SAFE,
    PREVENT,
    TERM,
    THANKS,
    UNKNOWN
}

data class LocalAnswer(
    val text: String,
    val intent: ChatIntent,
    /** False when the question is outside what the diagnosis can answer. */
    val handled: Boolean
)

/**
 * PatchCam's offline answer engine.
 *
 * Instead of depending on a multi-GB language model being installed,
 * the chat answers from the facts PatchCam already established about
 * the scan: the exact line, the parser evidence, the patch and its
 * validation. Answers that need fresh evidence (for example "are there
 * more errors?") are computed by really re-running Python's parser on
 * the patched code, so they are checked, not guessed.
 *
 * An installed on-device LLM is only an optional extra for open-ended
 * questions that fall outside these intents.
 */
object LocalDebugAnswerer {

    val suggestedQuestions: List<String> = listOf(
        "Why did this happen?",
        "Which line is wrong?",
        "Are there more errors?",
        "Explain like I'm a beginner",
        "Show the fixed code"
    )

    fun answer(
        diagnosis: Diagnosis,
        question: String,
        validate: (String) -> AstValidationResult?
    ): LocalAnswer {

        val q = normalize(question)

        return when (detect(q)) {
            ChatIntent.THANKS -> reply(
                ChatIntent.THANKS,
                "You're welcome! When you try the fix, tell PatchCam on the Flow tab whether it worked — " +
                        "it uses that to judge similar fixes next time."
            )

            ChatIntent.WHY -> reply(ChatIntent.WHY, why(diagnosis))
            ChatIntent.WHERE -> reply(ChatIntent.WHERE, locationAnswer(diagnosis))
            ChatIntent.FIX -> reply(ChatIntent.FIX, fix(diagnosis))
            ChatIntent.MORE_ERRORS -> reply(ChatIntent.MORE_ERRORS, moreErrors(diagnosis, validate))
            ChatIntent.BEGINNER -> reply(ChatIntent.BEGINNER, beginner(diagnosis))
            ChatIntent.FIXED_CODE -> reply(ChatIntent.FIXED_CODE, fixedCode(diagnosis))
            ChatIntent.SAFE -> reply(ChatIntent.SAFE, safety(diagnosis))
            ChatIntent.PREVENT -> reply(ChatIntent.PREVENT, prevention(diagnosis))

            ChatIntent.TERM -> {
                val topic = KnowledgeBase.findForQuestion(q)
                if (topic != null) reply(ChatIntent.TERM, term(topic.title, topic.meaning, topic.broken, topic.fixed, topic.tip))
                else fallback(diagnosis, question)
            }

            ChatIntent.UNKNOWN -> fallback(diagnosis, question)
        }
    }

    /* ---------------------------------------------------------------------- */
    /* INTENT DETECTION                                                       */
    /* ---------------------------------------------------------------------- */

    private fun reply(intent: ChatIntent, text: String) =
        LocalAnswer(text = text, intent = intent, handled = true)

    private fun normalize(text: String): String =
        text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .let { " $it " }

    private fun has(q: String, vararg phrases: String): Boolean =
        phrases.any { q.contains(it) }

    private fun detect(q: String): ChatIntent {

        if (has(q, " thanks ", " thank you ", " thx ", " tq ")) return ChatIntent.THANKS

        if (has(
                q,
                "more error", "other error", "another error", "any other", "anything else",
                "else wrong", "remaining error", "all errors", "more mistake", "other mistake",
                "any more", "still wrong", " inka ", "migata"
            )
        ) return ChatIntent.MORE_ERRORS

        if (has(
                q,
                "which line", "what line", "line number", " where ", "location",
                "exact place", "which part", " ekkada ", " e line ", " ye line "
            )
        ) return ChatIntent.WHERE

        if (has(
                q,
                "fixed code", "corrected code", "full code", "whole code", "final code",
                "updated code", "show code", "show me the code", "fixed version", "correct code"
            )
        ) return ChatIntent.FIXED_CODE

        if (has(
                q,
                "beginner", "simple", "simply", "eli5", "like im", "like i am", "layman",
                "easy words", "easier", "dont understand", "confus"
            )
        ) return ChatIntent.BEGINNER

        if (has(
                q,
                " safe ", "verified", "trust", "will it work", "are you sure", "sure about",
                "confident", "reliable", "accurate", "correct patch", "is it right",
                "is this right", "correct fix"
            )
        ) return ChatIntent.SAFE

        if (has(q, "avoid", "prevent", "next time", " tip", "remember", "never again"))
            return ChatIntent.PREVENT

        val definition = has(q, "what is ", "what does ", "whats ", "what are ", "meaning of", "define ")
        val aboutThisError = has(q, " this error", " my error", " this problem", " this mistake", " this ")

        if (definition && !aboutThisError && KnowledgeBase.findForQuestion(q) != null) {
            return ChatIntent.TERM
        }

        if (has(
                q,
                " why ", "cause", "reason", "what happened", "what went wrong", "how did this",
                "how come", " enduku ", "what does this error mean", "what is this error", "explain"
            )
        ) return ChatIntent.WHY

        if (has(
                q,
                " fix", "solve", "resolve", "correct", "change", "what should i do",
                "what do i do", "how do i", "how to", "repair", "patch", " ela ", "emi cheyali"
            )
        ) return ChatIntent.FIX

        if (KnowledgeBase.findForQuestion(q) != null) return ChatIntent.TERM

        return ChatIntent.UNKNOWN
    }

    /* ---------------------------------------------------------------------- */
    /* ANSWERS                                                                */
    /* ---------------------------------------------------------------------- */

    private fun why(d: Diagnosis): String = buildString {
        append(d.explanation.whyItHappened)

        d.location?.let {
            append("\n\nWhere: ${it.label()} — `${it.lineText}`")
        }
    }

    private fun locationAnswer(d: Diagnosis): String {
        val loc = d.location
            ?: return "I couldn't pin this down to one exact line from the scan. " +
                    d.explanation.howToFix

        return buildString {
            append("The problem is at ${loc.label().replaceFirstChar { it.lowercase() }}:\n")
            append("```\n${loc.lineText}\n```\n")
            append(d.explanation.howToFix)
        }
    }

    private fun fix(d: Diagnosis): String = buildString {
        append(d.explanation.howToFix)

        val patch = d.patch
        if (patch != null && patch.newCode.isNotBlank()) {

            val label = d.location?.let { "line ${it.line}" } ?: "line ${patch.targetLine}"

            append("\n\nChange $label")
            append(":\n```\n")
            if (patch.oldCode.isNotBlank()) append("- ${patch.oldCode.trim()}\n")
            append("+ ${patch.newCode.trim()}\n```")

            append(
                if (patch.verified) {
                    "\nPython's parser accepts the code after this change."
                } else {
                    "\nNot verified — review it before applying."
                }
            )
        }
    }

    private fun beginner(d: Diagnosis): String {
        val e = d.explanation

        return buildString {
            if (e.beginnerExplanation.isNotBlank()) {
                append(e.beginnerExplanation)
            } else {
                append("In simple words: ")
                append(e.whyItHappened)
            }
            append("\n\nWhat to do: ")
            append(e.howToFix)
        }
    }

    private fun prevention(d: Diagnosis): String {
        val tip = d.explanation.preventionTip

        return if (tip.isNotBlank()) {
            tip
        } else {
            "Run your code often in small pieces, and read the last line of an error message first — " +
                    "it names the problem. Then go to the line number it gives."
        }
    }

    private fun term(
        title: String,
        meaning: String,
        broken: String,
        fixed: String,
        tip: String
    ): String = buildString {
        append(title)
        append(": ")
        append(meaning)

        if (broken.isNotBlank() && fixed.isNotBlank()) {
            append("\n\nWrong:\n```\n$broken\n```\nRight:\n```\n$fixed\n```")
        }

        if (tip.isNotBlank()) {
            append("\nTip: ")
            append(tip)
        }

        append("\n\nYou can practise this in the Knowledge tab.")
    }

    private fun safety(d: Diagnosis): String {
        val patch = d.patch

        return buildString {
            when {
                patch == null || patch.newCode.isBlank() ->
                    append("PatchCam did not produce an automatic patch for this one, so there is nothing to verify. Follow the steps under HOW TO FIX IT.")

                patch.verified ->
                    append(
                        "Yes, as far as syntax goes. PatchCam applied the patch to the code it read and " +
                                "Python's own parser accepted the result. That proves the syntax is valid — " +
                                "it can't prove your program does what you intend, so run it to confirm."
                    )

                d.validation != null && !d.validation.valid ->
                    append(
                        "Not verified. After applying the patch, Python's parser still reports: " +
                                (d.validation.errorMessage ?: "an error") +
                                ". Treat it as a suggestion only."
                    )

                else ->
                    append(
                        "Not verified. This suggestion came from ${methodName(patch)} and couldn't be " +
                                "checked by a parser on this phone (only Python can be checked here). " +
                                "Review it yourself before applying."
                    )
            }

            append("\n\nConfidence ${d.confidence}% is an evidence score — how much supports this diagnosis — not a guarantee. ")
            append("Scan clarity was ${d.ocrReliability}%.")
        }
    }

    private fun methodName(p: PatchCandidate): String = when (p.matchMethod) {
        MatchMethod.LOCAL_TABLE -> "PatchCam's built-in fix rules"
        MatchMethod.ON_DEVICE_LLM -> "the on-device language model"
        MatchMethod.HEURISTIC -> "a safe heuristic"
    }

    private fun fixedCode(d: Diagnosis): String {
        val patch = d.patch

        if (patch == null || patch.newCode.isBlank()) {
            return "PatchCam has no automatic patch for this one, so there is no corrected code to show. " +
                    "Use the steps under HOW TO FIX IT."
        }

        val patched = applyPatch(d.code, patch)
        val lines = patched.lines()

        val changedIndex = lines.indexOfFirst { it.contains(patch.newCode.trim()) }

        val shown = if (lines.size > 24 && changedIndex >= 0) {
            val from = (changedIndex - 4).coerceAtLeast(0)
            val to = (changedIndex + 5).coerceAtMost(lines.size)
            "…\n" + lines.subList(from, to).joinToString("\n") + "\n…"
        } else {
            patched
        }

        return buildString {
            append("Here is your code with the fix applied")
            d.location?.let { append(" (changed: line ${it.line})") }
            append(":\n```\n")
            append(shown)
            append("\n```\n")
            append(
                if (patch.verified) {
                    "Python's parser accepts this version."
                } else {
                    "This version is not verified — review it before using it."
                }
            )
        }
    }

    /**
     * "Are there more errors?" — answered by evidence: apply the patch in
     * memory, then run Python's parser over the whole code again.
     */
    private fun moreErrors(
        d: Diagnosis,
        validate: (String) -> AstValidationResult?
    ): String {

        if (!LanguageDetector.isPython(d.code)) {
            return "PatchCam can look for further errors on-device only in Python code. " +
                    "For this language, apply the fix, rebuild, and scan again — the compiler will report the next problem, if there is one."
        }

        val patch = d.patch
        if (patch == null || patch.newCode.isBlank()) {
            return "Python's parser stops at the first error it meets, and PatchCam has no automatic patch " +
                    "for this one — so I can't look past it yet. Fix " +
                    (d.location?.let { "line ${it.line}" } ?: "the reported line") +
                    " using the steps under HOW TO FIX IT, then scan again to check for more."
        }

        val offset = d.location?.let { loc ->
            loc.codeIndex?.let { idx -> loc.line - idx }
        } ?: 0

        var current = applyPatch(d.code, patch)
        val problems = mutableListOf<String>()
        var stoppedEarly = false

        var rounds = 0
        while (rounds < 5) {
            rounds++

            val result = validate(current)
                ?: return "PatchCam couldn't run Python's parser just now, so I can't check for more errors. Try again in a moment."

            if (result.valid) break

            val lineNo = result.errorLine
            val shownLine = lineNo?.let { it + offset }
            val text = result.sourceLine?.trim().orEmpty()
            val message = (result.errorMessage ?: "syntax error").substringBefore(" (")

            problems += buildString {
                append("•  ")
                append(if (shownLine != null) "Around line $shownLine" else "Somewhere further down")
                if (text.isNotEmpty()) append(": `$text`")
                append(" — $message")
            }

            // Try a known fix so the parser can look even further down.
            val next = runCatching {
                KnownFixTable.findMatch(current, "SyntaxError: ${result.errorMessage.orEmpty()}")
            }.getOrNull()

            val patched = if (next != null && next.newCode.isNotBlank()) applyPatch(current, next) else current

            if (patched == current) {
                stoppedEarly = true
                break
            }
            current = patched
        }

        val fixedLabel = d.location?.let { "line ${it.line}" } ?: "the reported line"

        return buildString {
            if (problems.isEmpty()) {
                append("Good news: after the fix on $fixedLabel, Python's parser found no more syntax errors in the code PatchCam read.")
                append("\n\nThis only covers syntax. Mistakes that appear while the program runs — like using a variable that doesn't exist yet — can't be caught this way.")
            } else {
                append("After fixing $fixedLabel, Python's parser found ")
                append(if (problems.size == 1) "1 more problem:" else "${problems.size} more problems:")
                append("\n")
                append(problems.joinToString("\n"))
                if (stoppedEarly) {
                    append("\n\nI stopped there because the parser reports one error at a time and PatchCam has no safe automatic fix for the last one.")
                }
                append("\n\nLine numbers after the first fix are approximate. Fix them one at a time and scan again to confirm.")
            }

            if (d.ocrReliability < 70) {
                append("\n\nThe scan wasn't perfectly clear (${d.ocrReliability}%), so a re-scan will make this more trustworthy.")
            }
        }
    }

    private fun fallback(d: Diagnosis, question: String = ""): LocalAnswer {
        val e = d.explanation

        // Retrieval over PatchCam's own knowledge base (no model needed).
        val hit = if (question.isBlank()) null else LocalRetriever.retrieve(question, 1).firstOrNull()

        val text = buildString {
            if (hit != null) {
                append(hit.title)
                append(": ")
                append(hit.text)
                if (hit.broken.isNotBlank() && hit.fixed.isNotBlank()) {
                    append("\n\nWrong:\n```\n${hit.broken}\n```\nRight:\n```\n${hit.fixed}\n```")
                }
                append("\n\nSource: ${hit.citation}\n\n")
            }
            append("I can answer questions about this specific error. Try asking:\n")
            append("•  Why did this happen?\n")
            append("•  Which line is wrong?\n")
            append("•  How do I fix it?\n")
            append("•  Are there more errors?\n")
            append("•  Show the fixed code\n")
            append("•  What is a traceback? (or any term from the Knowledge tab)\n\n")
            append("In short: ")
            append(e.whatWentWrong.trimEnd('.'))
            append(". ")
            append(e.howToFix)
        }

        return LocalAnswer(text = text, intent = ChatIntent.UNKNOWN, handled = false)
    }

    private fun applyPatch(code: String, patch: PatchCandidate): String {
        if (patch.newCode.isBlank()) return code
        if (patch.oldCode.isBlank()) return patch.newCode + "\n" + code
        return code.replaceFirst(patch.oldCode, patch.newCode)
    }
}
