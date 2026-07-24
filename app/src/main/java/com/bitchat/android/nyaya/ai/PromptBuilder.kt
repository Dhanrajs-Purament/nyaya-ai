package com.bitchat.android.nyaya.ai

/**
 * Builds prompts in the Gemma instruction-tuned chat format for on-device
 * inference. Cloud engines use structured messages instead.
 */
object PromptBuilder {

    fun buildGemmaPrompt(systemPrompt: String, history: List<ChatTurn>): String {
        val sb = StringBuilder()
        var systemInjected = false
        for (turn in history) {
            when (turn.role) {
                ChatTurn.Role.USER -> {
                    sb.append("<start_of_turn>user\n")
                    if (!systemInjected) {
                        sb.append(systemPrompt).append("\n\n")
                        systemInjected = true
                    }
                    sb.append(turn.text).append("<end_of_turn>\n")
                }
                ChatTurn.Role.ASSISTANT -> {
                    sb.append("<start_of_turn>model\n")
                    sb.append(turn.text).append("<end_of_turn>\n")
                }
            }
        }
        if (!systemInjected) {
            // No user turn yet; still inject the system prompt.
            sb.append("<start_of_turn>user\n").append(systemPrompt).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
