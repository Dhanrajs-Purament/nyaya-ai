package com.bitchat.android.nyaya.ai

/**
 * A downloadable on-device model bundle.
 *
 * @param id stable key persisted in settings
 * @param displayName shown in the settings screen
 * @param url direct download URL
 * @param fileName name the bundle is stored under in app-private storage
 * @param downloadBytes exact size of the bundle, used to detect truncated downloads
 * @param recommendedRamBytes device RAM at or above which this model is usable
 * @param summary one-line description for the settings screen
 */
data class NyayaModel(
    val id: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val downloadBytes: Long,
    val recommendedRamBytes: Long,
    val summary: String
) {
    /** True for LiteRT-LM bundles, which is what the on-device engine runs. */
    val isLiteRtLm: Boolean get() = fileName.endsWith(NyayaModelCatalog.LITERTLM_SUFFIX)
}

/**
 * The on-device models Nyaya supports, and the single source of truth for their
 * URLs and sizes.
 *
 * Nyaya runs Gemma 4 through Google's LiteRT-LM runtime, using the `.litertlm`
 * bundles published by the litert-community organisation. Two properties of
 * those bundles matter a great deal for this app:
 *
 *  * They are Apache-2.0 and **not** license-gated, so a user can download a
 *    model without a Hugging Face account or token. The previous Gemma 3
 *    default lived in a gated repo and answered HTTP 401 for everybody who had
 *    not accepted Google's license, which made offline mode unreachable out of
 *    the box.
 *  * LiteRT-LM applies each model's own chat template internally, so the app
 *    must not hand-assemble turn markers. Gemma 4 uses `<|turn>user` where
 *    Gemma 3 used `<start_of_turn>user`; any hand-rolled Gemma 3 prompt is
 *    silently malformed input to a Gemma 4 model.
 *
 * E2B and E4B are the "effective parameter" mobile variants: a mixed 2/4/8-bit
 * quantisation with memory-mapped embeddings, so resident memory is far smaller
 * than the file on disk.
 */
object NyayaModelCatalog {

    const val LITERTLM_SUFFIX = ".litertlm"

    /** First bytes of a valid `.litertlm` bundle. */
    val LITERTLM_MAGIC: ByteArray = "LITERTLM".toByteArray(Charsets.US_ASCII)

    /** First bytes, at offset 4, of a MediaPipe/TFLite `.task` bundle. */
    val TFLITE_MAGIC: ByteArray = "TFL3".toByteArray(Charsets.US_ASCII)
    const val TFLITE_MAGIC_OFFSET = 4

    private const val GB = 1024L * 1024L * 1024L

    /** Default: smallest Gemma 4 that still answers legal questions well. */
    val GEMMA_4_E2B = NyayaModel(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B (recommended)",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
            "resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadBytes = 2_588_147_712L,
        recommendedRamBytes = 4 * GB,
        summary = "2.4 GB download. Runs offline on most 4 GB+ phones."
    )

    /** Larger, noticeably stronger on multi-step reasoning; needs a better phone. */
    val GEMMA_4_E4B = NyayaModel(
        id = "gemma-4-e4b",
        displayName = "Gemma 4 E4B (higher quality)",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/" +
            "resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadBytes = 3_659_530_240L,
        recommendedRamBytes = 6 * GB,
        summary = "3.4 GB download. Better answers, needs 6 GB+ RAM."
    )

    val all: List<NyayaModel> = listOf(GEMMA_4_E2B, GEMMA_4_E4B)

    val default: NyayaModel = GEMMA_4_E2B

    fun byId(id: String): NyayaModel? = all.firstOrNull { it.id == id }

    fun byUrl(url: String): NyayaModel? = all.firstOrNull { it.url == url }

    /**
     * Local file name for an arbitrary model URL. Catalog entries keep their
     * canonical name; a user-supplied URL falls back to its last path segment.
     */
    fun fileNameForUrl(url: String): String {
        byUrl(url)?.let { return it.fileName }
        val guess = url.substringAfterLast('/').substringBefore('?')
        return if (guess.isBlank()) "nyaya-model$LITERTLM_SUFFIX" else guess
    }

    /** Exact expected size for a catalog URL, or null when it is unknown. */
    fun expectedBytesForUrl(url: String): Long? = byUrl(url)?.downloadBytes
}
