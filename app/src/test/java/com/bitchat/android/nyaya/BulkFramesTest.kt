package com.bitchat.android.nyaya

import com.bitchat.android.nyaya.transfer.bulk.BulkFrames
import com.bitchat.android.nyaya.transfer.bulk.BulkOffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the bulk-transfer wire format and its authenticated encryption.
 * Everything here runs on the plain JVM — no Android dependencies.
 */
class BulkFramesTest {

    private fun sampleOffer(): BulkOffer = BulkOffer(
        transferId = BulkFrames.newTransferId(),
        fileSha256 = ByteArray(32) { it.toByte() },
        fileSize = 5_000_000L,
        chunkSize = BulkFrames.CHUNK_SIZE,
        chunkCount = BulkFrames.chunkCountFor(5_000_000L, BulkFrames.CHUNK_SIZE),
        key = BulkFrames.newKey(),
        fileName = "सबूत-photo.jpg",
        mimeType = "image/jpeg"
    )

    // MARK: - Frame detection

    @Test
    fun bulkFrames_areDetected_andForeignDataIsNot() {
        val offerFrame = BulkFrames.offerFrame(ByteArray(64) { 7 })
        assertTrue(BulkFrames.isBulkFrame(offerFrame))
        assertEquals(BulkFrames.TYPE_OFFER, BulkFrames.frameType(offerFrame))

        // A BitchatPacket starts with a small version byte, never the magic.
        assertFalse(BulkFrames.isBulkFrame(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertFalse(BulkFrames.isBulkFrame(ByteArray(0)))
        assertFalse(BulkFrames.isBulkFrame(byteArrayOf(0x4E, 0x59)))
        // Magic alone without a payload is not a valid frame.
        assertFalse(BulkFrames.isBulkFrame(byteArrayOf(0x4E, 0x59, 0x42, 0x31)))
    }

    @Test
    fun offerFrame_roundTripsItsCiphertext() {
        val ciphertext = ByteArray(300) { (it % 251).toByte() }
        val frame = BulkFrames.offerFrame(ciphertext)
        assertArrayEquals(ciphertext, BulkFrames.offerCiphertext(frame))
    }

    // MARK: - Authenticated encryption

    @Test
    fun encryptedFrame_roundTrips_andCarriesTransferId() {
        val key = BulkFrames.newKey()
        val tid = BulkFrames.newTransferId()
        val plaintext = "hello nyaya".toByteArray()
        val frame = BulkFrames.encryptedFrame(BulkFrames.TYPE_ACK, tid, key, plaintext)

        assertTrue(BulkFrames.isBulkFrame(frame))
        assertEquals(BulkFrames.TYPE_ACK, BulkFrames.frameType(frame))
        assertArrayEquals(tid, BulkFrames.frameTransferId(frame))
        assertArrayEquals(plaintext, BulkFrames.openEncryptedFrame(frame, key))
    }

    @Test
    fun tamperedFrame_failsClosed() {
        val key = BulkFrames.newKey()
        val frame = BulkFrames.encryptedFrame(
            BulkFrames.TYPE_CHUNK, BulkFrames.newTransferId(), key, ByteArray(100) { 3 }
        )
        val tampered = frame.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 1).toByte()
        assertNull(BulkFrames.openEncryptedFrame(tampered, key))
    }

    @Test
    fun wrongKey_failsClosed() {
        val frame = BulkFrames.encryptedFrame(
            BulkFrames.TYPE_CHUNK, BulkFrames.newTransferId(), BulkFrames.newKey(), ByteArray(10)
        )
        assertNull(BulkFrames.openEncryptedFrame(frame, BulkFrames.newKey()))
    }

    @Test
    fun typeConfusion_failsClosed_becauseTypeIsBoundAsAad() {
        val key = BulkFrames.newKey()
        val frame = BulkFrames.encryptedFrame(
            BulkFrames.TYPE_ACK, BulkFrames.newTransferId(), key, BulkFrames.encodeCount(5)
        )
        // Rewriting an ACK as a COMPLETE must not decrypt.
        val confused = frame.copyOf()
        confused[BulkFrames.MAGIC.size] = BulkFrames.TYPE_COMPLETE
        assertNull(BulkFrames.openEncryptedFrame(confused, key))
    }

    @Test
    fun transferConfusion_failsClosed_becauseTransferIdIsBoundAsAad() {
        val key = BulkFrames.newKey()
        val frame = BulkFrames.encryptedFrame(
            BulkFrames.TYPE_ACK, BulkFrames.newTransferId(), key, BulkFrames.encodeCount(5)
        )
        // Redirecting the frame at another transfer must not decrypt.
        val confused = frame.copyOf()
        confused[BulkFrames.MAGIC.size + 1] = (confused[BulkFrames.MAGIC.size + 1].toInt() xor 1).toByte()
        assertNull(BulkFrames.openEncryptedFrame(confused, key))
    }

    // MARK: - Codecs

    @Test
    fun offer_roundTrips_includingDevanagariFileName() {
        val offer = sampleOffer()
        val decoded = BulkFrames.decodeOffer(BulkFrames.encodeOffer(offer))
        assertNotNull(decoded)
        decoded!!
        assertArrayEquals(offer.transferId, decoded.transferId)
        assertArrayEquals(offer.fileSha256, decoded.fileSha256)
        assertEquals(offer.fileSize, decoded.fileSize)
        assertEquals(offer.chunkSize, decoded.chunkSize)
        assertEquals(offer.chunkCount, decoded.chunkCount)
        assertArrayEquals(offer.key, decoded.key)
        assertEquals("सबूत-photo.jpg", decoded.fileName)
        assertEquals("image/jpeg", decoded.mimeType)
    }

    @Test
    fun truncatedOffer_decodesToNull() {
        val encoded = BulkFrames.encodeOffer(sampleOffer())
        for (cut in intArrayOf(0, 10, 32, 60, encoded.size - 1)) {
            assertNull("cut at $cut should fail", BulkFrames.decodeOffer(encoded.copyOf(cut)))
        }
    }

    @Test
    fun chunk_roundTrips() {
        val data = ByteArray(1000) { (it % 127).toByte() }
        val payload = BulkFrames.encodeChunk(42, data, 1000)
        val (index, decoded) = BulkFrames.decodeChunk(payload)!!
        assertEquals(42, index)
        assertArrayEquals(data, decoded)
        assertNull(BulkFrames.decodeChunk(ByteArray(4)))
    }

    @Test
    fun countReasonAndComplete_roundTrip() {
        assertEquals(1234, BulkFrames.decodeCount(BulkFrames.encodeCount(1234)))
        assertNull(BulkFrames.decodeCount(ByteArray(3)))

        assertEquals("not enough storage", BulkFrames.decodeReason(BulkFrames.encodeReason("not enough storage")))

        val (ok, message) = BulkFrames.decodeComplete(BulkFrames.encodeComplete(true, "ok"))!!
        assertTrue(ok)
        assertEquals("ok", message)
        val (bad, why) = BulkFrames.decodeComplete(BulkFrames.encodeComplete(false, "hash mismatch"))!!
        assertFalse(bad)
        assertEquals("hash mismatch", why)
    }

    // MARK: - Arithmetic and budgets

    @Test
    fun chunkCount_math() {
        assertEquals(1, BulkFrames.chunkCountFor(1, BulkFrames.CHUNK_SIZE))
        assertEquals(1, BulkFrames.chunkCountFor(BulkFrames.CHUNK_SIZE.toLong(), BulkFrames.CHUNK_SIZE))
        assertEquals(2, BulkFrames.chunkCountFor(BulkFrames.CHUNK_SIZE.toLong() + 1, BulkFrames.CHUNK_SIZE))
        val hundredMb = 100L * 1024 * 1024
        val count = BulkFrames.chunkCountFor(hundredMb, BulkFrames.CHUNK_SIZE)
        assertEquals(((hundredMb + BulkFrames.CHUNK_SIZE - 1) / BulkFrames.CHUNK_SIZE).toInt(), count)
    }

    @Test
    fun largestChunkFrame_staysUnderTheSocketFrameCap() {
        val key = BulkFrames.newKey()
        val payload = BulkFrames.encodeChunk(0, ByteArray(BulkFrames.CHUNK_SIZE), BulkFrames.CHUNK_SIZE)
        val frame = BulkFrames.encryptedFrame(BulkFrames.TYPE_CHUNK, BulkFrames.newTransferId(), key, payload)
        assertTrue(
            "chunk frame (${frame.size} bytes) must stay under the 64 KB socket frame cap",
            frame.size < 64 * 1024
        )
    }

    @Test
    fun randomIdsAndKeys_haveCorrectSizeAndAreNotRepeated() {
        val a = BulkFrames.newTransferId()
        val b = BulkFrames.newTransferId()
        assertEquals(BulkFrames.TRANSFER_ID_BYTES, a.size)
        assertFalse(a.contentEquals(b))
        val k1 = BulkFrames.newKey()
        val k2 = BulkFrames.newKey()
        assertEquals(BulkFrames.KEY_BYTES, k1.size)
        assertFalse(k1.contentEquals(k2))
    }
}
