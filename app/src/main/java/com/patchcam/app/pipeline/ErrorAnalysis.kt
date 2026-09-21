package com.patchcam.app.pipeline

import com.patchcam.app.models.AstValidationResult
import com.patchcam.app.models.ErrorKind
import com.patchcam.app.models.ErrorLocation
import com.patchcam.app.models.PatchCandidate

/* -------------------------------------------------------------------------- */
/* 1. WHAT KIND OF ERROR IS THIS?                                             */
/* -------------------------------------------------------------------------- */

object ErrorKindDetector {

    private val colonExpected =
        Regex("""expected\s*['"‘’]?\s*:\s*['"‘’]?""")

    fun detect(errorText: String, ruleId: String? = null): ErrorKind {
        val t = errorText.lowercase()

        val byMessage = when {
            colonExpected.containsMatchIn(t) -> ErrorKind.MISSING_COLON

            "was never closed" in t ||
                    "unexpected eof" in t ||
                    "unmatched '" in t ->
                ErrorKind.UNCLOSED_BRACKET

            "unterminated string" in t ||
                    "eol while scanning" in t ||
                    "unterminated triple" in t ->
                ErrorKind.UNTERMINATED_STRING

            "missing parentheses in call to 'print'" in t -> ErrorKind.PRINT_PARENS

            "maybe you meant '=='" in t ||
                    "did you mean '=='" in t ->
                ErrorKind.SINGLE_EQUALS

            "expected an indented block" in t -> ErrorKind.EXPECTED_INDENT
            "unexpected indent" in t -> ErrorKind.UNEXPECTED_INDENT
            "unindent does not match" in t -> ErrorKind.UNINDENT_MISMATCH

            "taberror" in t ||
                    "inconsistent use of tabs" in t ->
                ErrorKind.TAB_ERROR

            "nameerror" in t ||
                    Regex("""name '[^']+' is not defined""").containsMatchIn(t) ->
                ErrorKind.NAME_ERROR

            "modulenotfounderror" in t || "no module named" in t ->
                ErrorKind.MODULE_NOT_FOUND

            "attributeerror" in t || "has no attribute" in t ->
                ErrorKind.ATTRIBUTE_ERROR

            "indexerror" in t || "index out of range" in t -> ErrorKind.INDEX_ERROR
            "keyerror" in t -> ErrorKind.KEY_ERROR

            "zerodivisionerror" in t || "division by zero" in t ->
                ErrorKind.ZERO_DIVISION

            "typeerror" in t -> ErrorKind.TYPE_ERROR
            "valueerror" in t -> ErrorKind.VALUE_ERROR

            "unresolved reference" in t -> ErrorKind.UNRESOLVED_REFERENCE

            "syntaxerror" in t || "invalid syntax" in t -> ErrorKind.INVALID_SYNTAX

            else -> ErrorKind.OTHER
        }

        if (byMessage != ErrorKind.INVALID_SYNTAX && byMessage != ErrorKind.OTHER) {
            return byMessage
        }

        // Older Pythons only say "invalid syntax"; the matched fix rule
        // tells us what PatchCam actually found on the line.
        val byRule = when (ruleId) {
            "SYNTAX_MISSING_COLON_DEF",
            "SYNTAX_MISSING_COLON_BLOCK" -> ErrorKind.MISSING_COLON

            "SYNTAX_SINGLE_EQUALS" -> ErrorKind.SINGLE_EQUALS
            "SYNTAX_PRINT_PARENS" -> ErrorKind.PRINT_PARENS
            "SYNTAX_UNCLOSED_PAREN" -> ErrorKind.UNCLOSED_BRACKET
            else -> null
        }

        return byRule ?: byMessage
    }
}

/* -------------------------------------------------------------------------- */
/* 2. WHERE EXACTLY IS IT?                                                    */
/* -------------------------------------------------------------------------- */

object ErrorLocator {

    private val quote = """["'“”‘’]"""

    private val fileLine =
        Regex("""File\s+$quote[^"'“”‘’]*$quote\s*,\s*line\s+(\d+)""", RegexOption.IGNORE_CASE)

    private val fileColonLine =
        Regex("""[\w./\\-]+\.(?:kt|kts|java|py|js|ts|tsx|cpp|c|h):(\d+)""")

    private val bareLine =
        Regex(""",\s*line\s+(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * The line number the user's OWN interpreter/compiler reported, taken
     * from the traceback that was on screen. Only "File ..., line N" style
     * references are used, never a "line N" that appears inside the message
     * itself (for example "block after 'if' statement on line 3", which
     * points at a different line than the one that failed).
     */
    fun tracebackLineNumber(ocrErrorText: String): Int? {
        fileLine.findAll(ocrErrorText).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        fileColonLine.findAll(ocrErrorText).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        return bareLine.findAll(ocrErrorText).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Combines three independent sources of evidence:
     *  - the parser result on the reconstructed source (best for syntax
     *    errors: exact line + column),
     *  - the line the patch was built for,
     *  - the line number printed in the on-screen traceback (the truth for
     *    what the user sees in their own file).
     *
     * The reconstructed source can be missing blank lines, so its line
     * numbers are only used to FIND the failing line; the number shown to
     * the user prefers the traceback's number.
     */
    fun locate(
        ocrErrorText: String,
        reconstructed: String,
        sourceValidation: AstValidationResult?,
        patch: PatchCandidate?,
        kind: ErrorKind
    ): ErrorLocation? {

        val lines = reconstructed.lines()
        val tracebackLine = tracebackLineNumber(ocrErrorText)

        var codeIndex: Int? = null
        var column: Int? = null

        val parserLine = sourceValidation
            ?.takeIf { !it.valid }
            ?.errorLine
            ?.takeIf { it in 1..lines.size }

        if (parserLine != null) {
            codeIndex = parserLine
            column = sourceValidation?.errorOffset
        }

        if (codeIndex == null) {
            codeIndex = indexFromHint(kind, ocrErrorText, lines)
        }

        if (codeIndex == null && patch != null && patch.oldCode.isNotBlank()) {
            val idx = lines.indexOfFirst { it.trim() == patch.oldCode.trim() }
            if (idx >= 0) codeIndex = idx + 1
        }

        if (codeIndex == null && tracebackLine != null && tracebackLine in 1..lines.size) {
            codeIndex = tracebackLine
        }

        val displayLine = tracebackLine ?: codeIndex ?: return null

        val rawLine = codeIndex?.let { lines.getOrNull(it - 1) }.orEmpty()
        val leading = rawLine.length - rawLine.trimStart().length

        val trimmedColumn = column?.let { (it - leading).coerceAtLeast(1) }

        return ErrorLocation(
            line = displayLine,
            column = trimmedColumn,
            lineText = rawLine.trim(),
            codeIndex = codeIndex,
            lineFromTraceback = tracebackLine != null
        )
    }

    /**
     * Runtime errors have no parser column, but the message often names
     * the offending identifier, which is enough to find the line.
     */
    private fun indexFromHint(
        kind: ErrorKind,
        errorText: String,
        lines: List<String>
    ): Int? {

        val token: String? = when (kind) {
            ErrorKind.NAME_ERROR ->
                Regex("""name '([^']+)' is not defined""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)

            ErrorKind.MODULE_NOT_FOUND ->
                Regex("""No module named '([^'.]+)""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)

            ErrorKind.ATTRIBUTE_ERROR ->
                Regex("""has no attribute '([^']+)'""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)

            else -> null
        }

        if (token != null) {
            val word = Regex("""\b${Regex.escape(token)}\b""")
            val idx = lines.indexOfFirst {
                !it.trimStart().startsWith("#") && word.containsMatchIn(it)
            }
            if (idx >= 0) return idx + 1
        }

        if (kind == ErrorKind.ZERO_DIVISION) {
            val idx = lines.indexOfFirst {
                !it.trimStart().startsWith("#") && Regex("""[/%]""").containsMatchIn(it)
            }
            if (idx >= 0) return idx + 1
        }

        return null
    }
}

/* -------------------------------------------------------------------------- */
/* 3. SAY IT IN PLAIN, SPECIFIC LANGUAGE                                      */
/* -------------------------------------------------------------------------- */

data class PlainExplanation(
    val headline: String,
    val why: String,
    val howToFix: String,
    val example: String,
    val beginner: String,
    val tip: String
)

object PlainLanguageExplainer {

    /**
     * Returns null when PatchCam has no specific wording for this kind of
     * error; the caller then keeps its existing generic explanation.
     */
    fun explain(
        kind: ErrorKind,
        errorText: String,
        location: ErrorLocation?,
        patch: PatchCandidate?
    ): PlainExplanation? {

        val lineText = location?.lineText?.takeIf { it.isNotBlank() }
        val at = if (location != null) "line ${location.line}" else "the reported line"
        val At = at.replaceFirstChar { it.uppercase() }
        val codeQ = lineText?.let { "`$it`" }

        // Only trust the patch's "after" line when it is a patch FOR the
        // failing line.
        val patchLine: String? = patch
            ?.takeIf {
                it.newCode.isNotBlank() &&
                        it.oldCode.isNotBlank() &&
                        (lineText == null || it.oldCode.trim() == lineText)
            }
            ?.newCode
            ?.trim()

        return when (kind) {

            ErrorKind.MISSING_COLON -> {
                val keyword = lineText?.let {
                    Regex("""^(def|class|if|elif|else|for|while|try|except|finally|with|match|case)\b""")
                        .find(it)?.groupValues?.get(1)
                }
                val what = when (keyword) {
                    "def" -> "a function"
                    "class" -> "a class"
                    "if", "elif", "else" -> "an if-condition"
                    "for", "while" -> "a loop"
                    "try", "except", "finally" -> "a try/except block"
                    "with" -> "a with-block"
                    else -> "a block of code"
                }
                val lastChar = lineText?.trimEnd()?.lastOrNull()

                PlainExplanation(
                    headline = "Missing ':' at the end of $at",
                    why = "$At starts $what" +
                            (codeQ?.let { " ($it)" } ?: "") +
                            " but ends without a ':'. Python needs a ':' at the end of this kind of line " +
                            "to know that the indented lines below belong to it.",
                    howToFix = "Go to $at and type ':' at the very end" +
                            (lastChar?.let { ", right after `$it`" } ?: "") + ".",
                    example = patchLine ?: lineText?.let { it.trimEnd() + ":" }.orEmpty(),
                    beginner = "Think of ':' as the word \"then\". In `if x > 5:` you are saying " +
                            "\"if x is bigger than 5, THEN do the lines below\". Python needs that " +
                            "small signal to know where the lines below begin.",
                    tip = "Any line that opens a block (def, if, for, while, class…) must end with ':'. " +
                            "Type it before you press Enter — your editor will then indent the next line for you."
                )
            }

            ErrorKind.UNCLOSED_BRACKET -> {
                val extra = "unmatched" in errorText.lowercase()
                val bracket = Regex("""'([(\[{])' was never closed""")
                    .find(errorText)?.groupValues?.get(1) ?: "("
                val closing = when (bracket) {
                    "[" -> "]"
                    "{" -> "}"
                    else -> ")"
                }

                if (extra) {
                    PlainExplanation(
                        headline = "Extra closing bracket on $at",
                        why = "$At has a closing bracket that has no matching opening bracket before it.",
                        howToFix = "On $at, remove the extra closing bracket, or add the opening bracket that is missing earlier on the line.",
                        example = patchLine.orEmpty(),
                        beginner = "Brackets come in pairs, like a door you open and close. Here one door is being closed that was never opened.",
                        tip = "Count your brackets: every opening ( [ { needs its own closing ) ] }."
                    )
                } else {
                    PlainExplanation(
                        headline = "'$bracket' on $at is never closed",
                        why = "$At opens a '$bracket'" +
                                (codeQ?.let { " ($it)" } ?: "") +
                                " but the matching '$closing' never appears, so Python keeps waiting for it.",
                        howToFix = "Add the missing '$closing' where the expression should end on $at " +
                                "(usually at the very end of the line).",
                        example = patchLine.orEmpty(),
                        beginner = "Brackets come in pairs, like a door you open and must close. " +
                                "You opened '$bracket' and Python is still waiting for '$closing'.",
                        tip = "When you type an opening bracket, type its closing bracket straight away, then fill in the middle."
                    )
                }
            }

            ErrorKind.UNTERMINATED_STRING -> PlainExplanation(
                headline = "A quote is not closed on $at",
                why = "$At has text that starts with a quote mark but never gets a closing quote, " +
                        "so Python thinks the text goes on forever.",
                howToFix = "On $at, add the closing quote (the same kind as the opening one, ' or \") at the end of the text.",
                example = patchLine.orEmpty(),
                beginner = "Quotes work like a pair of brackets around text. You opened the quote but never closed it.",
                tip = "Type both quotes first, then write the text between them."
            )

            ErrorKind.PRINT_PARENS -> PlainExplanation(
                headline = "print needs brackets on $at",
                why = "$At uses the old Python 2 style `print something`. In Python 3, print is a " +
                        "function, so what you want to show must be inside ( ).",
                howToFix = "On $at, put everything after print inside brackets: `print(...)`.",
                example = patchLine.orEmpty(),
                beginner = "In Python 3, print is a command you \"call\", and calls always use brackets, like `print(\"hi\")`.",
                tip = "Always write print(...) with brackets in Python 3."
            )

            ErrorKind.SINGLE_EQUALS -> PlainExplanation(
                headline = "'=' used instead of '==' on $at",
                why = "$At compares two things, but it uses a single '='. In Python a single '=' " +
                        "means \"store this value\". To ask \"are these equal?\" you need '=='.",
                howToFix = "On $at, change the '=' inside the condition to '=='.",
                example = patchLine.orEmpty(),
                beginner = "'=' is like writing a value on a label. '==' is like asking \"are these two the same?\". An `if` always asks a question.",
                tip = "Inside if / while conditions, use == to compare and = only to store a value."
            )

            ErrorKind.INVALID_SYNTAX -> {
                val col = location?.column
                val near = if (lineText != null && col != null) tokenNear(lineText, col) else null

                val why = when {
                    col != null && near != null ->
                        "Python stopped reading $at at column $col, $near. Something is missing or " +
                                "extra right there — most often a ':', a closing bracket, a quote or a comma."

                    codeQ != null ->
                        "Python could not understand the structure of $at ($codeQ). Something on it " +
                                "is missing or in the wrong place — most often a ':', a bracket, a quote or a comma."

                    else ->
                        "Python could not understand the structure of $at. Something on it is " +
                                "missing or in the wrong place — most often a ':', a bracket, a quote or a comma."
                }

                PlainExplanation(
                    headline = "Python can't read $at",
                    why = why,
                    howToFix = "Look at $at" + (if (col != null) " at the spot marked with ^" else "") +
                            ". Also check the end of the line above it — a missing symbol there is reported on the next line." +
                            (patch?.description?.takeIf { patchLine != null }?.let { " PatchCam's suggestion: $it." } ?: ""),
                    example = patchLine.orEmpty(),
                    beginner = "Python reads code like a very strict teacher reading a sentence. " +
                            "Somewhere on this line a punctuation mark is missing or extra, so it can't finish the sentence.",
                    tip = "Read the flagged line slowly, symbol by symbol, and compare it with a similar line that works."
                )
            }

            ErrorKind.EXPECTED_INDENT -> {
                val keyword = Regex("""after '(\w+)' statement""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)
                val opener = keyword?.let { "the `$it` statement" } ?: "the line"

                PlainExplanation(
                    headline = "$At needs to be indented",
                    why = "$opener just above $at opens a block, so $at must be pushed in " +
                            "(indented) by 4 spaces. It is not, so Python sees an empty block.",
                    howToFix = "Add 4 spaces at the start of $at, and of every other line that belongs inside the block. " +
                            "If the block should stay empty for now, write `pass` on that line.",
                    example = patchLine ?: lineText?.let { "    $it" }.orEmpty(),
                    beginner = "Indentation is how Python shows \"this belongs inside that\". Like bullet points under a heading, the lines inside a block must sit further to the right.",
                    tip = "After any line ending in ':', press Enter and let your editor indent — don't type the spaces by hand."
                )
            }

            ErrorKind.UNEXPECTED_INDENT -> PlainExplanation(
                headline = "$At is indented too far",
                why = "$At is pushed further right than the line above it, but the line above does not open a block " +
                        "(it does not end with ':'), so nothing there is meant to be indented.",
                howToFix = "Remove the extra spaces at the start of $at so it lines up with the line above.",
                example = patchLine ?: lineText.orEmpty(),
                beginner = "Only the lines inside a block are allowed to move to the right. This line moved right for no reason.",
                tip = "Use the same number of spaces for lines that belong together."
            )

            ErrorKind.UNINDENT_MISMATCH -> PlainExplanation(
                headline = "Indentation on $at doesn't match any block",
                why = "$At is indented to a level that no earlier block uses, so Python cannot tell which block it belongs to.",
                howToFix = "Change the spaces at the start of $at so they exactly match the block it should belong to " +
                        "(4 spaces per level).",
                example = patchLine.orEmpty(),
                beginner = "Picture a staircase where one step is a different height from the others — Python can't tell which stair you're standing on.",
                tip = "Use 4 spaces per level everywhere and never mix in tabs."
            )

            ErrorKind.TAB_ERROR -> PlainExplanation(
                headline = "Tabs and spaces are mixed near $at",
                why = "Some lines are indented with tabs and others with spaces. They can look the same on screen, but Python treats them as different.",
                howToFix = "Use only spaces (4 per level). In your editor, convert all tabs to spaces.",
                example = patchLine.orEmpty(),
                beginner = "A tab and four spaces look identical to you, but to Python they are different characters, like two look-alike keys.",
                tip = "Turn on \"insert spaces when pressing Tab\" in your editor."
            )

            ErrorKind.NAME_ERROR -> {
                val name = Regex("""name '([^']+)' is not defined""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)
                val n = name?.let { "`$it`" } ?: "a name"

                PlainExplanation(
                    headline = "$n is used before it exists ($at)",
                    why = "$At uses $n, but Python has never seen it: it is not created (assigned) anywhere " +
                            "above this line, it is spelled differently where you created it, or it lives inside another function.",
                    howToFix = "Check the spelling of $n on $at. Make sure a line like `${name ?: "name"} = ...` runs before it, " +
                            "or import it if it comes from a module." +
                            (patch?.description?.takeIf { it.isNotBlank() }?.let { " PatchCam's suggestion: $it." } ?: ""),
                    example = patchLine.orEmpty(),
                    beginner = "You asked Python to use a box called $n, but no box with that label exists yet. Create it first, or check its label for typos.",
                    tip = "Create every variable before you use it, and copy names instead of retyping them."
                )
            }

            ErrorKind.MODULE_NOT_FOUND -> {
                val module = Regex("""No module named '([^']+)'""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)
                val m = module?.let { "`$it`" } ?: "that module"

                PlainExplanation(
                    headline = "Module $m can't be found ($at)",
                    why = "Python looked for $m and could not find it. It is either not installed, or its name is spelled differently.",
                    howToFix = "Check the spelling in the import on $at. If it is a third-party package, install it first" +
                            (module?.let { " (for example `pip install ${it.substringBefore('.')}`)" } ?: "") + ".",
                    example = "",
                    beginner = "An import is like borrowing a book from a library. Python can't find a book with that title on its shelves.",
                    tip = "Most import errors are typos or a package that isn't installed in the environment you are running."
                )
            }

            ErrorKind.ATTRIBUTE_ERROR -> {
                val m = Regex("""'([^']+)' object has no attribute '([^']+)'""", RegexOption.IGNORE_CASE)
                    .find(errorText)
                val type = m?.groupValues?.get(1)
                val attr = m?.groupValues?.get(2)

                PlainExplanation(
                    headline = (attr?.let { "`$it`" } ?: "That attribute") + " doesn't exist on this value ($at)",
                    why = "$At tries to use " + (attr?.let { "`.$it`" } ?: "an attribute") +
                            (type?.let { " on a value of type `$it`" } ?: "") +
                            ", but that type has nothing with that name. It is usually a typo, the wrong method name, " +
                            "or a value that is not what you expected (for example None).",
                    howToFix = "Check the spelling of " + (attr?.let { "`.$it`" } ?: "the attribute") +
                            " on $at, and print the value's type with `type(value)` to see what it really is.",
                    example = "",
                    beginner = "You asked an object to do something it doesn't know how to do — like asking a number to \"capitalize\" itself.",
                    tip = "Use dir(value) to list everything a value can do."
                )
            }

            ErrorKind.INDEX_ERROR -> PlainExplanation(
                headline = "List position doesn't exist ($at)",
                why = "$At asks for a position in a list that is not there — the list is shorter than the index being used.",
                howToFix = "Check the list's length with `len(...)` and make sure the index is smaller than it. " +
                        "Positions start at 0, so a list of 3 items has positions 0, 1 and 2.",
                example = "",
                beginner = "It's like asking for the 5th page of a 3-page booklet.",
                tip = "Loop with `for item in items:` instead of counting positions by hand."
            )

            ErrorKind.KEY_ERROR -> {
                val key = Regex("""KeyError:\s*(.+)""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)?.lineSequence()?.first()?.trim()

                PlainExplanation(
                    headline = "Dictionary has no key ${key ?: ""}".trim() + " ($at)",
                    why = "$At looks up a key" + (key?.let { " ($it)" } ?: "") +
                            " in a dictionary that does not contain it.",
                    howToFix = "Check the spelling of the key on $at, or use `.get(key)` so a missing key gives None instead of an error.",
                    example = "",
                    beginner = "A dictionary is like a phone book: you looked up a name that isn't in it.",
                    tip = "Use `key in my_dict` to check before you look something up."
                )
            }

            ErrorKind.ZERO_DIVISION -> PlainExplanation(
                headline = "Division by zero ($at)",
                why = "$At divides a number by 0, which is impossible, so Python stops.",
                howToFix = "Make sure the value after `/`, `//` or `%` on $at is never 0 — for example add `if value != 0:` before dividing.",
                example = "",
                beginner = "You can't split something into zero equal groups.",
                tip = "Guard every division whose divisor can come from user input or a calculation."
            )

            ErrorKind.TYPE_ERROR -> typeError(errorText, at, At)

            ErrorKind.VALUE_ERROR -> {
                val bad = Regex("""invalid literal for int\(\) with base 10: '([^']*)'""", RegexOption.IGNORE_CASE)
                    .find(errorText)?.groupValues?.get(1)

                PlainExplanation(
                    headline = "Wrong kind of value ($at)",
                    why = if (bad != null) {
                        "$At tries to turn `$bad` into a whole number with int(), but `$bad` is not a whole number."
                    } else {
                        "$At gives a function a value of the right type but with contents it cannot accept."
                    },
                    howToFix = if (bad != null) {
                        "Make sure the text contains only digits before calling int() — for example check `.isdigit()` first."
                    } else {
                        "Check what value reaches $at (print it) and compare it with what the function expects."
                    },
                    example = "",
                    beginner = "The function accepts this kind of thing, but not this particular one — like typing letters into a phone-number box.",
                    tip = "Validate input before converting it."
                )
            }

            ErrorKind.UNRESOLVED_REFERENCE,
            ErrorKind.OTHER -> null
        }
    }

    private fun typeError(errorText: String, at: String, At: String): PlainExplanation {
        val t = errorText.lowercase()

        return when {
            "can only concatenate" in t || "unsupported operand" in t -> {
                val pair = Regex("""'(\w+)' and '(\w+)'""").find(errorText)
                val a = pair?.groupValues?.get(1)
                val b = pair?.groupValues?.get(2)

                PlainExplanation(
                    headline = "Can't combine these two types ($at)",
                    why = "$At tries to combine " +
                            (if (a != null && b != null) "a `$a` and a `$b`" else "two values of different types") +
                            " with an operator such as '+'. Python does not mix text and numbers automatically.",
                    howToFix = "Convert one side so both are the same type — for example `str(number)` to join with text, or `int(text)` to add numbers.",
                    example = "",
                    beginner = "It's like adding \"5 apples\" and 3 — Python doesn't know whether you want text or a number.",
                    tip = "Use f-strings for messages: f\"Total: {total}\"."
                )
            }

            "positional argument" in t -> {
                val m = Regex("""takes (\d+) positional arguments? but (\d+) (?:was|were) given""")
                    .find(t)

                PlainExplanation(
                    headline = "Wrong number of arguments ($at)",
                    why = "$At calls a function with " + (m?.groupValues?.get(2)?.let { "$it" } ?: "a different number of") +
                            " values, but the function is defined to take " + (m?.groupValues?.get(1) ?: "another number of") + ".",
                    howToFix = "Compare the call on $at with the function's `def` line and pass exactly the parameters it lists. " +
                            "If it is a method inside a class, remember `self` is passed automatically.",
                    example = "",
                    beginner = "You handed the function more (or fewer) things than it has slots for.",
                    tip = "Read the def line first, then write the call to match it."
                )
            }

            "nonetype" in t -> PlainExplanation(
                headline = "A value is None ($at)",
                why = "$At uses a value as if it held something, but it is `None` (nothing). " +
                        "This often happens when a function forgets to `return` a result.",
                howToFix = "Find where that value is created and make sure it returns or stores something real. Print it just before $at to check.",
                example = "",
                beginner = "You opened a box expecting an item and found it empty.",
                tip = "Functions that should give back a result must end with `return value`."
            )

            else -> PlainExplanation(
                headline = "Wrong type of value ($at)",
                why = "$At uses a value in a way its type does not allow.",
                howToFix = "Print the value and its `type(...)` just before $at and compare with what the operation expects.",
                example = "",
                beginner = "Like trying to put a square peg in a round hole — the value has the wrong shape for this operation.",
                tip = "Check types with `type(value)` when something behaves oddly."
            )
        }
    }

    /**
     * Describes what is at the given 1-based column of the line, e.g.
     * "right after `)`" or "near `tax`".
     */
    private fun tokenNear(line: String, column: Int): String? {
        if (line.isEmpty()) return null

        val idx = column - 1

        if (idx >= line.length) {
            return "right after `${line.last()}` at the end of the line"
        }
        if (idx < 0) return null

        val ch = line[idx]
        if (ch.isLetterOrDigit() || ch == '_') {
            var s = idx
            var e = idx
            while (s > 0 && (line[s - 1].isLetterOrDigit() || line[s - 1] == '_')) s--
            while (e < line.length - 1 && (line[e + 1].isLetterOrDigit() || line[e + 1] == '_')) e++
            return "near `${line.substring(s, e + 1)}`"
        }
        return "near `$ch`"
    }
}
