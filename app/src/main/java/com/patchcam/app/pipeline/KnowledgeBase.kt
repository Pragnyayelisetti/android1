package com.patchcam.app.pipeline

import com.patchcam.app.models.ErrorKind

/**
 * One entry in the Knowledge tab.
 *
 * The same entries also power the offline chat ("what is a traceback?"),
 * so PatchCam explains a term the same way in both places.
 *
 * Topics with [challengeLines] can be practised: the user taps the line
 * that contains the bug and gets instant feedback.
 */
data class KnowledgeTopic(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val meaning: String = "",
    val cause: String = "",
    val broken: String = "",
    val fixed: String = "",
    val keywords: List<String> = emptyList(),
    val challengeLines: List<String> = emptyList(),
    val bugIndex: Int = -1,
    val bugWhy: String = "",
    val tip: String = ""
) {
    val hasChallenge: Boolean
        get() = challengeLines.isNotEmpty() && bugIndex in challengeLines.indices
}

object KnowledgeBase {

    const val CAT_SYNTAX = "Syntax"
    const val CAT_INDENT = "Indentation"
    const val CAT_NAMES = "Names & imports"
    const val CAT_RUNTIME = "Runtime"
    const val CAT_BASICS = "Basics"
    const val CAT_PATCHCAM = "How PatchCam works"

    val categories: List<String> = listOf(
        CAT_SYNTAX,
        CAT_INDENT,
        CAT_NAMES,
        CAT_RUNTIME,
        CAT_BASICS,
        CAT_PATCHCAM
    )

    val topics: List<KnowledgeTopic> = listOf(

        /* ---------------------------- SYNTAX ---------------------------- */

        KnowledgeTopic(
            id = "missing-colon",
            category = CAT_SYNTAX,
            title = "Missing ':'",
            summary = "A line that opens a block must end with ':'.",
            meaning = "Python marks the start of a block — a function, an if, a loop, a class — with a ':' at the end of that line. The indented lines below then belong to it.",
            cause = "Forgetting the ':' after def, if, elif, else, for, while, class, try, except or with.",
            broken = "def add(a, b)\n    return a + b",
            fixed = "def add(a, b):\n    return a + b",
            keywords = listOf("colon", "missing colon", "expected ':'", "def", "block header"),
            challengeLines = listOf("def add(a, b)", "    return a + b", "print(add(2, 3))"),
            bugIndex = 0,
            bugWhy = "Line 1 is a def line, so it has to end with ':'.",
            tip = "Type the ':' before you press Enter and your editor will indent the next line for you."
        ),

        KnowledgeTopic(
            id = "unclosed-bracket",
            category = CAT_SYNTAX,
            title = "Bracket never closed",
            summary = "Every ( [ { needs its own ) ] }.",
            meaning = "Brackets always come in pairs. If one is opened and never closed, Python keeps waiting for the closing one and then fails.",
            cause = "A missing ')' at the end of a function call, or a bracket left open in a long expression.",
            broken = "print(\"Total:\", total",
            fixed = "print(\"Total:\", total)",
            keywords = listOf("bracket", "parenthesis", "parentheses", "never closed", "unmatched", "eof", "unclosed"),
            challengeLines = listOf("price = 100", "total = price * 1.18", "print(\"Total:\", total"),
            bugIndex = 2,
            bugWhy = "Line 3 opens 'print(' but never closes it with ')'.",
            tip = "Type the closing bracket right after the opening one, then fill in the middle."
        ),

        KnowledgeTopic(
            id = "unterminated-string",
            category = CAT_SYNTAX,
            title = "Quote never closed",
            summary = "Text in quotes needs a closing quote.",
            meaning = "Text (a string) starts and ends with a quote mark. Without the closing quote Python thinks the text never ends.",
            cause = "Forgetting the closing quote, or using a different kind of quote to close it.",
            broken = "name = \"Asha",
            fixed = "name = \"Asha\"",
            keywords = listOf("string", "quote", "unterminated", "eol"),
            challengeLines = listOf("name = \"Asha", "print(name)"),
            bugIndex = 0,
            bugWhy = "Line 1 opens a quote before Asha but never closes it.",
            tip = "Type both quote marks first, then write the text between them."
        ),

        KnowledgeTopic(
            id = "print-parens",
            category = CAT_SYNTAX,
            title = "print needs brackets",
            summary = "In Python 3, print is written print(...).",
            meaning = "Old Python 2 code wrote `print \"hi\"`. Python 3 turned print into a function, and functions are called with brackets.",
            cause = "Copying old Python 2 examples.",
            broken = "print \"Hello\"",
            fixed = "print(\"Hello\")",
            keywords = listOf("print", "python 2", "python 3"),
            challengeLines = listOf("name = \"Asha\"", "print \"Hello\", name"),
            bugIndex = 1,
            bugWhy = "Line 2 uses print without brackets.",
            tip = "Always write print(...) with brackets."
        ),

        KnowledgeTopic(
            id = "equals-vs-double",
            category = CAT_SYNTAX,
            title = "'=' vs '=='",
            summary = "One '=' stores a value; two '==' compare.",
            meaning = "A single '=' means \"store this value\". Two '==' ask \"are these equal?\". An if or while condition asks a question, so it needs '=='.",
            cause = "Using '=' inside an if or while condition.",
            broken = "if score = 100:\n    print(\"Perfect\")",
            fixed = "if score == 100:\n    print(\"Perfect\")",
            keywords = listOf("equals", "==", "comparison", "assignment", "compare"),
            challengeLines = listOf("score = 100", "if score = 100:", "    print(\"Perfect\")"),
            bugIndex = 1,
            bugWhy = "Line 2 asks a question, so it needs '==' instead of '='.",
            tip = "Say it out loud: if score IS EQUAL TO 100 → =="
        ),

        KnowledgeTopic(
            id = "invalid-syntax",
            category = CAT_SYNTAX,
            title = "Invalid syntax",
            summary = "Python can't read the line — something is missing or extra.",
            meaning = "\"Invalid syntax\" means Python could not understand the structure of a line. The caret ^ shows where it gave up, but the real slip is often just before that spot.",
            cause = "A missing comma, colon, quote or bracket; or an extra symbol.",
            broken = "numbers = [1, 2 3]",
            fixed = "numbers = [1, 2, 3]",
            keywords = listOf("syntax error", "syntaxerror", "invalid syntax", "syntax"),
            challengeLines = listOf("numbers = [1, 2 3]", "print(numbers)"),
            bugIndex = 0,
            bugWhy = "Line 1 is missing a comma between 2 and 3.",
            tip = "Check the flagged spot AND the end of the line before it."
        ),

        /* -------------------------- INDENTATION ------------------------- */

        KnowledgeTopic(
            id = "expected-indent",
            category = CAT_INDENT,
            title = "Expected an indented block",
            summary = "The line after a ':' must be pushed in by 4 spaces.",
            meaning = "Python uses indentation, not curly brackets, to show what belongs inside a block. After a line ending in ':' the next line must be indented.",
            cause = "Forgetting to indent the body of a function, loop or if.",
            broken = "def greet():\nprint(\"Hi\")",
            fixed = "def greet():\n    print(\"Hi\")",
            keywords = listOf("indentation", "indent", "indented block", "expected an indented block"),
            challengeLines = listOf("def greet():", "print(\"Hi\")", "greet()"),
            bugIndex = 1,
            bugWhy = "Line 2 belongs inside greet(), so it must be indented by 4 spaces.",
            tip = "Let the editor indent for you — press Enter right after the ':'."
        ),

        KnowledgeTopic(
            id = "unexpected-indent",
            category = CAT_INDENT,
            title = "Unexpected indent",
            summary = "A line is pushed right without a block above it.",
            meaning = "Only the lines inside a block may move to the right. If the line above doesn't open a block, extra spaces make no sense to Python.",
            cause = "A stray space or tab at the start of a line.",
            broken = "x = 5\n    y = 6",
            fixed = "x = 5\ny = 6",
            keywords = listOf("unexpected indent", "extra space", "stray space"),
            challengeLines = listOf("x = 5", "    y = 6", "print(x + y)"),
            bugIndex = 1,
            bugWhy = "Line 2 is indented, but line 1 doesn't open a block.",
            tip = "Lines that belong together should start in the same column."
        ),

        KnowledgeTopic(
            id = "unindent-mismatch",
            category = CAT_INDENT,
            title = "Indentation doesn't match",
            summary = "A line's indentation matches no earlier block.",
            meaning = "Each block has one fixed indentation. A later line that uses a different amount, one that no block used, confuses Python.",
            cause = "Mixing 2, 4 or 8 spaces inside the same block.",
            broken = "if ok:\n        a = 1\n    b = 2",
            fixed = "if ok:\n    a = 1\n    b = 2",
            keywords = listOf("unindent", "does not match", "outer indentation"),
            challengeLines = listOf("if ok:", "        a = 1", "    b = 2"),
            bugIndex = 2,
            bugWhy = "Line 3 uses 4 spaces, but the block started with 8.",
            tip = "Use exactly 4 spaces per level everywhere."
        ),

        KnowledgeTopic(
            id = "tabs-spaces",
            category = CAT_INDENT,
            title = "Tabs mixed with spaces",
            summary = "A tab and four spaces look the same but aren't.",
            meaning = "Tabs and spaces both push text right, but they are different characters. Mixing them in one block makes Python raise a TabError.",
            cause = "Editors that insert a tab when you press Tab, combined with hand-typed spaces.",
            broken = "def f():\n[TAB]a = 1\n    b = 2",
            fixed = "def f():\n    a = 1\n    b = 2",
            keywords = listOf("tab", "taberror", "tabs", "spaces"),
            challengeLines = listOf("def f():", "[TAB]a = 1", "    b = 2"),
            bugIndex = 1,
            bugWhy = "Line 2 is indented with a tab while line 3 uses spaces.",
            tip = "Turn on \"insert spaces when pressing Tab\" in your editor."
        ),

        /* ------------------------ NAMES & IMPORTS ----------------------- */

        KnowledgeTopic(
            id = "name-error",
            category = CAT_NAMES,
            title = "Name is not defined",
            summary = "A variable is used before it exists.",
            meaning = "Python only knows a name after a line has created it. Using a name that was never created (or was spelled differently) raises a NameError.",
            cause = "A typo, using a variable before assigning it, or forgetting an import.",
            broken = "price = 100\nprint(total)",
            fixed = "price = 100\ntotal = price * 1.18\nprint(total)",
            keywords = listOf("nameerror", "not defined", "variable", "undefined"),
            challengeLines = listOf("price = 100", "tax = price * 0.18", "print(total)"),
            bugIndex = 2,
            bugWhy = "Line 3 uses total, but no line ever creates it.",
            tip = "Copy-paste names instead of retyping them."
        ),

        KnowledgeTopic(
            id = "module-not-found",
            category = CAT_NAMES,
            title = "Module not found",
            summary = "An import names something Python can't find.",
            meaning = "import brings in code written elsewhere. If Python can't find a module with that name, it raises ModuleNotFoundError.",
            cause = "A typo in the module name, or a package that isn't installed.",
            broken = "import numpyy",
            fixed = "import numpy",
            keywords = listOf("import", "module", "modulenotfounderror", "no module named", "pip"),
            challengeLines = listOf("import numpyy", "print(numpyy.pi)"),
            bugIndex = 0,
            bugWhy = "Line 1 misspells the module name.",
            tip = "If the spelling is right, the package probably isn't installed — try pip install."
        ),

        /* ------------------------------ RUNTIME ------------------------- */

        KnowledgeTopic(
            id = "attribute-error",
            category = CAT_RUNTIME,
            title = "No such attribute",
            summary = "A value doesn't have the method you asked for.",
            meaning = "Every value only knows a fixed set of actions. Asking for one it doesn't have raises an AttributeError.",
            cause = "A misspelled method, or a value that isn't the type you expected (often None).",
            broken = "name = \"asha\"\nprint(name.upper2())",
            fixed = "name = \"asha\"\nprint(name.upper())",
            keywords = listOf("attributeerror", "attribute", "no attribute", "method"),
            challengeLines = listOf("name = \"asha\"", "print(name.upper2())"),
            bugIndex = 1,
            bugWhy = "Line 2 calls upper2(), which strings don't have.",
            tip = "dir(value) lists everything a value can do."
        ),

        KnowledgeTopic(
            id = "index-error",
            category = CAT_RUNTIME,
            title = "List index out of range",
            summary = "You asked for a position the list doesn't have.",
            meaning = "List positions start at 0. A list with 3 items has positions 0, 1 and 2, so position 3 doesn't exist.",
            cause = "Off-by-one mistakes, or an empty list.",
            broken = "items = [10, 20, 30]\nprint(items[3])",
            fixed = "items = [10, 20, 30]\nprint(items[2])",
            keywords = listOf("indexerror", "index out of range", "list index", "out of range"),
            challengeLines = listOf("items = [10, 20, 30]", "print(items[3])"),
            bugIndex = 1,
            bugWhy = "Line 2 asks for position 3, but the last position is 2.",
            tip = "Loop with `for item in items:` instead of counting positions."
        ),

        KnowledgeTopic(
            id = "key-error",
            category = CAT_RUNTIME,
            title = "Missing dictionary key",
            summary = "The key you looked up isn't in the dictionary.",
            meaning = "A dictionary is like a phone book. Looking up a name that isn't there raises a KeyError.",
            cause = "A misspelled key, or a key that was never added.",
            broken = "ages = {\"asha\": 20}\nprint(ages[\"ravi\"])",
            fixed = "ages = {\"asha\": 20}\nprint(ages.get(\"ravi\"))",
            keywords = listOf("keyerror", "dictionary", "dict", "key"),
            challengeLines = listOf("ages = {\"asha\": 20}", "print(ages[\"ravi\"])"),
            bugIndex = 1,
            bugWhy = "Line 2 looks up \"ravi\", which was never added.",
            tip = "`.get(key)` returns None instead of failing."
        ),

        KnowledgeTopic(
            id = "zero-division",
            category = CAT_RUNTIME,
            title = "Division by zero",
            summary = "A number was divided by 0.",
            meaning = "Dividing by zero is impossible, so Python stops with a ZeroDivisionError.",
            cause = "A divisor that is 0 because of input or an earlier calculation.",
            broken = "count = 0\nprint(100 / count)",
            fixed = "count = 0\nif count != 0:\n    print(100 / count)",
            keywords = listOf("zerodivisionerror", "division by zero", "divide by zero", "divide"),
            challengeLines = listOf("count = 0", "print(100 / count)"),
            bugIndex = 1,
            bugWhy = "Line 2 divides by count, which is 0.",
            tip = "Check the divisor before every division that could be 0."
        ),

        KnowledgeTopic(
            id = "type-error",
            category = CAT_RUNTIME,
            title = "Wrong type",
            summary = "Text and numbers can't be mixed with '+'.",
            meaning = "Python does not guess how to combine text and numbers. Convert one so both are the same type.",
            cause = "Joining a string and a number, or passing the wrong number of arguments.",
            broken = "age = 20\nprint(\"Age: \" + age)",
            fixed = "age = 20\nprint(\"Age: \" + str(age))",
            keywords = listOf("typeerror", "type error", "concatenate", "unsupported operand", "nonetype"),
            challengeLines = listOf("age = 20", "print(\"Age: \" + age)"),
            bugIndex = 1,
            bugWhy = "Line 2 adds text and a number; age needs str(age).",
            tip = "f-strings avoid this: f\"Age: {age}\"."
        ),

        KnowledgeTopic(
            id = "value-error",
            category = CAT_RUNTIME,
            title = "Bad value",
            summary = "The type is right but the contents aren't.",
            meaning = "A function accepted the type of the value but can't work with what is inside it — like int(\"abc\").",
            cause = "Converting text that isn't a number.",
            broken = "n = int(\"abc\")",
            fixed = "n = int(\"42\")",
            keywords = listOf("valueerror", "invalid literal", "int()"),
            challengeLines = listOf("text = \"abc\"", "n = int(text)"),
            bugIndex = 1,
            bugWhy = "Line 2 tries to turn \"abc\" into a whole number.",
            tip = "Check `text.isdigit()` before converting."
        ),

        /* ------------------------------ BASICS -------------------------- */

        KnowledgeTopic(
            id = "traceback",
            category = CAT_BASICS,
            title = "Reading a traceback",
            summary = "Read it from the bottom up.",
            meaning = "A traceback is the report Python prints when something fails.\n1. Last line — the kind of error and what went wrong.\n2. The line above it — the code that failed.\n3. \"line N\" — where that code is in your file.",
            keywords = listOf("traceback", "stack trace", "stacktrace", "error report"),
            tip = "Don't read top to bottom — the answer is at the bottom."
        ),

        KnowledgeTopic(
            id = "syntax-vs-runtime",
            category = CAT_BASICS,
            title = "Syntax vs runtime errors",
            summary = "Some errors stop the program before it starts, some during.",
            meaning = "A syntax error means Python can't even read the code, so nothing runs. A runtime error happens while the program is already running — the code was readable but did something impossible.",
            keywords = listOf("runtime", "syntax vs runtime", "difference between"),
            tip = "PatchCam can check syntax on your phone; runtime errors need the program to actually run."
        ),

        /* -------------------------- HOW PATCHCAM WORKS ------------------ */

        KnowledgeTopic(
            id = "pc-observe",
            category = CAT_PATCHCAM,
            title = "Observe automatically",
            summary = "OCR reads the visible source and error instead of asking you to type it.",
            meaning = "OCR reads the visible source and error instead of asking you to type it.",
            keywords = listOf("ocr", "observe", "camera", "scan")
        ),

        KnowledgeTopic(
            id = "pc-reconstruct",
            category = CAT_PATCHCAM,
            title = "Reconstruct structure",
            summary = "Bounding-box geometry is used to recover line order and indentation.",
            meaning = "Bounding-box geometry is used to recover line order and indentation.",
            keywords = listOf("reconstruct", "reconstruction", "geometry")
        ),

        KnowledgeTopic(
            id = "pc-root-cause",
            category = CAT_PATCHCAM,
            title = "Find the root cause",
            summary = "Known fixes are checked before an optional on-device model fallback.",
            meaning = "Known fixes are checked before an optional on-device model fallback.",
            keywords = listOf("root cause", "known fix", "fix table")
        ),

        KnowledgeTopic(
            id = "pc-overclaim",
            category = CAT_PATCHCAM,
            title = "Don't overclaim",
            summary = "Suggestions remain explicitly unverified when stronger validation is unavailable.",
            meaning = "Suggestions remain explicitly unverified when stronger validation is unavailable.",
            keywords = listOf("overclaim", "unverified", "confidence", "reliability")
        ),

        KnowledgeTopic(
            id = "pc-sessions",
            category = CAT_PATCHCAM,
            title = "Long errors are sessions",
            summary = "Multiple camera frames can be captured and overlapping lines are removed automatically.",
            meaning = "Multiple camera frames can be captured and overlapping lines are removed automatically.",
            keywords = listOf("frames", "session", "long error", "overlap")
        ),

        KnowledgeTopic(
            id = "pc-verified",
            category = CAT_PATCHCAM,
            title = "What \"verified\" means",
            summary = "Python's own parser accepted the fixed code on your phone.",
            meaning = "A patch is VERIFIED when Python's parser (running on your phone) accepts the code after the patch is applied. That proves the syntax is valid — it does not prove the program does what you want.",
            keywords = listOf("verified", "verification", "validate", "validation", "parser", "ast"),
            tip = "Verified = the syntax is valid. Always run your program afterwards."
        ),

        KnowledgeTopic(
            id = "pc-feedback",
            category = CAT_PATCHCAM,
            title = "How your feedback teaches PatchCam",
            summary = "Tell PatchCam if a fix worked — it adjusts its confidence for that kind of fix.",
            meaning = "On the Flow tab you can say whether a fix worked. PatchCam remembers this on your phone: fixes that worked for you get more confidence next time, and fixes that failed get less. You can also correct a misread line and re-analyze.",
            keywords = listOf("feedback", "learn", "learning", "flow")
        )
    )

    fun byId(id: String): KnowledgeTopic? = topics.firstOrNull { it.id == id }

    /** The Knowledge topic that explains the given kind of error. */
    fun topicForKind(kind: ErrorKind): KnowledgeTopic? {
        val id = when (kind) {
            ErrorKind.MISSING_COLON -> "missing-colon"
            ErrorKind.UNCLOSED_BRACKET -> "unclosed-bracket"
            ErrorKind.UNTERMINATED_STRING -> "unterminated-string"
            ErrorKind.PRINT_PARENS -> "print-parens"
            ErrorKind.SINGLE_EQUALS -> "equals-vs-double"
            ErrorKind.INVALID_SYNTAX -> "invalid-syntax"
            ErrorKind.EXPECTED_INDENT -> "expected-indent"
            ErrorKind.UNEXPECTED_INDENT -> "unexpected-indent"
            ErrorKind.UNINDENT_MISMATCH -> "unindent-mismatch"
            ErrorKind.TAB_ERROR -> "tabs-spaces"
            ErrorKind.NAME_ERROR -> "name-error"
            ErrorKind.MODULE_NOT_FOUND -> "module-not-found"
            ErrorKind.ATTRIBUTE_ERROR -> "attribute-error"
            ErrorKind.INDEX_ERROR -> "index-error"
            ErrorKind.KEY_ERROR -> "key-error"
            ErrorKind.ZERO_DIVISION -> "zero-division"
            ErrorKind.TYPE_ERROR -> "type-error"
            ErrorKind.VALUE_ERROR -> "value-error"
            ErrorKind.UNRESOLVED_REFERENCE,
            ErrorKind.OTHER -> null
        }
        return id?.let { byId(it) }
    }

    /** Filters by category and by every word typed in the search box. */
    fun search(query: String, category: String?): List<KnowledgeTopic> {
        val words = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }

        return topics.filter { topic ->
            (category == null || topic.category == category) &&
                    words.all { word ->
                        topic.title.lowercase().contains(word) ||
                                topic.summary.lowercase().contains(word) ||
                                topic.meaning.lowercase().contains(word) ||
                                topic.keywords.any { it.lowercase().contains(word) }
                    }
        }
    }

    /**
     * Best topic for a free-text question such as "what is a traceback?".
     * Returns null unless a topic keyword or title actually appears in it.
     */
    fun findForQuestion(question: String): KnowledgeTopic? {
        val q = question.lowercase()

        var best: KnowledgeTopic? = null
        var bestScore = 0

        for (topic in topics) {
            var score = 0

            for (keyword in topic.keywords) {
                val k = keyword.lowercase()
                if (k.length >= 3 && q.contains(k)) score += k.length
            }

            val title = topic.title.lowercase()
            if (title.length >= 4 && q.contains(title)) score += title.length

            if (score > bestScore) {
                bestScore = score
                best = topic
            }
        }

        return best
    }
}
