package com.bitchat.android.nyaya.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fully offline LLM engine. Runs Gemma 4 `.litertlm` bundles on the phone via
 * Google's LiteRT-LM runtime. After the one-time model download no network is
 * used at all — conversations never leave the device.
 *
 * Two things are deliberately delegated to LiteRT-LM rather than done here:
 *
 *  * **Prompt templating.** The runtime applies the model's own chat template,
 *    so the app passes a system instruction plus role-tagged messages and never
 *    writes turn markers itself. Gemma 4 uses `<|turn>user`, Gemma 3 used
 *    `<start_of_turn>user`; hand-assembling either one would break the moment
 *    the model changes.
 *  * **KV-cache management**, which is what makes a multi-turn conversation
 *    affordable on a phone.
 */
class OnDeviceLlmEngine(private val context: Context) : LlmEngine {

    private var engine: Engine? = null

    /** Serialises native calls: the runtime is not safe to drive concurrently. */
    private val lock = Mutex()

    /** Backend actually in use once loaded, e.g. "GPU" or "CPU". */
    @Volatile
    var activeBackend: String = ""
        private set

    override val isReady: Boolean get() = engine != null

    /**
     * Loads (or reloads) a model bundle. Heavy — call from a background scope.
     *
     * When [preferGpu] is set the GPU backend is tried first because it is both
     * markedly faster and lighter on RAM than CPU for these bundles; if GPU
     * initialisation fails on the device we fall back to CPU rather than
     * leaving the user with no offline engine at all.
     */
    suspend fun load(modelFile: File, preferGpu: Boolean = true) = withContext(Dispatchers.IO) {
        lock.withLock {
            closeLocked()

            if (!modelFile.isFile || modelFile.length() == 0L) {
                throw IllegalStateException(
                    "Model file is missing or empty: ${modelFile.name}. " +
                        "Download the model again from Settings."
                )
            }

            val backends = if (preferGpu) {
                listOf(Backend.GPU() to "GPU", Backend.CPU() to "CPU")
            } else {
                listOf(Backend.CPU() to "CPU")
            }

            val failures = mutableListOf<String>()
            for ((backend, label) in backends) {
                val candidate = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        maxNumTokens = MAX_CONTEXT_TOKENS,
                        cacheDir = context.cacheDir.absolutePath
                    )
                )
                try {
                    candidate.initialize()
                    engine = candidate
                    activeBackend = label
                    return@withLock
                } catch (t: Throwable) {
                    runCatching { candidate.close() }
                    failures += "$label: ${t.message ?: t::class.java.simpleName}"
                }
            }
            throw IllegalStateException(describeLoadFailure(failures))
        }
    }

    /**
     * Generates a reply. A fresh conversation is created per call because the
     * system prompt carries freshly retrieved legal extracts that differ for
     * every question, and a conversation's system instruction is fixed at
     * creation time.
     */
    override suspend fun generate(
        systemPrompt: String,
        history: List<ChatTurn>
    ): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val active = engine ?: throw IllegalStateException(
                "On-device model is not loaded yet. Download/load the model first."
            )
            // Everything except the final user turn becomes prior context; the
            // final turn is what we actually send.
            val lastUserIndex = history.indexOfLast { it.role == ChatTurn.Role.USER }
            if (lastUserIndex < 0) {
                throw IllegalArgumentException("Cannot generate a reply without a user message.")
            }
            val priorTurns = history.subList(0, lastUserIndex)
            val question = history[lastUserIndex].text

            val config = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = priorTurns.map { turn ->
                    if (turn.role == ChatTurn.Role.USER) {
                        Message.user(turn.text)
                    } else {
                        Message.model(Contents.of(turn.text))
                    }
                },
                samplerConfig = SamplerConfig(
                    topK = SAMPLER_TOP_K,
                    topP = SAMPLER_TOP_P,
                    temperature = ANSWER_TEMPERATURE
                )
            )

            active.createConversation(config).use { conversation ->
                textOf(conversation.sendMessage(question))
            }
        }
    }

    /** Flattens a reply's content parts into plain text. */
    private fun textOf(message: Message): String =
        message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .trim()

    private fun describeLoadFailure(failures: List<String>): String {
        val detail = failures.joinToString("; ")
        val abiHint = if (failures.any { it.contains("UnsatisfiedLinkError", ignoreCase = true) }) {
            " LiteRT-LM ships native code for 64-bit devices only (arm64-v8a, x86_64), " +
                "so on-device AI is unavailable on this phone. Use Settings to add your own " +
                "API key instead."
        } else {
            ""
        }
        return "Could not start the on-device AI engine ($detail).$abiHint"
    }

    override fun close() {
        // close() is not suspending (it is called from ViewModel.onCleared), so
        // it cannot take the coroutine mutex; releasing the native engine is
        // idempotent and the reference swap below is atomic.
        closeLocked()
    }

    private fun closeLocked() {
        val current = engine
        engine = null
        activeBackend = ""
        if (current != null) {
            runCatching { current.close() }
        }
    }

    private companion object {
        /** Bounds resident memory; the bundles themselves support far more. */
        const val MAX_CONTEXT_TOKENS = 4096

        /** Low temperature: legal answers should be conservative, not creative. */
        const val ANSWER_TEMPERATURE = 0.3

        /** Gemma's recommended nucleus-sampling settings. */
        const val SAMPLER_TOP_K = 64
        const val SAMPLER_TOP_P = 0.95
    }
}
