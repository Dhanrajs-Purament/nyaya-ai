package com.bitchat.android.nyaya.ai

import com.bitchat.android.nyaya.settings.NyayaSettings
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
 *
 * "OpenAI-compatible" is a loose standard, so the response parser makes no
 * assumptions beyond the envelope: `message.content` may be a plain string or
 * an array of typed parts, refusals may arrive instead of content, and some
 * providers return HTTP 200 with an `error` object in the body. Every failure
 * path throws an [IOException] whose message says what the provider actually
 * returned, because a BYOK user's only debugging tool is that message.
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
            parseChatCompletion(bodyStr)
        }
    }

    /**
     * Extracts the assistant text from an OpenAI-style chat completion body,
     * tolerating the variations found across "compatible" providers.
     */
    private fun parseChatCompletion(bodyStr: String): String {
        val parsed = try {
            JsonParser.parseString(bodyStr).asJsonObject
        } catch (e: Exception) {
            throw IOException("Cloud AI returned a non-JSON response: " + bodyStr.take(300))
        }

        // Some providers return HTTP 200 with an error object in the body.
        parsed.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.let { err ->
            val msg = err.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                ?: err.toString().take(300)
            throw IOException("Cloud AI provider error: $msg")
        }

        val choices = parsed.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray
        if (choices == null || choices.size() == 0) {
            throw IOException("Cloud AI response had no choices: " + bodyStr.take(300))
        }
        val choice = choices.get(0).asJsonObject
        val message = choice.get("message")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IOException("Cloud AI response had no message: " + bodyStr.take(300))

        // Models that decline to answer report it here rather than in content.
        message.get("refusal")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() }
            ?.let { throw IOException("Cloud AI refused to answer: $it") }

        val text = extractContentText(message.get("content")).trim()
        if (text.isEmpty()) {
            val finishReason = choice.get("finish_reason")
                ?.takeIf { it.isJsonPrimitive }?.asString ?: "unknown"
            throw IOException(
                "Cloud AI returned an empty reply (finish_reason: $finishReason). " +
                    "Check the model name in Settings."
            )
        }
        return text
    }

    /**
     * `content` may be a string (OpenAI), an array of parts (some proxies and
     * self-hosted stacks; each part is either a string or a `{type, text}`
     * object), or JSON null (tool calls / refusals).
     */
    private fun extractContentText(content: JsonElement?): String = when {
        content == null || content.isJsonNull -> ""
        content.isJsonPrimitive -> content.asString
        content.isJsonArray -> content.asJsonArray.joinToString("") { part ->
            when {
                part.isJsonPrimitive -> part.asString
                part.isJsonObject -> part.asJsonObject.get("text")
                    ?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                else -> ""
            }
        }
        else -> ""
    }

    override fun close() {
        // OkHttp client is shared/stateless; nothing to release.
    }
}
