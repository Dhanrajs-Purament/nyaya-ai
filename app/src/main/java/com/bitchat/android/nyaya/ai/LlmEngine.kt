package com.bitchat.android.nyaya.ai

/**
 * A single turn in the AI lawyer conversation.
 */
data class ChatTurn(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

/**
 * Abstraction over LLM backends (on-device Gemma via MediaPipe, or a BYOK
 * OpenAI-compatible cloud endpoint). The UI and memory layers only talk to this.
 */
interface LlmEngine {
    val isReady: Boolean

    /**
     * Generates a complete assistant reply for the given system prompt and history.
     * Runs on a background dispatcher; throws on failure.
     */
    suspend fun generate(systemPrompt: String, history: List<ChatTurn>): String

    fun close()
}
