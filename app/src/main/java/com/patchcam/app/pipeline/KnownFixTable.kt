package com.patchcam.app.pipeline

import com.patchcam.app.models.MatchMethod
import com.patchcam.app.models.PatchCandidate

data class KnownFix(
    val id: String,
    val errorType: String,
    val errorRegex: Regex,
    val codeRegex: Regex? = null,
    val fixDescription: String,
    val patchGenerator: (code: String, error: String) -> PatchCandidate?
)

object KnownFixTable {

    val fixes: List<KnownFix> = listOf(
        // --- SyntaxError (1-5) ---
        KnownFix(
            id = "SYNTAX_MISSING_COLON_DEF",
            errorType = "SyntaxError",
            errorRegex = Regex("""(expected ':'|invalid syntax)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""def\s+[a-zA-Z_]\w*\s*\([^)]*\)\s*$""", RegexOption.MULTILINE),
            fixDescription = "Add missing colon to function declaration",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""def\s+[a-zA-Z_]\w*\s*\([^)]*\)\s*$""").containsMatchIn(it) }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].trimEnd() + ":",
                        description = "Add missing ':' at end of def statement",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "SYNTAX_MISSING_COLON_DEF"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "SYNTAX_MISSING_COLON_BLOCK",
            errorType = "SyntaxError",
            errorRegex = Regex("""(expected ':'|invalid syntax)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""^\s*(if|for|while|elif|class)\s+[^:]+$""", RegexOption.MULTILINE),
            fixDescription = "Add missing colon to block header",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""^\s*(if|for|while|elif|class)\s+[^:]+$""").containsMatchIn(it) }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].trimEnd() + ":",
                        description = "Add missing ':' at end of statement header",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "SYNTAX_MISSING_COLON_BLOCK"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "SYNTAX_SINGLE_EQUALS",
            errorType = "SyntaxError",
            errorRegex = Regex("""(invalid syntax|cannot assign to expression|did you mean '==')""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""^\s*(if|elif)\s+[^=]*\b[a-zA-Z_]\w*\s*=\s*[^=]+:""", RegexOption.MULTILINE),
            fixDescription = "Replace assignment '=' with comparison '=='",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""^\s*(if|elif)\s+.*(?<!=)=(?!=).*:""").containsMatchIn(it) }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].replace(Regex("""(\s+)(?<!=)=(?!=)(\s+)"""), "$1==$2"),
                        description = "Replace assignment operator '=' with equality '==' in conditional",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "SYNTAX_SINGLE_EQUALS"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "SYNTAX_PRINT_PARENS",
            errorType = "SyntaxError",
            errorRegex = Regex("""(Missing parentheses in call to 'print'|invalid syntax)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""^\s*print\s+[^(]""", RegexOption.MULTILINE),
            fixDescription = "Wrap print argument in parentheses for Python 3",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""^\s*print\s+[^(]""").containsMatchIn(it) }
                if (idx != -1) {
                    val match = Regex("""^(\s*)print\s+(.+)$""").find(lines[idx])
                    if (match != null) {
                        PatchCandidate(
                            targetLine = idx + 1,
                            oldCode = lines[idx],
                            newCode = "${match.groupValues[1]}print(${match.groupValues[2]})",
                            description = "Add parentheses to print statement",
                            verified = true,
                            matchMethod = MatchMethod.LOCAL_TABLE,
                            ruleId = "SYNTAX_PRINT_PARENS"
                        )
                    } else null
                } else null
            }
        ),
        KnownFix(
            id = "SYNTAX_UNCLOSED_PAREN",
            errorType = "SyntaxError",
            errorRegex = Regex("""(unexpected EOF while parsing|was never closed)""", RegexOption.IGNORE_CASE),
            codeRegex = null,
            fixDescription = "Close unclosed opening parenthesis",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                var target = -1
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.count { it == '(' } > line.count { it == ')' }) {
                        target = i
                        break
                    }
                }
                if (target != -1) {
                    PatchCandidate(
                        targetLine = target + 1,
                        oldCode = lines[target],
                        newCode = lines[target] + ")",
                        description = "Append missing closing parenthesis ')'",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "SYNTAX_UNCLOSED_PAREN"
                    )
                } else null
            }
        ),

        // --- IndentationError (6-10) ---
        KnownFix(
            id = "INDENT_EXPECTED_BLOCK",
            errorType = "IndentationError",
            errorRegex = Regex("""(expected an indented block)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""def\s+.*:\s*$""", RegexOption.MULTILINE),
            fixDescription = "Indent body after def header",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.trim().endsWith(":") }
                if (idx != -1 && idx + 1 < lines.size) {
                    PatchCandidate(
                        targetLine = idx + 2,
                        oldCode = lines[idx + 1],
                        newCode = "    " + lines[idx + 1].trimStart(),
                        description = "Indent block body with 4 spaces",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "INDENT_EXPECTED_BLOCK"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "INDENT_TAB_SPACE_CONFLICT",
            errorType = "IndentationError",
            errorRegex = Regex("""(inconsistent use of tabs and spaces|TabError)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""\t"""),
            fixDescription = "Convert tabs to 4 spaces",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.contains("\t") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].replace("\t", "    "),
                        description = "Replace hard tab with 4 spaces",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "INDENT_TAB_SPACE_CONFLICT"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "INDENT_UNEXPECTED_INDENT",
            errorType = "IndentationError",
            errorRegex = Regex("""(unexpected indent)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""^ +""", RegexOption.MULTILINE),
            fixDescription = "Remove spurious top-level indentation",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.startsWith(" ") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].trimStart(),
                        description = "Strip unexpected leading whitespace",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "INDENT_UNEXPECTED_INDENT"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "INDENT_UNINDENT_MISMATCH",
            errorType = "IndentationError",
            errorRegex = Regex("""(unindent does not match any outer indentation level)""", RegexOption.IGNORE_CASE),
            codeRegex = null,
            fixDescription = "Align unindent to 4-space boundary",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                for (i in lines.indices) {
                    val leading = lines[i].takeWhile { it == ' ' }.length
                    if (leading > 0 && leading % 4 != 0) {
                        val corrected = " ".repeat((leading / 4) * 4)
                        return@KnownFix PatchCandidate(
                            targetLine = i + 1,
                            oldCode = lines[i],
                            newCode = corrected + lines[i].trimStart(),
                            description = "Align indentation to 4-space multiple",
                            verified = true,
                            matchMethod = MatchMethod.LOCAL_TABLE,
                            ruleId = "INDENT_UNINDENT_MISMATCH"
                        )
                    }
                }
                null
            }
        ),
        KnownFix(
            id = "INDENT_EMPTY_BLOCK_PASS",
            errorType = "IndentationError",
            errorRegex = Regex("""(expected an indented block)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex(""":\s*$""", RegexOption.MULTILINE),
            fixDescription = "Add pass statement to empty block",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfLast { it.trim().endsWith(":") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx] + "\n    pass",
                        description = "Insert 'pass' into empty block",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "INDENT_EMPTY_BLOCK_PASS"
                    )
                } else null
            }
        ),

        // --- NameError (11-15) ---
        KnownFix(
            id = "NAME_SQRT_UNIMPORTED",
            errorType = "NameError",
            errorRegex = Regex("""name 'sqrt' is not defined""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""\bsqrt\("""),
            fixDescription = "Import sqrt from math",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                PatchCandidate(
                    targetLine = 1,
                    oldCode = lines.firstOrNull() ?: "",
                    newCode = "from math import sqrt\n" + (lines.firstOrNull() ?: ""),
                    description = "Add 'from math import sqrt' import",
                    verified = true,
                    matchMethod = MatchMethod.LOCAL_TABLE,
                    ruleId = "NAME_SQRT_UNIMPORTED"
                )
            }
        ),
        KnownFix(
            id = "NAME_OS_UNIMPORTED",
            errorType = "NameError",
            errorRegex = Regex("""name 'os' is not defined""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""\bos\."""),
            fixDescription = "Import os module",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                PatchCandidate(
                    targetLine = 1,
                    oldCode = lines.firstOrNull() ?: "",
                    newCode = "import os\n" + (lines.firstOrNull() ?: ""),
                    description = "Add 'import os' header",
                    verified = true,
                    matchMethod = MatchMethod.LOCAL_TABLE,
                    ruleId = "NAME_OS_UNIMPORTED"
                )
            }
        ),
        KnownFix(
            id = "NAME_SYS_UNIMPORTED",
            errorType = "NameError",
            errorRegex = Regex("""name 'sys' is not defined""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""\bsys\."""),
            fixDescription = "Import sys module",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                PatchCandidate(
                    targetLine = 1,
                    oldCode = lines.firstOrNull() ?: "",
                    newCode = "import sys\n" + (lines.firstOrNull() ?: ""),
                    description = "Add 'import sys' header",
                    verified = true,
                    matchMethod = MatchMethod.LOCAL_TABLE,
                    ruleId = "NAME_SYS_UNIMPORTED"
                )
            }
        ),
        KnownFix(
            id = "NAME_SELF_PARAM_MISSING",
            errorType = "NameError",
            errorRegex = Regex("""name 'self' is not defined""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""def\s+[a-zA-Z_]\w*\s*\(\s*\):"""),
            fixDescription = "Add self parameter to method",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""def\s+[a-zA-Z_]\w*\s*\(\s*\):""").containsMatchIn(it) }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = lines[idx].replace(Regex("""\(\s*\):"""), "(self):"),
                        description = "Add 'self' parameter to instance method",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "NAME_SELF_PARAM_MISSING"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "NAME_LOWERCASE_BOOLEAN",
            errorType = "NameError",
            errorRegex = Regex("""name '(true|false|none)' is not defined""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""\b(true|false|none)\b"""),
            fixDescription = "Capitalize Python True, False, None",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""\b(true|false|none)\b""").containsMatchIn(it) }
                if (idx != -1) {
                    val corrected = lines[idx]
                        .replace(Regex("""\btrue\b"""), "True")
                        .replace(Regex("""\bfalse\b"""), "False")
                        .replace(Regex("""\bnone\b"""), "None")
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = corrected,
                        description = "Capitalize boolean literal (True/False/None)",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "NAME_LOWERCASE_BOOLEAN"
                    )
                } else null
            }
        ),

        // --- ImportError (16-20) ---
        KnownFix(
            id = "IMPORT_OS_PATH_JOIN",
            errorType = "ImportError",
            errorRegex = Regex("""cannot import name 'path_join'""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""from\s+os\s+import\s+path_join"""),
            fixDescription = "Fix path_join to from os.path import join",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""from\s+os\s+import\s+path_join""").containsMatchIn(it) }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = "from os.path import join",
                        description = "Correct import to 'from os.path import join'",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "IMPORT_OS_PATH_JOIN"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "IMPORT_DATETIME_TYPO",
            errorType = "ImportError",
            errorRegex = Regex("""No module named 'date_time'""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""import\s+date_time"""),
            fixDescription = "Fix date_time to datetime",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.contains("import date_time") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = "import datetime",
                        description = "Fix module name to 'import datetime'",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "IMPORT_DATETIME_TYPO"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "IMPORT_URLPARSE_PY3",
            errorType = "ImportError",
            errorRegex = Regex("""No module named 'urlparse'""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""import\s+urlparse"""),
            fixDescription = "Update urlparse to urllib.parse",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.contains("import urlparse") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = "import urllib.parse as urlparse",
                        description = "Migrate urlparse to urllib.parse as urlparse",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "IMPORT_URLPARSE_PY3"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "IMPORT_QUEUE_CASE",
            errorType = "ImportError",
            errorRegex = Regex("""No module named 'Queue'""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""import\s+Queue"""),
            fixDescription = "Change Queue to lowercase queue for Python 3",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { it.contains("import Queue") }
                if (idx != -1) {
                    PatchCandidate(
                        targetLine = idx + 1,
                        oldCode = lines[idx],
                        newCode = "import queue as Queue",
                        description = "Change module to 'queue' for Python 3",
                        verified = true,
                        matchMethod = MatchMethod.LOCAL_TABLE,
                        ruleId = "IMPORT_QUEUE_CASE"
                    )
                } else null
            }
        ),
        KnownFix(
            id = "IMPORT_CIRCULAR_SAFE",
            errorType = "ImportError",
            errorRegex = Regex("""(cannot import name .* from partially initialized module|circular import)""", RegexOption.IGNORE_CASE),
            codeRegex = Regex("""from\s+([a-zA-Z_]\w*)\s+import"""),
            fixDescription = "Switch from-import to top-level module import",
            patchGenerator = { code, _ ->
                val lines = code.lines()
                val idx = lines.indexOfFirst { Regex("""from\s+([a-zA-Z_]\w*)\s+import""").containsMatchIn(it) }
                if (idx != -1) {
                    val match = Regex("""from\s+([a-zA-Z_]\w*)\s+import""").find(lines[idx])
                    if (match != null) {
                        val mod = match.groupValues[1]
                        PatchCandidate(
                            targetLine = idx + 1,
                            oldCode = lines[idx],
                            newCode = "import $mod  # Direct module import to prevent circular import",
                            description = "Change from-import to direct import $mod",
                            verified = true,
                            matchMethod = MatchMethod.LOCAL_TABLE,
                            ruleId = "IMPORT_CIRCULAR_SAFE"
                        )
                    } else null
                } else null
            }
        )
    )

    fun findMatch(codeText: String, errorText: String): PatchCandidate? {
        for (fix in fixes) {
            if (fix.errorRegex.containsMatchIn(errorText)) {
                if (fix.codeRegex == null || fix.codeRegex.containsMatchIn(codeText)) {
                    val candidate = fix.patchGenerator(codeText, errorText)
                    if (candidate != null) return candidate
                }
            }
        }
        return null
    }
}
