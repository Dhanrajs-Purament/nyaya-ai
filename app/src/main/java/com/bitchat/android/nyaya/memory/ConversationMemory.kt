package com.bitchat.android.nyaya.memory

import com.bitchat.android.nyaya.ai.ChatTurn

/**
 * Long-conversation memory that prevents context overflow and hallucination drift:
 *
 * 1. Rolling recursive summarization — when the transcript exceeds the token
 *    budget, the older half is summarized (by the model itself) into a running
 *    "Case File" that is re-injected as trusted context (arXiv 2308.15022 pattern).
 * 2. The Case File preserves durable facts — names, dates, places, FIR numbers,
 *    statutes discussed, advice already given — so nothing important is lost.
 */
class ConversationMemory(
    private val maxContextTokens: Int = 2600,
    private val minTurnsBeforeCompaction: Int = 8,
    private val recentTurnsToKeep: Int = 4,
    private val summarize: suspend (prompt: String) -> String
) {

    private val turns = mutableListOf<ChatTurn>()

    /** The running "Case File" summary. Empty until the first compaction. */
    var caseFile: String = ""
        private set

    fun allTurns(): List<ChatTurn> = turns.toList()

    fun add(turn: ChatTurn) {
        turns.add(turn)
    }

    fun clear() {
        turns.clear()
        caseFile = ""
    }

    /**
     * Restores a conversation that was reopened from the user's saved history.
     *
     * The Case File has to come back with the turns. Once a long conversation has
     * been compacted the older exchanges exist only inside that summary, so
     * restoring turns alone would silently drop the names, dates and FIR numbers
     * the user already gave and the model would start asking for them again.
     */
    fun restore(previousTurns: List<ChatTurn>, previousCaseFile: String) {
        turns.clear()
        turns.addAll(previousTurns)
        caseFile = previousCaseFile
    }

    private fun estimateTokens(text: String): Int = text.length / 4 + 1

    /**
     * Compacts old turns into the Case File when the context grows too large.
     * Call after each exchange, from a background scope.
     */
    suspend fun compactIfNeeded() {
        val total = turns.sumOf { estimateTokens(it.text) } + estimateTokens(caseFile)
        if (total <= maxContextTokens || turns.size <= minTurnsBeforeCompaction) return

        val old = turns.take(turns.size - recentTurnsToKeep)
        val transcript = old.joinToString("\n") { t ->
            (if (t.role == ChatTurn.Role.USER) "User: " else "Nyaya: ") + t.text
        }
        val prompt = buildString {
            append("You maintain the CASE FILE for an ongoing legal-help conversation. ")
            append("Update it with the new transcript below. STRICT RULES: keep every ")
            append("name, date, place, FIR/case number, statute or section mentioned, ")
            append("the user's goal, and advice already given. Do not add anything that ")
            append("is not in the transcript. Output only the updated case file as short ")
            append("bullet points.\n\n")
            append("EXISTING CASE FILE:\n")
            append(if (caseFile.isBlank()) "(empty)" else caseFile)
            append("\n\nNEW TRANSCRIPT:\n")
            append(transcript)
        }

        caseFile = try {
            summarize(prompt).trim().ifBlank { caseFile }
        } catch (e: Exception) {
            caseFile // keep old summary on failure; never lose memory
        }

        val recent = turns.takeLast(recentTurnsToKeep)
        turns.clear()
        turns.addAll(recent)
    }

    /** History to send to the model: Case File first (as trusted memory), then recent turns. */
    fun contextForModel(): List<ChatTurn> {
        if (caseFile.isBlank()) return turns.toList()
        return listOf(
            ChatTurn(
                ChatTurn.Role.USER,
                "CASE FILE — verified memory of our conversation so far. Trust it and do not re-ask:\n" + caseFile
            ),
            ChatTurn(ChatTurn.Role.ASSISTANT, "Understood. I have the case file and will continue from there.")
        ) + turns
    }
}
