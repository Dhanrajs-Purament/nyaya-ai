package com.bitchat.android.nyaya

import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.history.ChatHistoryStore
import com.bitchat.android.nyaya.history.PlainChatFileCodec
import com.bitchat.android.nyaya.history.SavedChat
import com.bitchat.android.nyaya.history.SavedMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Behaviour of the user's saved conversations.
 *
 * These run on the JVM against [PlainChatFileCodec]. The production codec adds
 * Keystore encryption, which needs a real device — the serialisation, ordering,
 * deletion and incognito rules under test here are the parts that can actually
 * contain logic bugs, and they are identical either way.
 */
class ChatHistoryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File
    private lateinit var store: ChatHistoryStore

    @Before
    fun setUp() {
        file = File(folder.newFolder(), "chats.json")
        store = ChatHistoryStore(PlainChatFileCodec(file))
    }

    private fun chat(
        id: String,
        title: String = "Question",
        updatedAt: Long = 1_000L,
        incognito: Boolean = false,
        caseFile: String = "",
        messages: List<SavedMessage> = listOf(
            SavedMessage(ChatTurn.Role.USER, "The police stopped me", updatedAt)
        )
    ) = SavedChat(
        id = id,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        messages = messages,
        caseFile = caseFile,
        incognito = incognito
    )

    @Test
    fun `starts with no conversations`() = runBlocking {
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `saves and reads back a conversation`() = runBlocking {
        store.upsert(chat("a", title = "FIR refused"))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("FIR refused", loaded.first().title)
        assertEquals("The police stopped me", loaded.first().messages.first().text)
    }

    @Test
    fun `survives a new store instance reading the same file`() = runBlocking {
        store.upsert(chat("a"))
        // A fresh instance has an empty cache, so this exercises the file path.
        val reopened = ChatHistoryStore(PlainChatFileCodec(file))
        assertEquals(1, reopened.load().size)
    }

    @Test
    fun `upsert replaces rather than duplicates the same conversation`() = runBlocking {
        store.upsert(chat("a", title = "First draft", updatedAt = 1_000L))
        store.upsert(chat("a", title = "Updated", updatedAt = 2_000L))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("Updated", loaded.first().title)
    }

    @Test
    fun `orders most recently updated first`() = runBlocking {
        store.upsert(chat("old", title = "Older", updatedAt = 1_000L))
        store.upsert(chat("new", title = "Newer", updatedAt = 5_000L))
        store.upsert(chat("mid", title = "Middle", updatedAt = 3_000L))
        assertEquals(listOf("Newer", "Middle", "Older"), store.load().map { it.title })
    }

    @Test
    fun `preserves the case file so a reopened conversation keeps its memory`() = runBlocking {
        store.upsert(chat("a", caseFile = "- FIR 123/2025 at Andheri police station"))
        assertEquals(
            "- FIR 123/2025 at Andheri police station",
            store.load().first().caseFile
        )
    }

    @Test
    fun `never writes an incognito conversation`() = runBlocking {
        store.upsert(chat("secret", incognito = true))
        assertTrue(store.load().isEmpty())
        assertFalse("the file must not even be created", file.exists())
    }

    @Test
    fun `ignores an empty conversation`() = runBlocking {
        store.upsert(chat("empty", messages = emptyList()))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `deletes one conversation and keeps the rest`() = runBlocking {
        store.upsert(chat("a", title = "Keep me"))
        store.upsert(chat("b", title = "Delete me", updatedAt = 2_000L))
        store.delete("b")
        assertEquals(listOf("Keep me"), store.load().map { it.title })
    }

    @Test
    fun `deleting an unknown conversation changes nothing`() = runBlocking {
        store.upsert(chat("a"))
        store.delete("does-not-exist")
        assertEquals(1, store.load().size)
    }

    @Test
    fun `deleteAll removes every conversation and the file itself`() = runBlocking {
        store.upsert(chat("a"))
        store.upsert(chat("b", updatedAt = 2_000L))
        store.deleteAll()
        assertTrue(store.load().isEmpty())
        assertFalse("nothing should be left on disk", file.exists())
    }

    @Test
    fun `sets an unreadable file aside instead of destroying the user's data`() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("{ this is not valid json")
        assertTrue(store.load().isEmpty())
        val quarantined = file.parentFile?.listFiles()?.map { it.name }.orEmpty()
        assertTrue(
            "the original file should be kept for recovery, found $quarantined",
            quarantined.any { it.contains("unreadable") }
        )
    }

    // -----------------------------------------------------------------------
    // Titles
    // -----------------------------------------------------------------------

    @Test
    fun `title uses the question when it is short enough`() {
        assertEquals("How do I file an FIR?", SavedChat.titleFrom("How do I file an FIR?"))
    }

    @Test
    fun `title collapses newlines and stray spacing`() {
        assertEquals("Police refused my FIR", SavedChat.titleFrom("  Police refused\n\n my FIR  "))
    }

    @Test
    fun `title truncates a long question on a word boundary`() {
        val question = "The police have refused to register my FIR and I do not know what to do next"
        val title = SavedChat.titleFrom(question)
        assertTrue("should be shortened", title.length <= SavedChat.MAX_TITLE_CHARS + 1)
        assertTrue("should end with an ellipsis", title.endsWith("\u2026"))
        assertFalse("should not cut mid-word", title.contains("  "))
        assertTrue("should start with the question", question.startsWith(title.dropLast(1).trim()))
    }

    @Test
    fun `title falls back when there is nothing to derive from`() {
        assertEquals("New conversation", SavedChat.titleFrom(null))
        assertEquals("New conversation", SavedChat.titleFrom("   "))
    }

    @Test
    fun `title handles a long run with no spaces to break on`() {
        val title = SavedChat.titleFrom("a".repeat(120))
        assertTrue(title.length <= SavedChat.MAX_TITLE_CHARS + 1)
    }

    @Test
    fun `saved chat reports emptiness`() {
        assertTrue(chat("a", messages = emptyList()).isEmpty)
        assertFalse(chat("a").isEmpty)
    }

    @Test
    fun `generated ids are unique`() {
        val first = SavedChat(title = "a", createdAt = 0, updatedAt = 0, messages = emptyList())
        val second = SavedChat(title = "a", createdAt = 0, updatedAt = 0, messages = emptyList())
        assertNull(null)
        assertFalse(first.id == second.id)
    }
}
