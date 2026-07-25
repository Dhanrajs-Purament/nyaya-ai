package com.bitchat.android.nyaya.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln

/**
 * Offline legal knowledge base (lightweight RAG).
 *
 * Loads every markdown file bundled under assets/nyaya_kb/ when the app
 * starts ("pre-warming"), splits it into heading-aligned passages, and builds
 * an in-memory keyword index. For every user question, [retrieve] returns the
 * most relevant passages, which are injected into the model's context so the
 * AI answers from the real text of Indian bare acts instead of guessing.
 *
 * Pure Kotlin, no network, no native dependencies — works fully offline.
 * Add more knowledge by dropping additional .md files into assets/nyaya_kb/
 * (see tools/kb/fetch_full_kb.py to bundle the complete bare acts).
 */
class LegalKnowledgeBase(private val context: Context) {

    data class Passage(
        val source: String,
        val heading: String,
        val text: String,
        internal val termFreq: Map<String, Int>,
        internal val tokenCount: Int
    )

    @Volatile
    var isWarm: Boolean = false
        private set

    @Volatile
    private var passages: List<Passage> = emptyList()

    @Volatile
    private var docFreq: Map<String, Int> = emptyMap()

    /** Mean passage length in tokens, used for BM25 length normalisation. */
    @Volatile
    private var averageTokens: Double = 1.0

    /** Loads and indexes all bundled knowledge. Safe to call multiple times. */
    suspend fun warmUp() {
        if (isWarm) return
        withContext(Dispatchers.Default) {
            val names = try {
                context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".md") }
            } catch (e: Exception) {
                emptyList<String>()
            }
            val all = mutableListOf<Passage>()
            for (name in names.sorted()) {
                try {
                    val raw = context.assets.open("$ASSET_DIR/$name")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    all += splitIntoPassages(name, raw)
                } catch (_: Exception) {
                    // Never block app start on a bad knowledge file.
                }
            }
            val df = HashMap<String, Int>()
            for (p in all) for (t in p.termFreq.keys) df[t] = (df[t] ?: 0) + 1
            passages = all
            docFreq = df
            averageTokens = if (all.isEmpty()) 1.0 else {
                all.sumOf { it.tokenCount.toDouble() } / all.size
            }
            isWarm = true
        }
    }

    fun passageCount(): Int = passages.size

    /**
     * Returns the most relevant passages for [query], bounded by [maxChars].
     *
     * Ranking is Okapi BM25. Plain TF-IDF was not enough once the full
     * Income-tax Acts were bundled: those two files alone are roughly 4.5 MB of
     * the library, so they contribute a large share of all passages, and
     * everyday questions about consumer complaints or online harassment started
     * surfacing tax and procedure text instead of the right act. BM25's
     * length normalisation (the `b` term) and saturating term frequency (the
     * `k1` term) stop a handful of very large statutes from dominating, and let
     * the short curated summaries — which are the highest-signal files in the
     * library — win when they are genuinely the best answer.
     */
    fun retrieve(query: String, maxPassages: Int = 4, maxChars: Int = 3000): List<Passage> {
        val snapshot = passages
        if (snapshot.isEmpty()) return emptyList()
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()
        val n = snapshot.size.toDouble()
        val avgLen = averageTokens.coerceAtLeast(1.0)
        val scored = snapshot.mapNotNull { p ->
            var score = 0.0
            val lengthNorm = K1 * (1.0 - B + B * (p.tokenCount / avgLen))
            for ((term, qtf) in queryTerms) {
                val tf = p.termFreq[term] ?: continue
                val df = (docFreq[term] ?: 1).toDouble()
                // BM25 IDF, floored at zero so terms present in almost every
                // passage cannot push a score negative.
                val idf = ln(1.0 + (n - df + 0.5) / (df + 0.5)).coerceAtLeast(0.0)
                score += qtf * idf * (tf * (K1 + 1.0)) / (tf + lengthNorm)
                if (p.heading.lowercase(Locale.ROOT).contains(term)) {
                    score += idf * HEADING_BOOST
                }
            }
            if (score <= 0.0) null else p to score
        }.sortedByDescending { it.second }
        val out = mutableListOf<Passage>()
        var budget = maxChars
        for ((p, _) in scored) {
            if (out.size >= maxPassages) break
            if (p.text.length > budget && out.isNotEmpty()) continue
            out += p
            budget -= p.text.length
            if (budget <= 0) break
        }
        return out
    }

    /** Formats retrieved passages as a reference block for the system prompt. */
    fun asReferenceBlock(found: List<Passage>): String =
        found.joinToString("\n---\n") { "[" + it.source + " \u2014 " + it.heading + "]\n" + it.text }

    private fun splitIntoPassages(fileName: String, raw: String): List<Passage> {
        val sourceTitle = raw.lineSequence()
            .firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: fileName.removeSuffix(".md")
        val result = mutableListOf<Passage>()
        var heading = sourceTitle
        val current = StringBuilder()
        fun flush() {
            val text = current.toString().trim()
            if (text.length >= MIN_PASSAGE_CHARS) {
                val freq = tokenize(heading + " " + text)
                result += Passage(sourceTitle, heading, text, freq, freq.values.sum())
            }
            current.setLength(0)
        }
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            val isHeading = trimmed.startsWith("#")
            if (isHeading || current.length >= MAX_PASSAGE_CHARS) {
                flush()
                if (isHeading) {
                    heading = trimmed.trimStart('#').trim()
                    continue
                }
            }
            current.appendLine(line)
        }
        flush()
        return result
    }

    private fun tokenize(text: String): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (m in TOKEN_REGEX.findAll(text.lowercase(Locale.ROOT))) {
            val t = m.value
            if (t.length < 2 && !t[0].isDigit()) continue
            if (t in STOPWORDS) continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        return counts
    }

    companion object {
        private const val ASSET_DIR = "nyaya_kb"
        private const val MAX_PASSAGE_CHARS = 1600
        private const val MIN_PASSAGE_CHARS = 40

        /** BM25 term-frequency saturation. */
        private const val K1 = 1.2

        /** BM25 length normalisation strength (0 = off, 1 = full). */
        private const val B = 0.75

        /** Extra weight when a query term appears in the passage's heading. */
        private const val HEADING_BOOST = 0.9
        private val TOKEN_REGEX = Regex("[a-z0-9]+")
        private val STOPWORDS = setOf(
            "the", "a", "an", "of", "and", "or", "to", "in", "on", "for", "by",
            "is", "are", "was", "be", "with", "as", "at", "it", "its", "this",
            "that", "any", "not", "no", "if", "than", "then", "so", "such",
            "can", "may", "shall", "will", "what", "when", "how", "who", "i",
            "my", "me", "you", "your", "he", "she", "his", "her", "they",
            "their", "have", "has", "had", "do", "does", "did", "about",
            "under", "from", "into", "against"
        )
    }
}
