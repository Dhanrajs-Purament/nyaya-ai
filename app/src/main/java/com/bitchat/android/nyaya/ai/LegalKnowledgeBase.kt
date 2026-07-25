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
 * The bundled acts are written in English, but the app promises answers in
 * Hindi and Hinglish too. Questions asked in Devanagari or romanised Hindi
 * are bridged onto the English index at query time — see [expandQueryTerms]
 * and [QUERY_BRIDGE]. Without that bridge a Hindi question tokenised to
 * nothing, retrieval returned nothing, and the model answered ungrounded,
 * which is exactly the hallucination path retrieval exists to close.
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
        val queryTerms = expandQueryTerms(tokenize(query))
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

    /**
     * Bridges Hindi legal vocabulary in the query — Devanagari and common
     * romanisations — onto the statutory English terms the index is built
     * from. Expansion is query-time only: the passage index never changes,
     * an English query is returned untouched (same object, zero cost), and a
     * bridged term keeps the original token too, which is harmless because a
     * Devanagari term simply matches nothing in an English index.
     *
     * This is a curated map, not a transliterator or dictionary, on purpose:
     * every entry is auditable against the act it should reach, and a wrong
     * mapping here would be a legal-accuracy bug rather than a typo.
     */
    private fun expandQueryTerms(tokens: Map<String, Int>): Map<String, Int> {
        var expanded: HashMap<String, Int>? = null
        for ((term, count) in tokens) {
            val bridged = QUERY_BRIDGE[term] ?: continue
            val target = expanded ?: HashMap(tokens).also { expanded = it }
            for (english in bridged) {
                target[english] = (target[english] ?: 0) + count
            }
        }
        return expanded ?: tokens
    }

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

        /**
         * Tokens are runs of Latin letters, digits, or Devanagari
         * (\u0900-\u097F, which covers matras and nukta forms). Devanagari
         * survives [String.lowercase] unchanged, so Hindi words arrive here
         * intact instead of being silently dropped.
         */
        private val TOKEN_REGEX = Regex("[a-z0-9\u0900-\u097F]+")

        /**
         * Hindi (Devanagari and romanised) legal vocabulary mapped to the
         * English terms used by the bundled acts. Keys must be lowercase, as
         * produced by [tokenize]. Values must not be index stopwords (which is
         * why वसीयत maps to "succession" and not "will").
         */
        private val QUERY_BRIDGE: Map<String, List<String>> = mapOf(
            // Police, arrest, FIR
            "पुलिस" to listOf("police"),
            "थाना" to listOf("police", "station"),
            "thana" to listOf("police", "station"),
            "गिरफ्तार" to listOf("arrest"),
            "गिरफ्तारी" to listOf("arrest"),
            "giraftar" to listOf("arrest"),
            "giraftari" to listOf("arrest"),
            "एफआईआर" to listOf("fir", "information", "report"),
            "प्राथमिकी" to listOf("fir", "information", "report"),
            "दर्ज" to listOf("register", "file"),
            "darj" to listOf("register", "file"),
            "शिकायत" to listOf("complaint"),
            "shikayat" to listOf("complaint"),
            "हिरासत" to listOf("custody", "detention"),
            "hirasat" to listOf("custody", "detention"),
            "जमानत" to listOf("bail"),
            "zamanat" to listOf("bail"),
            "jamanat" to listOf("bail"),
            // Courts, lawyers, process
            "अदालत" to listOf("court"),
            "adalat" to listOf("court"),
            "न्यायालय" to listOf("court"),
            "वकील" to listOf("lawyer", "advocate", "legal", "aid"),
            "vakil" to listOf("lawyer", "advocate", "legal", "aid"),
            "कानून" to listOf("law"),
            "kanoon" to listOf("law"),
            "kanun" to listOf("law"),
            "अधिकार" to listOf("right", "rights"),
            "adhikar" to listOf("right", "rights"),
            "धारा" to listOf("section"),
            "dhara" to listOf("section"),
            "सजा" to listOf("punishment", "imprisonment"),
            "saza" to listOf("punishment", "imprisonment"),
            "जुर्माना" to listOf("fine", "penalty"),
            "jurmana" to listOf("fine", "penalty"),
            "मुआवजा" to listOf("compensation"),
            "muavza" to listOf("compensation"),
            "नोटिस" to listOf("notice"),
            "गवाह" to listOf("witness"),
            "gawah" to listOf("witness"),
            // Offences
            "चोरी" to listOf("theft"),
            "chori" to listOf("theft"),
            "धोखाधड़ी" to listOf("cheating", "fraud"),
            "धोखा" to listOf("cheating", "fraud"),
            "dhokha" to listOf("cheating", "fraud"),
            "मारपीट" to listOf("assault", "hurt", "cruelty"),
            "marpit" to listOf("assault", "hurt", "cruelty"),
            "हत्या" to listOf("murder"),
            "hatya" to listOf("murder"),
            "बलात्कार" to listOf("rape"),
            "balatkar" to listOf("rape"),
            "अपहरण" to listOf("kidnapping", "abduction"),
            "apharan" to listOf("kidnapping", "abduction"),
            "रिश्वत" to listOf("bribe", "bribery", "corruption"),
            "rishwat" to listOf("bribe", "bribery", "corruption"),
            "उत्पीड़न" to listOf("harassment"),
            "utpidan" to listOf("harassment"),
            "छेड़छाड़" to listOf("harassment", "molestation", "assault"),
            // Family
            "तलाक" to listOf("divorce", "marriage"),
            "talaq" to listOf("divorce", "marriage"),
            "talak" to listOf("divorce", "marriage"),
            "शादी" to listOf("marriage"),
            "shaadi" to listOf("marriage"),
            "shadi" to listOf("marriage"),
            "विवाह" to listOf("marriage"),
            "पति" to listOf("husband"),
            "pati" to listOf("husband"),
            "पत्नी" to listOf("wife"),
            "patni" to listOf("wife"),
            "दहेज" to listOf("dowry"),
            "dahej" to listOf("dowry"),
            "घरेलू" to listOf("domestic"),
            "gharelu" to listOf("domestic"),
            "हिंसा" to listOf("violence"),
            "hinsa" to listOf("violence"),
            "गुजारा" to listOf("maintenance"),
            "guzara" to listOf("maintenance"),
            "भरण" to listOf("maintenance"),
            "वसीयत" to listOf("succession", "testament"),
            "vasiyat" to listOf("succession", "testament"),
            "उत्तराधिकार" to listOf("succession", "inheritance"),
            "विरासत" to listOf("succession", "inheritance", "property"),
            "virasat" to listOf("succession", "inheritance", "property"),
            // Property, money, consumer
            "संपत्ति" to listOf("property"),
            "sampatti" to listOf("property"),
            "जमीन" to listOf("land", "property"),
            "zameen" to listOf("land", "property"),
            "jameen" to listOf("land", "property"),
            "मकान" to listOf("house", "property"),
            "makan" to listOf("house", "property"),
            "किराया" to listOf("rent", "lease"),
            "kiraya" to listOf("rent", "lease"),
            "किरायेदार" to listOf("tenant", "lease"),
            "मालिक" to listOf("owner", "landlord"),
            "malik" to listOf("owner", "landlord"),
            "कर्ज" to listOf("debt", "loan"),
            "karz" to listOf("debt", "loan"),
            "उपभोक्ता" to listOf("consumer"),
            "upbhokta" to listOf("consumer"),
            "रिफंड" to listOf("refund"),
            "वारंटी" to listOf("warranty"),
            // Tax, company, information
            "आयकर" to listOf("income", "tax"),
            "aaykar" to listOf("income", "tax"),
            "टैक्स" to listOf("tax"),
            "जीएसटी" to listOf("gst", "goods", "services", "tax"),
            "कंपनी" to listOf("company"),
            "दिवालिया" to listOf("insolvency", "bankruptcy"),
            "diwaliya" to listOf("insolvency", "bankruptcy"),
            "सूचना" to listOf("information"),
            "आरटीआई" to listOf("rti", "information"),
            // Accidents and vehicles
            "दुर्घटना" to listOf("accident"),
            "durghatna" to listOf("accident"),
            "लाइसेंस" to listOf("licence", "license"),
            "गाड़ी" to listOf("vehicle", "motor"),
            "gaadi" to listOf("vehicle", "motor")
        )

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
