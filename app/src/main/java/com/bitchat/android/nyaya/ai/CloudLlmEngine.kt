package com.bitchat.android.nyaya.ai

import com.bitchat.android.nyaya.settings.NyayaSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * BYOK (bring-your-own-key) engine. Talks to any OpenAI-compatible
 * /chat/completions endpoint (OpenAI, Groq, OpenRouter, Gemini-compat proxies,
 * self-hosted vLLM/Ollama, etc.) over HTTPS using the user's own API key.
 * The key is stored in Keystore-encrypted preferences and is only ever sent to
 * the endpoint the user configured.
 */
class CloudLlmEngine(private val settings: NyayaSettings) : LlmEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    override val isReady: Boolean
        get() = settings.cloudApiKey.isNotBlank() && settings.cloudBaseUrl.isNotBlank()

    override suspend fun generate(
        systemPrompt: String,
        history: List<ChatTurn>
    ): String = withContext(Dispatchers.IO) {
        val messages = JsonArray()
        fun add(role: String, content: String) {
            val o = JsonObject()
            o.addProperty("role", role)
            o.addProperty("content", content)
            messages.add(o)
        }
        add("system", systemPrompt)
        for (turn in history) {
            add(if (turn.role == ChatTurn.Role.USER) "user" else "assistant", turn.text)
        }

        val root = JsonObject()
        root.addProperty("model", settings.cloudModel)
        root.add("messages", messages)
        root.addProperty("temperature", 0.3)

        val request = Request.Builder()
            .url(settings.cloudBaseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + settings.cloudApiKey)
            .post(root.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("Cloud AI error HTTP " + resp.code + ": " + bodyStr.take(300))
            }
            val parsed = JsonParser.parseString(bodyStr).asJsonObject
            parsed.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
                .trim()
        }
    }

    override fun close() {
        // OkHttp client is shared/stateless; nothing to release.
    }
}
