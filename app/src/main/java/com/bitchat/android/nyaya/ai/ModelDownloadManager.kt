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
 * (HTTP Range).
 *
 * A Gemma 4 bundle is 2.4-3.4 GB, so a download interrupted by a dropped
 * mobile connection is the normal case rather than the exception. Every
 * completed download is therefore verified before it is promoted to its final
 * name: the byte count must match the advertised length and the file must start
 * with a recognised model magic. Without those checks a stream that ended early
 * would be renamed into place and treated as a valid model forever, leaving the
 * user with an engine that can never load and no way to notice why.
 *
 * An optional Hugging Face access token is supported for gated or private
 * repositories. The Gemma 4 bundles in the catalog are Apache-2.0 and
 * ungated, so the token is not needed for the default model.
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

    private fun partFile(fileName: String): File = File(modelsDir, "$fileName.part")

    /**
     * True only for a model that passed verification. [expectedBytes], when
     * known, guards against a file left behind by an older build that did not
     * verify sizes.
     */
    fun isDownloaded(fileName: String, expectedBytes: Long? = null): Boolean {
        val f = modelFile(fileName)
        if (!f.isFile || f.length() == 0L) return false
        return expectedBytes == null || f.length() == expectedBytes
    }

    fun deleteModel(fileName: String) {
        modelFile(fileName).delete()
        partFile(fileName).delete()
    }

    /**
     * Downloads [url] to [fileName], resuming a partial download if present.
     * [onProgress] receives 0f..1f (or -1f when the total size is unknown).
     *
     * @param expectedBytes exact expected size, used to reject a truncated
     *   download. Defaults to the catalog's value for [url] when it has one.
     */
    suspend fun download(
        url: String,
        fileName: String,
        hfToken: String = "",
        expectedBytes: Long? = NyayaModelCatalog.expectedBytesForUrl(url),
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(fileName)
        if (isDownloaded(fileName, expectedBytes)) return@withContext target
        // A stale file of the wrong size must not shadow a fresh download.
        if (target.exists()) target.delete()

        val tmp = partFile(fileName)
        val existing = if (tmp.exists()) tmp.length() else 0L

        val builder = Request.Builder().url(url)
        if (hfToken.isNotBlank()) builder.header("Authorization", "Bearer " + hfToken)
        if (existing > 0) builder.header("Range", "bytes=$existing-")

        var written = 0L
        var advertisedTotal = -1L

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw IOException(
                    "Access denied (HTTP " + resp.code + "). This model repository is " +
                        "license-gated: accept the license on huggingface.co and paste a HF " +
                        "access token in Settings, or pick one of the bundled Gemma 4 models, " +
                        "which need no token."
                )
            }
            if (!resp.isSuccessful) throw IOException("Download failed: HTTP " + resp.code)
            val body = resp.body ?: throw IOException("Empty response body")

            val resumed = resp.code == 206
            val already = if (resumed) existing else 0L
            advertisedTotal = if (body.contentLength() > 0) body.contentLength() + already else -1L
            val total = expectedBytes ?: advertisedTotal

            var done = already
            FileOutputStream(tmp, resumed).use { out ->
                val buf = ByteArray(1 shl 16)
                val input = body.byteStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(if (total > 0) done.toFloat() / total.toFloat() else -1f)
                }
                out.flush()
                out.fd.sync()
            }
            written = done
        }

        verifyOrFail(tmp, fileName, expectedBytes ?: advertisedTotal, written)

        if (!tmp.renameTo(target)) throw IOException("Could not finalize downloaded model file")
        onProgress(1f)
        target
    }

    /**
     * Rejects an incomplete or non-model file. The partial file is kept when the
     * only problem is length, so the next attempt can resume instead of
     * restarting a multi-gigabyte transfer; it is deleted when the content is
     * not a model at all, because resuming that is pointless.
     */
    private fun verifyOrFail(tmp: File, fileName: String, expectedTotal: Long, written: Long) {
        val actual = tmp.length()
        if (expectedTotal > 0 && actual != expectedTotal) {
            throw IOException(
                "Download incomplete: got " + actual + " of " + expectedTotal +
                    " bytes for " + fileName + ". Reconnect and tap download again to resume."
            )
        }
        if (actual == 0L) {
            tmp.delete()
            throw IOException("Download produced an empty file for $fileName.")
        }
        if (!hasModelMagic(tmp)) {
            tmp.delete()
            throw IOException(
                "Downloaded file is not a recognised model bundle. Check the model URL in " +
                    "Settings — it must point directly at a .litertlm file, not a web page."
            )
        }
        if (written == 0L && expectedTotal <= 0) {
            throw IOException("Nothing was downloaded for $fileName.")
        }
    }

    /** Accepts a LiteRT-LM bundle, or a TFLite `.task` bundle. */
    private fun hasModelMagic(file: File): Boolean {
        val header = ByteArray(HEADER_PROBE_BYTES)
        val read = file.inputStream().use { it.read(header) }
        if (read < HEADER_PROBE_BYTES) return false
        val litertLm = NyayaModelCatalog.LITERTLM_MAGIC
        if (regionMatches(header, 0, litertLm)) return true
        return regionMatches(header, NyayaModelCatalog.TFLITE_MAGIC_OFFSET, NyayaModelCatalog.TFLITE_MAGIC)
    }

    private fun regionMatches(haystack: ByteArray, offset: Int, needle: ByteArray): Boolean {
        if (offset + needle.size > haystack.size) return false
        for (i in needle.indices) {
            if (haystack[offset + i] != needle[i]) return false
        }
        return true
    }

    private companion object {
        const val HEADER_PROBE_BYTES = 16
    }
}
