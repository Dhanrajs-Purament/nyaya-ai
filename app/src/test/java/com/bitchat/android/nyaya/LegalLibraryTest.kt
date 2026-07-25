package com.bitchat.android.nyaya

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bitchat.android.nyaya.ai.LegalLibrary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The offline legal library, tested against the real bundled assets rather than
 * fixtures — the point of these tests is that what actually ships is browsable.
 */
@RunWith(RobolectricTestRunner::class)
class LegalLibraryTest {

    private lateinit var library: LegalLibrary
    private lateinit var documents: List<LegalLibrary.Document>

    @Before
    fun setUp() {
        library = LegalLibrary(ApplicationProvider.getApplicationContext<Application>())
        documents = runBlocking { library.list() }
    }

    @Test
    fun `lists the bundled law`() {
        assertTrue("expected a substantial library, got ${documents.size}", documents.size >= 30)
    }

    @Test
    fun `excludes the readme from the browsable list`() {
        assertFalse(documents.any { it.assetName == "README.md" })
    }

    @Test
    fun `every document has a readable title rather than a file name`() {
        documents.forEach { doc ->
            assertFalse(
                "${doc.assetName} fell back to its file name",
                doc.title.contains(".md") || doc.title.contains("_")
            )
            assertTrue("${doc.assetName} has an empty title", doc.title.isNotBlank())
        }
    }

    @Test
    fun `guides are listed before the full acts`() {
        val firstAct = documents.indexOfFirst { it.kind == LegalLibrary.Kind.FULL_ACT }
        val lastGuide = documents.indexOfLast { it.kind == LegalLibrary.Kind.GUIDE }
        assertTrue("both kinds should be present", firstAct > 0 && lastGuide >= 0)
        assertTrue("guides must come first", lastGuide < firstAct)
    }

    @Test
    fun `classifies the new criminal codes as full acts`() {
        val bns = documents.first { it.assetName.contains("bharatiya_nyaya_sanhita") }
        assertEquals(LegalLibrary.Kind.FULL_ACT, bns.kind)
    }

    @Test
    fun `classifies the curated guidance as a guide`() {
        val guide = documents.first { it.assetName.contains("cyber_offences") }
        assertEquals(LegalLibrary.Kind.GUIDE, guide.kind)
    }

    @Test
    fun `flags the repealed income-tax act so a user cannot mistake it for current law`() {
        val old = documents.first { it.assetName.contains("income_tax_act_1961") }
        assertNotNull("the repealed Act must carry a status note", old.statusNote)
        val note = old.statusNote.orEmpty()
        assertTrue("status should say it is repealed, was: $note", note.contains("REPEAL", true))
        assertTrue("status must stay short enough for a badge", note.length <= 48)
    }

    @Test
    fun `flags the in-force income-tax act as current`() {
        val current = documents.first { it.assetName.contains("income_tax_act_2025") }
        assertNotNull(current.statusNote)
        assertFalse(
            "the in-force Act must not be labelled repealed",
            current.statusNote.orEmpty().contains("REPEALED", true)
        )
    }

    @Test
    fun `ordinary acts carry no status badge`() {
        val contract = documents.first { it.assetName.contains("indian_contract_act") }
        assertNull(contract.statusNote)
    }

    @Test
    fun `short title drops the trailing provenance clauses`() {
        val old = documents.first { it.assetName.contains("income_tax_act_1961") }
        assertFalse(
            "short title should not carry the FULL TEXT suffix: ${old.shortTitle}",
            old.shortTitle.contains("FULL TEXT")
        )
        assertTrue(old.shortTitle.length < old.title.length)
    }

    @Test
    fun `splits a guide into readable sections`() {
        val guide = documents.first { it.assetName.contains("emergency_and_helplines") }
        val sections = runBlocking { library.sections(guide.assetName) }
        assertTrue("expected several sections, got ${sections.size}", sections.size >= 3)
        assertTrue(
            "at least one section should have body text",
            sections.any { it.body.isNotBlank() }
        )
    }

    @Test
    fun `splits a full act into its numbered sections`() {
        val act = documents.first { it.assetName.contains("consumer_protection_act") }
        val sections = runBlocking { library.sections(act.assetName) }
        assertTrue("a full Act should split into many sections", sections.size >= 20)
    }

    private fun assertNull(value: Any?) = org.junit.Assert.assertNull(value)
}
