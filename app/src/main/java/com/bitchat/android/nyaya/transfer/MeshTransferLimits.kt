package com.bitchat.android.nyaya.transfer

import com.bitchat.android.util.AppConstants

/**
 * Single source of truth for how large a file the BLE mesh can ACTUALLY deliver.
 *
 * Why this exists: bitchat's receiver-side FragmentManager enforces hard caps on
 * reassembly — at most [AppConstants.Fragmentation.MAX_FRAGMENTS_PER_ID] fragments
 * per transfer and at most [AppConstants.Fragmentation.MAX_FRAGMENT_TOTAL_BYTES]
 * cumulative bytes per set. Those caps are deliberate: they protect every relaying
 * phone from being OOM-killed by a malicious or oversized transfer, so they must
 * NOT be raised. But the sender used to validate files against a 50 MB constant
 * that nothing downstream could honour, which made large sends fail silently.
 *
 * This object derives the honest, guaranteed-deliverable size from the very same
 * constants the receiver enforces. If the fragmentation constants ever change,
 * the user-facing limit follows automatically.
 *
 * The computation is worst-case on purpose: a file accepted here is deliverable
 * regardless of whether the packet ends up private (recipient ID present),
 * source-routed (up to [AppConstants.MESSAGE_TTL_HOPS] hops), or broadcast.
 */
object MeshTransferLimits {

    /** MTU budget used by FragmentManager when sizing fragments (matches iOS). */
    private const val MTU_BUDGET_BYTES = 512

    /** BitchatPacket v2 header (v1 is 13; assume the larger v2 for worst case). */
    private const val V2_HEADER_BYTES = 15

    private const val SENDER_ID_BYTES = 8
    private const val RECIPIENT_ID_BYTES = 8

    /** FragmentPayload header prepended to every fragment. */
    private const val FRAGMENT_HEADER_BYTES = 13

    /** MessagePadding.optimalBlockSize overhead reserved by FragmentManager. */
    private const val PADDING_BUFFER_BYTES = 16

    /** Worst-case source route: 1 count byte + 8 bytes per hop at max TTL. */
    private val WORST_CASE_ROUTE_BYTES = 1 + AppConstants.MESSAGE_TTL_HOPS.toInt() * 8

    /**
     * Worst-case usable data bytes per fragment, mirroring the dynamic
     * computation in FragmentManager.createFragments().
     */
    val perFragmentDataBytes: Int =
        (MTU_BUDGET_BYTES - (V2_HEADER_BYTES + SENDER_ID_BYTES + RECIPIENT_ID_BYTES +
            WORST_CASE_ROUTE_BYTES + FRAGMENT_HEADER_BYTES + PADDING_BUFFER_BYTES))
            .coerceAtMost(AppConstants.Fragmentation.MAX_FRAGMENT_SIZE)

    /**
     * Largest encoded (unpadded) packet the receiver is guaranteed to reassemble:
     * bounded both by the fragment-count cap and the per-set cumulative byte cap.
     */
    val maxDeliverableEncodedBytes: Int = minOf(
        AppConstants.Fragmentation.MAX_FRAGMENTS_PER_ID * perFragmentDataBytes,
        AppConstants.Fragmentation.MAX_FRAGMENT_TOTAL_BYTES
    )

    /**
     * Fixed allowance for everything wrapped around the raw file bytes inside the
     * encoded packet: the outer BitchatPacket fields, Noise encryption overhead
     * (per-chunk MACs and framing), and the BitchatFilePacket TLV headers
     * (FILE_SIZE, CONTENT length fields, TLV type/length bytes). Deliberately
     * generous so acceptance here guarantees deliverability.
     */
    private const val ENVELOPE_ALLOWANCE_BYTES = 1024

    /**
     * Maximum raw file content (in bytes) that can be sent with the given file
     * name and MIME type. Never negative, even for absurd metadata.
     */
    fun maxFileContentBytes(fileName: String, mimeType: String): Long {
        val nameBytes = fileName.toByteArray(Charsets.UTF_8).size
        val mimeBytes = mimeType.toByteArray(Charsets.UTF_8).size
        return (maxDeliverableEncodedBytes.toLong()
            - ENVELOPE_ALLOWANCE_BYTES.toLong()
            - nameBytes.toLong()
            - mimeBytes.toLong())
            .coerceAtLeast(0L)
    }

    /** Human-readable size for user-facing messages, e.g. "97 KB", "1.5 MB". */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> "${bytes / 1_024} KB"
        else -> "$bytes B"
    }
}
