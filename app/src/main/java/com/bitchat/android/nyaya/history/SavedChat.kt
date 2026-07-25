package com.bitchat.android.nyaya.history

import com.bitchat.android.nyaya.ai.ChatTurn
import java.util.UUID

/**
 * One saved conversation, as stored on the user's device.
 *
 * The conversation belongs to the user: it is kept until they delete it, and it
 * is never uploaded anywhere. Deleting is available per chat and for everything
 * at once. Conversations marked [incognito] are never written to storage at all.
 */
data class SavedChat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<SavedMessage>,
    /**
     * The running "Case File" summary from [com.bitchat.android.nyaya.memory.ConversationMemory].
     *
     * This has to be persisted with the transcript. Once a long conversation has
     * been compacted, the older turns exist *only* inside this summary, so
     * reopening a chat without it would silently lose the names, dates and FIR
     * numbers the user already provided.
     */
    val caseFile: String = "",
    /** Incognito chats live in memory only and are never persisted. */
    val incognito: Boolean = false
) {
    val isEmpty: Boolean get() = messages.isEmpty()

    companion object {
        /** Longest auto-derived title, in characters. */
        const val MAX_TITLE_CHARS = 48

        private const val UNTITLED = "New conversation"

        /**
         * Derives a human-readable title from the first thing the user asked.
         *
         * Deliberately plain truncation rather than asking the model for a
         * title: a model call would cost seconds of latency and, on-device,
         * battery, for something the user reads once in a list.
         */
        fun titleFrom(firstUserMessage: String?): String {
            val cleaned = firstUserMessage
                ?.replace(WHITESPACE, " ")
                ?.trim()
                .orEmpty()
            if (cleaned.isEmpty()) return UNTITLED
            if (cleaned.length <= MAX_TITLE_CHARS) return cleaned
            val cut = cleaned.take(MAX_TITLE_CHARS)
            // Prefer a word boundary, but only if it does not throw away most of
            // the title — "Whatisthelawon..." has no spaces to break on.
            val lastSpace = cut.lastIndexOf(' ')
            val body = if (lastSpace >= MAX_TITLE_CHARS / 2) cut.take(lastSpace) else cut
            return body.trimEnd(' ', ',', '.', ';', ':', '-') + "\u2026"
        }

        private val WHITESPACE = Regex("\\s+")
    }
}

/** A single turn in a saved conversation. */
data class SavedMessage(
    val role: ChatTurn.Role,
    val text: String,
    val at: Long
)
