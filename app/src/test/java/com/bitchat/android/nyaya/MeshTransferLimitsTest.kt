package com.bitchat.android.nyaya

import com.bitchat.android.nyaya.transfer.MeshTransferLimits
import com.bitchat.android.util.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the honest transfer-limit arithmetic to the receiver-side caps in
 * AppConstants.Fragmentation. These tests exist so that the fictional-limit
 * bug (sender accepts 50 MB, receiver reassembles ~100 KB, user sees silent
 * failure) can never be quietly reintroduced by a constant change.
 */
class MeshTransferLimitsTest {

    @Test
    fun perFragmentCapacity_isPositive_andNeverExceedsIosMaxFragmentSize() {
        assertTrue(MeshTransferLimits.perFragmentDataBytes > 0)
        assertTrue(
            "per-fragment data must respect the iOS-compatible max fragment size",
            MeshTransferLimits.perFragmentDataBytes <= AppConstants.Fragmentation.MAX_FRAGMENT_SIZE
        )
    }

    @Test
    fun deliverableBytes_respectBothReceiverCaps() {
        assertTrue(
            "must respect the fragment-count cap",
            MeshTransferLimits.maxDeliverableEncodedBytes <=
                AppConstants.Fragmentation.MAX_FRAGMENTS_PER_ID * AppConstants.Fragmentation.MAX_FRAGMENT_SIZE
        )
        assertTrue(
            "must respect the per-set cumulative byte cap",
            MeshTransferLimits.maxDeliverableEncodedBytes <= AppConstants.Fragmentation.MAX_FRAGMENT_TOTAL_BYTES
        )
    }

    @Test
    fun deliverableBytes_matchWorstCaseArithmetic() {
        // v2 header (15) + sender (8) + recipient (8) + route (1 + 7*8 = 57)
        // + fragment header (13) + padding buffer (16) = 117 bytes of overhead
        // within the 512-byte MTU budget -> 395 usable bytes per fragment.
        val expectedPerFragment = (512 - 117).coerceAtMost(AppConstants.Fragmentation.MAX_FRAGMENT_SIZE)
        assertEquals(expectedPerFragment, MeshTransferLimits.perFragmentDataBytes)
        assertEquals(
            minOf(
                AppConstants.Fragmentation.MAX_FRAGMENTS_PER_ID * expectedPerFragment,
                AppConstants.Fragmentation.MAX_FRAGMENT_TOTAL_BYTES
            ),
            MeshTransferLimits.maxDeliverableEncodedBytes
        )
    }

    @Test
    fun fileLimit_isHonest_farBelowTheFictional50Mb_butStillUseful() {
        val limit = MeshTransferLimits.maxFileContentBytes("evidence.pdf", "application/pdf")
        assertTrue(
            "the honest limit must be far below the old fictional 50 MB cap",
            limit < AppConstants.Media.MAX_FILE_SIZE_BYTES / 100
        )
        assertTrue(
            "the mesh should still deliver at least 64 KB",
            limit >= 64L * 1024L
        )
    }

    @Test
    fun longerMetadata_reducesTheContentBudget() {
        val short = MeshTransferLimits.maxFileContentBytes("a.pdf", "application/pdf")
        val long = MeshTransferLimits.maxFileContentBytes(
            "a-very-long-descriptive-file-name-for-a-legal-document-scan.pdf",
            "application/pdf"
        )
        assertTrue(long < short)
    }

    @Test
    fun absurdMetadata_neverProducesANegativeLimit() {
        val limit = MeshTransferLimits.maxFileContentBytes("x".repeat(200_000), "application/octet-stream")
        assertEquals(0L, limit)
    }

    @Test
    fun formatBytes_isHumanReadable() {
        assertEquals("512 B", MeshTransferLimits.formatBytes(512))
        assertEquals("100 KB", MeshTransferLimits.formatBytes(102_400))
        assertEquals("1.5 MB", MeshTransferLimits.formatBytes(1_572_864))
    }
}
