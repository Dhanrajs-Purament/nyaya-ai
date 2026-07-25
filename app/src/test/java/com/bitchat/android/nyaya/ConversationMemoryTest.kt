package com.bitchat.android.nyaya

import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.ai.LawyerSystemPrompt
import com.bitchat.android.nyaya.ai.NyayaModelCatalog
import com.bitchat.android.nyaya.memory.ConversationMemory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the two pieces that decide whether a long, real
 * conversation stays coherent: the rolling Case File memory and the Gemma
 * chat-format prompt assembly. No Android, no network, no model.
 */
class ConversationMemoryTest {

    private fun memory(
        maxContextTokens: Int = 2600,
        minTurnsBeforeCompaction: Int = 8,
        recentTurnsToKeep: Int = 4,
        summarizer: suspend (String) -> String
    ) = ConversationMemory(
        maxContextTokens = maxContextTokens,
        minTurnsBeforeCompaction = minTurnsBeforeCompaction,
        recentTurnsToKeep = recentTurnsToKeep,
        summarize = summarizer
    )

    /** Builds a realistic long consultation: 30 alternating turns. */
    private fun ConversationMemory.seedLongConversation(turns: Int = 30) {
        for (i in 0 until turns) {
            val role = if (i % 2 == 0) ChatTurn.Role.USER else ChatTurn.Role.ASSISTANT
            add(ChatTurn(role, "Message $i about the FIR at Andheri police station. " + "x".repeat(400)))
        }
    }

    @Test
    fun shortConversation_isNotCompacted() = runBlocking {
        var called = false
        val m = memory { called = true; "summary" }
        m.add(ChatTurn(ChatTurn.Role.USER, "My landlord kept my deposit."))
        m.add(ChatTurn(ChatTurn.Role.ASSISTANT, "Tell me which state you are in."))
        m.compactIfNeeded()

        assertFalse("summarizer must not run for a short chat", called)
        assertEquals("", m.caseFile)
        assertEquals(2, m.allTurns().size)
    }

    @Test
    fun longConversation_compactsIntoCaseFileAndKeepsRecentTurns() = runBlocking {
        var summarizerCalls = 0
        var lastPrompt = ""
        val m = memory { prompt ->
            summarizerCalls++
            lastPrompt = prompt
            "- FIR pending at Andheri police station\n- User wants a copy of the FIR"
        }
        m.seedLongConversation(30)
        assertEquals(30, m.allTurns().size)

        m.compactIfNeeded()

        assertEquals("summarizer should run exactly once", 1, summarizerCalls)
        assertEquals("only the recent window should survive", 4, m.allTurns().size)
        assertTrue("case file should be populated", m.caseFile.contains("Andheri"))
        // The summarization prompt must carry the anti-hallucination contract.
        assertTrue(lastPrompt.contains("CASE FILE"))
        assertTrue(lastPrompt.contains("Do not add anything that"))
        // The oldest turns were folded away, the newest kept verbatim.
        assertTrue(m.allTurns().last().text.contains("Message 29"))
        assertTrue(m.allTurns().none { it.text.contains("Message 0 ") })
    }

    @Test
    fun compaction_reinjectsCaseFileAsTrustedContext() = runBlocking {
        val m = memory { "- Client name: Asha\n- FIR number: 123/2026" }
        m.seedLongConversation(30)
        m.compactIfNeeded()

        val context = m.contextForModel()
        assertTrue("case file must be prepended", context.first().text.contains("CASE FILE"))
        assertTrue(context.first().text.contains("FIR number: 123/2026"))
        assertEquals(ChatTurn.Role.USER, context.first().role)
        assertEquals(ChatTurn.Role.ASSISTANT, context[1].role)
        assertEquals("case-file pair + 4 recent turns", 6, context.size)
    }

    @Test
    fun summarizerFailure_neverLosesExistingMemory() = runBlocking {
        var failNext = false
        val m = memory { if (failNext) throw IllegalStateException("engine died") else "- first summary" }

        m.seedLongConversation(30)
        m.compactIfNeeded()
        assertEquals("- first summary", m.caseFile)

        failNext = true
        m.seedLongConversation(30)
        m.compactIfNeeded()
        assertEquals("old case file must survive a summarizer crash", "- first summary", m.caseFile)
        assertEquals(4, m.allTurns().size)
    }

    @Test
    fun blankSummary_doesNotWipeCaseFile() = runBlocking {
        var blank = false
        val m = memory { if (blank) "   " else "- real memory" }
        m.seedLongConversation(30)
        m.compactIfNeeded()
        blank = true
        m.seedLongConversation(30)
        m.compactIfNeeded()
        assertEquals("- real memory", m.caseFile)
    }

    @Test
    fun clear_resetsTurnsAndCaseFile() = runBlocking {
        val m = memory { "- something" }
        m.seedLongConversation(30)
        m.compactIfNeeded()
        m.clear()
        assertEquals(0, m.allTurns().size)
        assertEquals("", m.caseFile)
        assertTrue(m.contextForModel().isEmpty())
    }

    @Test
    fun contextForModel_withoutCaseFile_isJustTheTurns() {
        val m = memory { "unused" }
        m.add(ChatTurn(ChatTurn.Role.USER, "hello"))
        assertEquals(1, m.contextForModel().size)
        assertEquals("hello", m.contextForModel().first().text)
    }

    @Test
    fun repeatedCompaction_keepsContextBounded() = runBlocking {
        val m = memory { "- running case file entry" }
        repeat(5) {
            m.seedLongConversation(30)
            m.compactIfNeeded()
        }
        val totalChars = m.contextForModel().sumOf { it.text.length }
        assertTrue(
            "context must stay bounded across many compactions, was $totalChars chars",
            totalChars < 20_000
        )
    }
}

class LawyerSystemPromptTest {

    @Test
    fun realLawyerPrompt_carriesItsSafetyGuardrails() {
        val prompt = LawyerSystemPrompt.PROMPT
        assertTrue("must not claim to be an advocate", prompt.contains("not an advocate"))
        assertTrue("must forbid invented citations", prompt.contains("NEVER invent"))
        assertTrue("must route to free legal aid", prompt.contains("15100"))
        assertTrue("must route emergencies to 112", prompt.contains("112"))
        assertTrue("must know IPC/CrPC were replaced", prompt.contains("Bharatiya Nyaya Sanhita"))
    }

    /**
     * Regression guard for the Gemma 3 to Gemma 4 migration.
     *
     * Gemma 3 used `<start_of_turn>user`; Gemma 4 uses `<|turn>user`. The app no
     * longer writes turn markers at all — LiteRT-LM applies each model's own
     * chat template — so no hard-coded marker of either dialect may reappear in
     * the prompt or anywhere else in the Nyaya sources.
     */
    @Test
    fun systemPrompt_containsNoHardCodedChatTemplateMarkers() {
        val prompt = LawyerSystemPrompt.PROMPT
        assertFalse("Gemma 3 marker leaked in", prompt.contains("<start_of_turn>"))
        assertFalse("Gemma 3 marker leaked in", prompt.contains("<end_of_turn>"))
        assertFalse("Gemma 4 marker leaked in", prompt.contains("<|turn>"))
    }
}

class NyayaModelCatalogTest {

    @Test
    fun defaultModel_isTheSmallGemma4Bundle() {
        val default = NyayaModelCatalog.default
        assertEquals("gemma-4-e2b", default.id)
        assertTrue("default must be a LiteRT-LM bundle", default.isLiteRtLm)
        assertTrue("default must be a Gemma 4 model", default.url.contains("gemma-4-E2B"))
    }

    @Test
    fun everyCatalogEntry_isAnUngatedLiteRtLmBundleOverHttps() {
        assertTrue(NyayaModelCatalog.all.isNotEmpty())
        for (model in NyayaModelCatalog.all) {
            assertTrue("${model.id} must download over HTTPS", model.url.startsWith("https://"))
            assertTrue("${model.id} must be a .litertlm bundle", model.isLiteRtLm)
            assertTrue(
                "${model.id} must come from the ungated litert-community org",
                model.url.contains("litert-community/")
            )
            assertTrue("${model.id} needs a real expected size", model.downloadBytes > 0)
            assertTrue(
                "${model.id} url must end with its file name",
                model.url.endsWith(model.fileName)
            )
        }
    }

    @Test
    fun catalogIds_areUnique() {
        val ids = NyayaModelCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun fileNameForUrl_prefersCatalogNameAndFallsBackToLastPathSegment() {
        assertEquals(
            NyayaModelCatalog.GEMMA_4_E4B.fileName,
            NyayaModelCatalog.fileNameForUrl(NyayaModelCatalog.GEMMA_4_E4B.url)
        )
        assertEquals(
            "my-model.litertlm",
            NyayaModelCatalog.fileNameForUrl("https://example.org/a/b/my-model.litertlm?download=true")
        )
        assertTrue(
            "a URL with no file segment still yields a usable name",
            NyayaModelCatalog.fileNameForUrl("https://example.org/").endsWith(".litertlm")
        )
    }

    @Test
    fun expectedBytes_isKnownForCatalogUrlsAndUnknownOtherwise() {
        assertEquals(
            NyayaModelCatalog.GEMMA_4_E2B.downloadBytes,
            NyayaModelCatalog.expectedBytesForUrl(NyayaModelCatalog.GEMMA_4_E2B.url)
        )
        assertNull(NyayaModelCatalog.expectedBytesForUrl("https://example.org/custom.litertlm"))
    }

    @Test
    fun litertLmMagic_isTheBundleHeader() {
        assertEquals("LITERTLM", String(NyayaModelCatalog.LITERTLM_MAGIC, Charsets.US_ASCII))
        assertEquals("TFL3", String(NyayaModelCatalog.TFLITE_MAGIC, Charsets.US_ASCII))
    }
}
