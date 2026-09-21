package com.patchcam.app.pipeline

import com.patchcam.app.models.LanguageTrack

object LanguageDetector {

    /**
     * Checks for Python cues (def, import, : block ends, print) vs
     * C-family cues (semicolons, braces, const/let, public class, std::cout).
     */
    fun isPython(codeText: String): Boolean {
        var pythonScore = 0
        var nonPythonScore = 0

        // Python cues
        if (Regex("""\bdef\s+[a-zA-Z_]\w*\s*\(""").containsMatchIn(codeText)) pythonScore += 3
        if (Regex("""\b(import\s+[a-zA-Z_]|from\s+[a-zA-Z_]\w*\s+import)""").containsMatchIn(codeText)) pythonScore += 3
        if (Regex("""(def|if|for|while|class|elif|else):\s*$""", RegexOption.MULTILINE).containsMatchIn(codeText)) pythonScore += 3
        if (Regex("""\b(None|True|False|elif|self|pass)\b""").containsMatchIn(codeText)) pythonScore += 2
        if (Regex("""\bprint\s*\(""").containsMatchIn(codeText) && !codeText.contains("System.out") && !codeText.contains("console.log")) pythonScore += 2

        // Non-Python cues
        if (Regex(""";\s*$""", RegexOption.MULTILINE).findAll(codeText).count() >= 2) nonPythonScore += 4
        if (codeText.contains("{") || codeText.contains("}")) nonPythonScore += 3
        if (Regex("""\b(const|let|var|console\.log|function)\b""").containsMatchIn(codeText)) nonPythonScore += 4
        if (Regex("""\b(public\s+class|System\.out\.println)\b""").containsMatchIn(codeText)) nonPythonScore += 4
        if (Regex("""\b(std::cout|#include)\b""").containsMatchIn(codeText)) nonPythonScore += 4

        return pythonScore >= nonPythonScore && pythonScore > 0 || (nonPythonScore == 0 && codeText.isNotBlank())
    }

    fun getTrack(codeText: String): LanguageTrack {
        return if (isPython(codeText)) LanguageTrack.PYTHON_VERIFIED else LanguageTrack.OTHER_UNVERIFIED
    }
}
