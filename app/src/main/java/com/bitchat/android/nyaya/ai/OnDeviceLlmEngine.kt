package com.bitchat.android.nyaya.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fully offline LLM engine. Runs Gemma mobile bundles (.task / .litertlm) on the
 * phone via Google's MediaPipe LLM Inference API. After the one-time model
 * download, no network is used at all — conversations never leave the device.
 */
class OnDeviceLlmEngine(private val context: Context) : LlmEngine {

    private var llm: LlmInference? = null

    override val isReady: Boolean get() = llm != null

    /** Loads (or reloads) the model file. Heavy — call from a background scope. */
    suspend fun load(modelFile: File) = withContext(Dispatchers.IO) {
        close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(4096)
            .build()
        llm = LlmInference.createFromOptions(context, options)
    }

    override suspend fun generate(
        systemPrompt: String,
        history: List<ChatTurn>
    ): String = withContext(Dispatchers.Default) {
        val engine = llm ?: throw IllegalStateException(
            "On-device model is not loaded yet. Download/load the model first."
        )
        val prompt = PromptBuilder.buildGemmaPrompt(systemPrompt, history)
        (engine.generateResponse(prompt) ?: "").trim()
    }

    override fun close() {
        try {
            llm?.close()
        } catch (_: Exception) {
        }
        llm = null
    }
}
