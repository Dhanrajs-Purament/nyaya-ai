package com.bitchat.android.nyaya.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads LLM model bundles into app-private storage with resume support
 * (HTTP Range). Supports an optional Hugging Face access token for gated
 * repos (Gemma weights require accepting Google's license on HF).
 */
class ModelDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // large files; no read timeout
        .followRedirects(true)
        .build()

    val modelsDir: File
        get() = File(context.filesDir, "nyaya_models").apply { mkdirs() }

    fun modelFile(fileName: String): File = File(modelsDir, fileName)

    fun isDownloaded(fileName: String): Boolean {
        val f = modelFile(fileName)
        return f.exists() && f.length() > 0
    }

    fun deleteModel(fileName: String) {
        modelFile(fileName).delete()
        File(modelsDir, "$fileName.part").delete()
    }

    /**
     * Downloads [url] to [fileName], resuming a partial download if present.
     * [onProgress] receives 0f..1f (or -1f when total size is unknown).
     */
    suspend fun download(
        url: String,
        fileName: String,
        hfToken: String = "",
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        if (target.exists() && target.length() > 0) return@withContext target

        val tmp = File(modelsDir, "$fileName.part")
        val existing = if (tmp.exists()) tmp.length() else 0L

        val builder = Request.Builder().url(url)
        if (hfToken.isNotBlank()) builder.header("Authorization", "Bearer " + hfToken)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw IOException(
                    "Access denied (HTTP " + resp.code + "). Gemma models on Hugging Face are " +
                        "license-gated: accept the license on huggingface.co and paste a HF " +
                        "access token in Settings."
                )
            }
            if (!resp.isSuccessful) throw IOException("Download failed: HTTP " + resp.code)
            val body = resp.body ?: throw IOException("Empty response body")

            val resumed = resp.code == 206
            val already = if (resumed) existing else 0L
            val total = if (body.contentLength() > 0) body.contentLength() + already else -1L

            FileOutputStream(tmp, resumed).use { out ->
                val buf = ByteArray(1 shl 16)
                var done = already
                val input = body.byteStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(if (total > 0) done.toFloat() / total.toFloat() else -1f)
                }
                out.flush()
            }
        }

        if (!tmp.renameTo(target)) throw IOException("Could not finalize downloaded model file")
        onProgress(1f)
        target
    }
}
