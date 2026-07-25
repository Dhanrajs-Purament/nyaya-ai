package com.bitchat.android.nyaya

import androidx.test.core.app.ApplicationProvider
import com.bitchat.android.nyaya.ai.LawyerSystemPrompt
import com.bitchat.android.nyaya.ai.LegalKnowledgeBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the offline legal RAG pipeline against the REAL knowledge base
 * bundled in src/main/assets/nyaya_kb (populated by tools/kb/fetch_full_kb.py).
 *
 * These tests are the offline-mode proof: nothing here touches the network,
 * the LLM, or a device. They verify that the app can take a citizen's
 * question and ground it in the actual text of Indian bare acts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegalKnowledgeBaseTest {

    companion object {
        /**
         * Cached across the test methods of this class: Robolectric reuses one
         * sandbox classloader per class, and indexing the whole bundled library
         * on every test would be wasteful. It cannot be done in @BeforeClass,
         * because the Android environment is only registered per test method.
         */
        private var cached: LegalKnowledgeBase? = null
    }

    private lateinit var kb: LegalKnowledgeBase

    @Before
    fun warmUpOnce() {
        kb = cached ?: LegalKnowledgeBase(ApplicationProvider.getApplicationContext())
            .also { runBlocking { it.warmUp() }; cached = it }
    }

    @Test
    fun warmUp_indexesBundledKnowledgeBase() {
        assertTrue("knowledge base should report itself warm", kb.isWarm)
        assertTrue(
            "expected a substantial passage index, got ${kb.passageCount()}",
            kb.passageCount() > 500
        )
    }

    @Test
    fun warmUp_isIdempotent() {
        val before = kb.passageCount()
        runBlocking { kb.warmUp() }
        assertEquals(before, kb.passageCount())
    }

    /**
     * The critical offline scenario: a user whose FIR is being refused.
     * The answer must be grounded in BNSS police procedure, not invented.
     */
    @Test
    fun firRefusal_retrievesBnssPoliceProcedure() {
        val found = kb.retrieve("Police refuse to file my FIR, what are my rights?")
        assertTrue("retrieval returned nothing", found.isNotEmpty())

        val block = kb.asReferenceBlock(found)
        assertTrue(
            "expected BNSS / police-procedure grounding, got sources=" +
                found.map { it.source },
            found.any {
                it.source.contains("Nagarik Suraksha", ignoreCase = true) ||
                    it.heading.contains("FIR", ignoreCase = true) ||
                    it.text.contains("First Information Report", ignoreCase = true)
            }
        )
        assertTrue(
            "grounding block should carry the Section 173 FIR remedy; block was:\n$block",
            block.contains("173")
        )
        assertTrue(
            "grounding block should explain the refusal remedy (SP / Magistrate)",
            block.contains("Superintendent of Police", ignoreCase = true) ||
                block.contains("Magistrate", ignoreCase = true)
        )
    }

    @Test
    fun zeroFir_isDiscoverable() {
        val block = kb.asReferenceBlock(kb.retrieve("Can I file a Zero FIR at any police station?"))
        assertTrue("Zero FIR guidance not retrieved:\n$block", block.contains("Zero FIR", ignoreCase = true))
    }

    @Test
    fun arrestRights_retrieveTwentyFourHourAndWomenSafeguards() {
        val block = kb.asReferenceBlock(
            kb.retrieve("Police arrested my brother at night. What are his rights?")
        )
        assertTrue(
            "expected arrest safeguards in retrieved text:\n$block",
            block.contains("24 hours") || block.contains("Magistrate", ignoreCase = true)
        )
    }

    @Test
    fun freeLegalAid_helplineIsRetrievable() {
        val block = kb.asReferenceBlock(
            kb.retrieve("I cannot afford a lawyer, how do I get free legal aid?")
        )
        assertTrue(
            "NALSA helpline 15100 should be reachable through retrieval:\n$block",
            block.contains("15100")
        )
    }

    /** Each domain in the library must actually be reachable by a plain question. */
    @Test
    fun libraryBreadth_eachDomainIsReachable() {
        val expectations = mapOf(
            "My husband beats me, what protection can I get?" to Regex("domestic violence", RegexOption.IGNORE_CASE),
            "The shop sold me a defective phone and refuses a refund" to Regex("consumer", RegexOption.IGNORE_CASE),
            "How do I file for divorce under Hindu law?" to Regex("hindu marriage|divorce", RegexOption.IGNORE_CASE),
            "How do I file an RTI application?" to Regex("information", RegexOption.IGNORE_CASE),
            "Someone is harassing me online and leaked my photos" to Regex("cyber|online harassment|information technology|data|computer", RegexOption.IGNORE_CASE)
        )
        val failures = mutableListOf<String>()
        for ((question, expected) in expectations) {
            val found = kb.retrieve(question)
            val haystack = found.joinToString(" ") { it.source + " " + it.heading }
            if (found.isEmpty() || !expected.containsMatchIn(haystack)) {
                failures += "\"$question\" -> ${found.map { it.source }}"
            }
        }
        assertTrue("questions that failed to reach the right act:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * KNOWN LIMITATION, pinned deliberately so it stays visible.
     *
     * Retrieval is purely lexical (TF-IDF over unigrams), so a tenancy question
     * about a "security deposit" collides with company-law vocabulary, where
     * "securities" and "deposits" are high-signal terms. The Transfer of
     * Property Act - which actually governs leases - is not retrieved.
     *
     * Fixing this needs phrase/bigram scoring or per-domain routing in
     * LegalKnowledgeBase.retrieve(). If that lands, this test will fail and
     * should be promoted into libraryBreadth_eachDomainIsReachable above.
     */
    @Test
    fun knownLimitation_securityDepositQueryMissesTenancyLaw() {
        val sources = kb.retrieve("My landlord will not return my security deposit")
            .map { it.source }
        assertTrue("retrieval should still return something", sources.isNotEmpty())
        assertFalse(
            "retrieval now reaches property/lease law - promote this case into " +
                "libraryBreadth_eachDomainIsReachable. Sources were: $sources",
            sources.any { it.contains("Transfer of Property", ignoreCase = true) }
        )
    }

    @Test
    fun retrieve_respectsPassageAndCharBudget() {
        val found = kb.retrieve("bail conditions for a first time offender", maxPassages = 2, maxChars = 1200)
        assertTrue("should honour maxPassages=2, got ${found.size}", found.size <= 2)
        assertTrue(
            "should honour maxChars=1200, got ${found.sumOf { it.text.length }}",
            found.sumOf { it.text.length } <= 1200 + 1600 // budget check allows the first passage through
        )
    }

    @Test
    fun retrieve_returnsNothingForContentFreeQuery() {
        assertTrue(kb.retrieve("the of and to in").isEmpty())
        assertTrue(kb.retrieve("").isEmpty())
    }

    /**
     * Reproduces exactly what NyayaViewModel.send() assembles before calling the
     * model, proving the offline grounded prompt is complete without any network.
     */
    @Test
    fun offlineGroundedSystemPrompt_isAssembledForTheModel() {
        val question = "Police refuse to file my FIR, what are my rights?"
        val references = kb.retrieve(question)
        assertTrue(references.isNotEmpty())

        val systemPrompt = LawyerSystemPrompt.PROMPT +
            "\n\nVERIFIED REFERENCE EXTRACTS from official Indian law " +
            "(base your answer on these; cite section numbers only from " +
            "these extracts; never invent citations):\n" +
            kb.asReferenceBlock(references)

        assertNotNull(systemPrompt)
        // Guardrails from the system prompt survive into the final payload.
        assertTrue(systemPrompt.contains("LEGAL INFORMATION, not legal advice"))
        assertTrue(systemPrompt.contains("NEVER invent case names, citations"))
        assertTrue(systemPrompt.contains("15100"))
        assertTrue(systemPrompt.contains("112"))
        // Grounding is present and attributed to a source.
        assertTrue(systemPrompt.contains("VERIFIED REFERENCE EXTRACTS"))
        assertTrue(systemPrompt.contains("173"))
        assertTrue(
            "prompt should stay within a small-model context budget, was ${systemPrompt.length}",
            systemPrompt.length < 12000
        )
    }

    @Test
    fun bundledFullActs_areAuthoritativeIndiaCodeText() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val names = context.assets.list("nyaya_kb").orEmpty().filter { it.endsWith(".md") }
        val fullActs = names.filter { it.contains("_full_") }
        assertTrue("expected the full bare acts to be bundled, found $fullActs", fullActs.size >= 20)

        // Every act must come from an official Government of India source. One
        // documented exception: the Income-tax Act, 2025 is not published on
        // indiacode.nic.in or any reachable .gov.in host, so it is taken from
        // PRS Legislative Research's copy of the text as passed by Lok Sabha,
        // and the file header says so explicitly.
        val allowedMirrors = mapOf(
            "34_full_income_tax_act_2025_NEW_in_force.md" to "prsindia.org"
        )
        val notOfficial = fullActs.filter { name ->
            val header = context.assets.open("nyaya_kb/$name")
                .bufferedReader().use { it.readText() }.take(600)
            val official = header.contains("indiacode.nic.in") || header.contains("gov.in")
            val mirror = allowedMirrors[name]?.let { header.contains(it) } ?: false
            !official && !mirror
        }
        assertTrue("these acts are not sourced from an official .gov.in source: $notOfficial", notOfficial.isEmpty())
    }

    /**
     * Both income-tax regimes must be bundled, and each must state — in text the
     * retriever can actually surface — whether it is the repealed 1961 Act or the
     * 2025 Act now in force. Getting this wrong means quoting the wrong tax law
     * as if it were current.
     */
    @Test
    fun bothIncomeTaxActs_areBundledAndClearlyLabelled() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val names = context.assets.list("nyaya_kb").orEmpty().toList()

        val old = names.first { it.contains("income_tax_act_1961") }
        val new = names.first { it.contains("income_tax_act_2025") }

        fun head(name: String) = context.assets.open("nyaya_kb/$name")
            .bufferedReader().use { it.readText() }.take(2500)

        val oldHead = head(old)
        assertTrue("1961 Act must be flagged as repealed:\n$oldHead", oldHead.contains("REPEALED"))
        assertTrue("1961 Act must give the cut-off date", oldHead.contains("1 April 2026"))
        assertTrue("1961 Act must point at the replacement Act", oldHead.contains("2025"))

        val newHead = head(new)
        assertTrue("2025 Act must be flagged as in force:\n$newHead", newHead.contains("IN FORCE"))
        assertTrue("2025 Act must give the commencement date", newHead.contains("1 April 2026"))
        assertTrue("2025 Act must warn that section numbers differ", newHead.contains("numbering differs"))
    }

    @Test
    fun incomeTaxQuestion_reachesIncomeTaxLaw() {
        val sources = kb.retrieve("How much income tax do I have to pay this year?", maxPassages = 6)
            .map { it.source }
        assertTrue(
            "an income-tax question should reach income-tax law, got $sources",
            sources.any { it.contains("Income-tax", ignoreCase = true) }
        )
    }
}
