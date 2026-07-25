package com.bitchat.android.nyaya.transfer.bulk

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Offer metadata for a bulk file transfer.
 *
 * The offer travels inside the peer's established Noise session (mutually
 * authenticated, end-to-end encrypted). It carries a fresh one-time AES-256
 * key that encrypts every subsequent frame of this transfer, and the SHA-256
 * of the file so the receiver can verify integrity end to end.
 */
class BulkOffer(
    val transferId: ByteArray,
    val fileSha256: ByteArray,
    val fileSize: Long,
    val chunkSize: Int,
    val chunkCount: Int,
    val key: ByteArray,
    val fileName: String,
    val mimeType: String
)

/**
 * Wire format for the Nyaya bulk file channel (Android ↔ Android fast path).
 *
 * Frames ride the existing Wi-Fi Aware framed TCP socket alongside regular
 * BitchatPackets. They start with a magic header that no BitchatPacket can
 * produce, so builds that do not understand the channel silently skip them
 * (their packet parser rejects the unknown version byte) — full backward and
 * iOS compatibility is preserved because the BLE wire format is untouched.
 *
 * Layouts:
 * - OFFER frame:      MAGIC(4) | type(1) | noiseCiphertext           (Noise session encrypted)
 * - all other frames: MAGIC(4) | type(1) | transferId(32) | nonce(12) | AES-256-GCM ciphertext
 *
 * The GCM AAD binds MAGIC + type + transferId, so a frame cannot be replayed
 * as a different type or against a different transfer. Transfer IDs are
 * random (not file hashes), so nothing about the file leaks outside the
 * encrypted OFFER.
 */
object BulkFrames {

    /** "NYB1" — also an invalid BitchatPacket version byte, so old builds skip these frames. */
    val MAGIC = byteArrayOf(0x4E, 0x59, 0x42, 0x31)

    const val TYPE_OFFER: Byte = 0x01
    const val TYPE_ACCEPT: Byte = 0x02
    const val TYPE_DECLINE: Byte = 0x03
    const val TYPE_CHUNK: Byte = 0x04
    const val TYPE_ACK: Byte = 0x05
    const val TYPE_COMPLETE: Byte = 0x06
    const val TYPE_CANCEL: Byte = 0x07

    const val TRANSFER_ID_BYTES = 32
    const val FILE_HASH_BYTES = 32
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_STRING_BYTES = 1024

    /**
     * Plaintext chunk size: 48 KiB. Ciphertext + frame header stays comfortably
     * under the socket layer's 64 KB frame cap, and each chunk is far below the
     * AES-GCM per-message limits.
     */
    const val CHUNK_SIZE = 49_152

    /** Sanity cap for a single transfer over the fast path. */
    const val MAX_FILE_BYTES = 256L * 1024L * 1024L

    private val random = SecureRandom()

    private val ENCRYPTED_HEADER_BYTES = MAGIC.size + 1 + TRANSFER_ID_BYTES

    fun isBulkFrame(raw: ByteArray): Boolean {
        if (raw.size <= MAGIC.size + 1) return false
        for (i in MAGIC.indices) {
            if (raw[i] != MAGIC[i]) return false
        }
        return true
    }

    fun frameType(raw: ByteArray): Byte = raw[MAGIC.size]

    fun offerFrame(noiseCiphertext: ByteArray): ByteArray {
        return ByteBuffer.allocate(MAGIC.size + 1 + noiseCiphertext.size)
            .put(MAGIC)
            .put(TYPE_OFFER)
            .put(noiseCiphertext)
            .array()
    }

    fun offerCiphertext(raw: ByteArray): ByteArray =
        raw.copyOfRange(MAGIC.size + 1, raw.size)

    /** Transfer ID of a non-OFFER frame, or null if the frame is too short. */
    fun frameTransferId(raw: ByteArray): ByteArray? {
        if (raw.size <= ENCRYPTED_HEADER_BYTES + NONCE_BYTES) return null
        return raw.copyOfRange(MAGIC.size + 1, ENCRYPTED_HEADER_BYTES)
    }

    /** Builds an AES-256-GCM encrypted frame with type + transferId bound as AAD. */
    fun encryptedFrame(type: Byte, transferId: ByteArray, key: ByteArray, plaintext: ByteArray): ByteArray {
        require(transferId.size == TRANSFER_ID_BYTES) { "transferId must be $TRANSFER_ID_BYTES bytes" }
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aadFor(type, transferId))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(ENCRYPTED_HEADER_BYTES + NONCE_BYTES + ciphertext.size)
            .put(MAGIC)
            .put(type)
            .put(transferId)
            .put(nonce)
            .put(ciphertext)
            .array()
    }

    /** Decrypts an encrypted frame. Returns null on any tampering, truncation, or wrong key. */
    fun openEncryptedFrame(raw: ByteArray, key: ByteArray): ByteArray? {
        if (raw.size <= ENCRYPTED_HEADER_BYTES + NONCE_BYTES) return null
        return try {
            val type = frameType(raw)
            val transferId = raw.copyOfRange(MAGIC.size + 1, ENCRYPTED_HEADER_BYTES)
            val nonce = raw.copyOfRange(ENCRYPTED_HEADER_BYTES, ENCRYPTED_HEADER_BYTES + NONCE_BYTES)
            val ciphertext = raw.copyOfRange(ENCRYPTED_HEADER_BYTES + NONCE_BYTES, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aadFor(type, transferId))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun aadFor(type: Byte, transferId: ByteArray): ByteArray {
        return ByteBuffer.allocate(MAGIC.size + 1 + TRANSFER_ID_BYTES)
            .put(MAGIC)
            .put(type)
            .put(transferId)
            .array()
    }

    fun newTransferId(): ByteArray = ByteArray(TRANSFER_ID_BYTES).also { random.nextBytes(it) }

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun chunkCountFor(fileSize: Long, chunkSize: Int): Int {
        require(fileSize > 0) { "fileSize must be positive" }
        require(chunkSize > 0) { "chunkSize must be positive" }
        return ((fileSize + chunkSize - 1) / chunkSize).toInt()
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    // MARK: - Payload codecs (all payloads are the plaintext INSIDE the encryption)

    fun encodeOffer(offer: BulkOffer): ByteArray {
        require(offer.transferId.size == TRANSFER_ID_BYTES)
        require(offer.fileSha256.size == FILE_HASH_BYTES)
        require(offer.key.size == KEY_BYTES)
        val name = offer.fileName.toByteArray(Charsets.UTF_8)
        val mime = offer.mimeType.toByteArray(Charsets.UTF_8)
        require(name.size in 1..MAX_STRING_BYTES) { "file name too long" }
        require(mime.size in 1..MAX_STRING_BYTES) { "mime type too long" }
        return ByteBuffer.allocate(
            TRANSFER_ID_BYTES + FILE_HASH_BYTES + 8 + 4 + 4 + KEY_BYTES + 2 + name.size + 2 + mime.size
        )
            .put(offer.transferId)
            .put(offer.fileSha256)
            .putLong(offer.fileSize)
            .putInt(offer.chunkSize)
            .putInt(offer.chunkCount)
            .put(offer.key)
            .putShort(name.size.toShort())
            .put(name)
            .putShort(mime.size.toShort())
            .put(mime)
            .array()
    }

    fun decodeOffer(payload: ByteArray): BulkOffer? {
        return try {
            val buf = ByteBuffer.wrap(payload)
            val transferId = ByteArray(TRANSFER_ID_BYTES).also { buf.get(it) }
            val fileSha256 = ByteArray(FILE_HASH_BYTES).also { buf.get(it) }
            val fileSize = buf.long
            val chunkSize = buf.int
            val chunkCount = buf.int
            val key = ByteArray(KEY_BYTES).also { buf.get(it) }
            val nameLen = buf.short.toInt() and 0xFFFF
            if (nameLen == 0 || nameLen > MAX_STRING_BYTES || buf.remaining() < nameLen) return null
            val name = ByteArray(nameLen).also { buf.get(it) }
            val mimeLen = buf.short.toInt() and 0xFFFF
            if (mimeLen == 0 || mimeLen > MAX_STRING_BYTES || buf.remaining() < mimeLen) return null
            val mime = ByteArray(mimeLen).also { buf.get(it) }
            BulkOffer(
                transferId = transferId,
                fileSha256 = fileSha256,
                fileSize = fileSize,
                chunkSize = chunkSize,
                chunkCount = chunkCount,
                key = key,
                fileName = String(name, Charsets.UTF_8),
                mimeType = String(mime, Charsets.UTF_8)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeCount(count: Int): ByteArray = ByteBuffer.allocate(4).putInt(count).array()

    fun decodeCount(payload: ByteArray): Int? =
        if (payload.size < 4) null else ByteBuffer.wrap(payload).int

    fun encodeChunk(index: Int, data: ByteArray, length: Int): ByteArray {
        require(length in 0..data.size)
        return ByteBuffer.allocate(4 + length).putInt(index).put(data, 0, length).array()
    }

    fun decodeChunk(payload: ByteArray): Pair<Int, ByteArray>? {
        if (payload.size < 5) return null
        val index = ByteBuffer.wrap(payload, 0, 4).int
        if (index < 0) return null
        return index to payload.copyOfRange(4, payload.size)
    }

    fun encodeReason(reason: String): ByteArray {
        val bytes = reason.toByteArray(Charsets.UTF_8).let {
            if (it.size > MAX_STRING_BYTES) it.copyOf(MAX_STRING_BYTES) else it
        }
        return ByteBuffer.allocate(2 + bytes.size).putShort(bytes.size.toShort()).put(bytes).array()
    }

    fun decodeReason(payload: ByteArray): String? {
        if (payload.size < 2) return null
        val len = ByteBuffer.wrap(payload, 0, 2).short.toInt() and 0xFFFF
        if (len > MAX_STRING_BYTES || payload.size < 2 + len) return null
        return String(payload, 2, len, Charsets.UTF_8)
    }

    fun encodeComplete(ok: Boolean, message: String): ByteArray {
        val bytes = message.toByteArray(Charsets.UTF_8).let {
            if (it.size > MAX_STRING_BYTES) it.copyOf(MAX_STRING_BYTES) else it
        }
        return ByteBuffer.allocate(1 + 2 + bytes.size)
            .put(if (ok) 1.toByte() else 0.toByte())
            .putShort(bytes.size.toShort())
            .put(bytes)
            .array()
    }

    fun decodeComplete(payload: ByteArray): Pair<Boolean, String>? {
        if (payload.size < 3) return null
        val ok = payload[0] == 1.toByte()
        val len = ByteBuffer.wrap(payload, 1, 2).short.toInt() and 0xFFFF
        if (len > MAX_STRING_BYTES || payload.size < 3 + len) return null
        return ok to String(payload, 3, len, Charsets.UTF_8)
    }
}
