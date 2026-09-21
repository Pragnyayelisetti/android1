package com.patchcam.app.pipeline

import kotlin.math.ln

/**
 * On-device retrieval (the "R" of a local RAG).
 *
 * The corpus is PatchCam's own knowledge: every Knowledge topic (with its
 * broken -> fixed example) and every built-in fix rule. Nothing leaves the
 * phone and no embedding model is needed — it is a BM25 index built in
 * memory the first time it is used.
 *
 * Two consumers:
 *  - the on-device LLM prompts (patch generation and chat) receive the
 *    best-matching solved cases as grounding, so a small model like
 *    Gemma 2B works from real examples instead of guessing;
 *  - the offline chat uses the top hit as an answer when the model is not
 *    installed, and shows where it came from.
 */
data class RetrievedCase(
    val id: String,
    val title: String,
    /** "Knowledge" or "Fix rule" — shown to the user as the source. */
    val source: String,
    val text: String,
    val broken: String,
    val fixed: String,
    val score: Double
) {
    val citation: String get() = "$source › $title"
}

object LocalRetriever {

    private class Doc(
        val id: String,
        val title: String,
        val source: String,
        val text: String,
        val broken: String,
        val fixed: String,
        val tokens: List<String>
    )

    private const val K1 = 1.5
    private const val B = 0.75

    /** Below this a hit is treated as "nothing relevant found". */
    const val MIN_SCORE = 5.0

    private val docs: List<Doc> by lazy { buildCorpus() }

    private val avgLength: Double by lazy {
        if (docs.isEmpty()) 1.0 else docs.sumOf { it.tokens.size }.toDouble() / docs.size
    }

    private val docFrequency: Map<String, Int> by lazy {
        val df = HashMap<String, Int>()
        for (doc in docs) {
            for (t in doc.tokens.toSet()) df[t] = (df[t] ?: 0) + 1
        }
        df
    }

    fun retrieve(query: String, k: Int = 3): List<RetrievedCase> {
        val q = tokenize(query)
        if (q.isEmpty()) return emptyList()

        val n = docs.size.toDouble()

        return docs
            .map { doc ->
                var score = 0.0
                val tf = HashMap<String, Int>()
                for (t in doc.tokens) tf[t] = (tf[t] ?: 0) + 1

                for (term in q.toSet()) {
                    val f = tf[term] ?: continue
                    val df = (docFrequency[term] ?: 0).toDouble()
                    val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5))
                    val norm = f * (K1 + 1) / (f + K1 * (1 - B + B * doc.tokens.size / avgLength))
                    score += idf * norm
                }

                RetrievedCase(doc.id, doc.title, doc.source, doc.text, doc.broken, doc.fixed, score)
            }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .let { ranked ->
                // Drop weak tail hits: they only add noise to a small model's prompt.
                val top = ranked.firstOrNull()?.score ?: 0.0
                ranked.filter { it.score >= top * 0.5 }
            }
            .take(k)
    }

    /**
     * A prompt block of similar solved cases. Empty when nothing relevant
     * is found, so the prompt stays short.
     */
    fun contextFor(errorText: String, code: String, k: Int = 2): String {
        val cases = retrieve(errorText + " " + code.lines().take(12).joinToString(" "), k)
        if (cases.isEmpty()) return ""

        return buildString {
            append("SIMILAR SOLVED CASES (from PatchCam's knowledge base):\n")
            cases.forEachIndexed { i, c ->
                append("${i + 1}) ${c.title}: ${c.text.take(220)}\n")
                if (c.broken.isNotBlank() && c.fixed.isNotBlank()) {
                    append("   Broken: ${c.broken.replace("\n", " ⏎ ")}\n")
                    append("   Fixed:  ${c.fixed.replace("\n", " ⏎ ")}\n")
                }
            }
        }
    }

    /* ---------------------------------------------------------------------- */

    private fun buildCorpus(): List<Doc> {
        val list = ArrayList<Doc>()

        for (t in KnowledgeBase.topics) {
            // Title and keywords are repeated so they weigh more than body text.
            val body = listOf(
                t.title, t.title, t.keywords.joinToString(" "), t.keywords.joinToString(" "),
                t.summary, t.meaning, t.cause, t.broken, t.fixed
            ).joinToString(" ")

            list += Doc(
                id = "topic:${t.id}",
                title = t.title,
                source = "Knowledge",
                text = (t.meaning.ifBlank { t.summary }),
                broken = t.broken,
                fixed = t.fixed,
                tokens = tokenize(body)
            )
        }

        for (f in KnownFixTable.fixes) {
            val body = "${f.errorType} ${f.errorType} ${f.fixDescription} ${f.fixDescription} ${f.id.replace('_', ' ')}"
            list += Doc(
                id = "rule:${f.id}",
                title = f.fixDescription,
                source = "Fix rule",
                text = "${f.errorType}: ${f.fixDescription}",
                broken = "",
                fixed = "",
                tokens = tokenize(body)
            )
        }

        return list
    }

    private val stop = setOf(
        "the", "a", "an", "is", "are", "was", "to", "of", "in", "on", "it", "this", "that",
        "and", "or", "for", "with", "at", "by", "be", "as", "do", "does", "did", "how", "what",
        "why", "my", "me", "i", "you", "can", "line", "file", "main"
    )

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            // Punctuation carries meaning in error messages: expected ':' means "colon".
            .replace("':'", " colon ")
            .replace("'('", " parenthesis ")
            .replace("'=='", " double equals ")
            .split(Regex("""[^a-z0-9_]+"""))
            .filter { it.length >= 2 && it !in stop }
}
