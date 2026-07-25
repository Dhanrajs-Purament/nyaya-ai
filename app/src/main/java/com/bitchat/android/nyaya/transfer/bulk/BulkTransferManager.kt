package com.bitchat.android.nyaya.transfer.bulk

import android.util.Log
import com.bitchat.android.mesh.TransferProgressManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Sender/receiver state machines for the Nyaya bulk file channel.
 *
 * Security model:
 * - The OFFER travels inside the peer's established, mutually authenticated
 *   Noise session and carries a fresh one-time AES-256 transfer key.
 * - Every other frame is AES-256-GCM encrypted with that key; the frame type
 *   and transfer ID are bound as AAD, so frames fail closed on any confusion.
 * - The receiver verifies the file's full SHA-256 (carried in the OFFER)
 *   before accepting the file. A flipped bit anywhere fails the transfer.
 *
 * Reliability model:
 * - Chunks are sent strictly in order over the reliable framed TCP socket.
 * - The receiver acks progress periodically; the sender's progress UI is
 *   driven by those acks (true delivery, not just "bytes written").
 * - A re-OFFER of a known transfer resumes from the receiver's chunk count.
 * - Failures are reported with a reason instead of stalling silently.
 *
 * Memory model: files are streamed from/to disk in CHUNK_SIZE pieces. The
 * whole file is never held in RAM, so a 100 MB send cannot OOM the phone.
 */
class BulkTransferManager(
    private val scope: CoroutineScope,
    private val incomingDirProvider: () -> File,
    private val noiseEncrypt: (ByteArray, String) -> ByteArray?,
    private val noiseDecrypt: (ByteArray, String) -> ByteArray?,
    private val writeFrame: (String, ByteArray) -> Boolean,
    private val onFileReceived: (peerID: String, filePath: String, fileName: String, mimeType: String) -> Unit,
    private val onTransferFailed: (peerID: String, transferIdHex: String, reason: String) -> Unit
) {
    companion object {
        private const val TAG = "BulkTransferManager"
        private const val ACCEPT_TIMEOUT_MS = 30_000L
        private const val COMPLETE_TIMEOUT_MS = 60_000L
        private const val INCOMING_IDLE_TIMEOUT_MS = 120_000L
        private const val SWEEP_INTERVAL_MS = 30_000L
        private const val ACK_EVERY_CHUNKS = 16
        private const val MIN_FREE_SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val CANCELLED_REASON = "cancelled"
    }

    private class OutgoingTransfer(
        val peerID: String,
        val file: File,
        val transferId: ByteArray,
        val tidHex: String,
        val key: ByteArray,
        val chunkCount: Int
    ) {
        val accepted = CompletableDeferred<Int>()
        val completed = CompletableDeferred<Boolean>()
        @Volatile var cancelled = false
    }

    private class IncomingTransfer(
        val peerID: String,
        val offer: BulkOffer,
        val partFile: File,
        val raf: RandomAccessFile
    ) {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        var receivedChunks: Int = 0
        @Volatile var lastActivityAt: Long = System.currentTimeMillis()
        @Volatile var closed = false
    }

    private val outgoing = ConcurrentHashMap<String, OutgoingTransfer>()
    private val incoming = ConcurrentHashMap<String, IncomingTransfer>()

    init {
        scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MS)
                sweepStaleIncoming()
            }
        }
    }

    // MARK: - Sending

    /**
     * Starts sending a file. Returns the transfer ID (hex) used with
     * TransferProgressManager, or null if the transfer could not start.
     */
    fun sendFile(peerID: String, filePath: String, fileName: String, mimeType: String): String? {
        val file = File(filePath)
        if (!file.isFile) {
            Log.e(TAG, "sendFile: not a file: $filePath")
            return null
        }
        val fileSize = file.length()
        if (fileSize <= 0L || fileSize > BulkFrames.MAX_FILE_BYTES) {
            Log.e(TAG, "sendFile: size out of range: $fileSize")
            return null
        }

        val fileSha256 = try {
            sha256OfFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "sendFile: hashing failed: ${e.message}")
            return null
        }

        val transferId = BulkFrames.newTransferId()
        val tidHex = BulkFrames.toHex(transferId)
        val key = BulkFrames.newKey()
        val chunkCount = BulkFrames.chunkCountFor(fileSize, BulkFrames.CHUNK_SIZE)
        val offer = BulkOffer(
            transferId = transferId,
            fileSha256 = fileSha256,
            fileSize = fileSize,
            chunkSize = BulkFrames.CHUNK_SIZE,
            chunkCount = chunkCount,
            key = key,
            fileName = fileName.take(255).ifBlank { "file" },
            mimeType = mimeType.ifBlank { "application/octet-stream" }
        )

        val offerPlaintext = try {
            BulkFrames.encodeOffer(offer)
        } catch (e: Exception) {
            Log.e(TAG, "sendFile: offer encoding failed: ${e.message}")
            return null
        }
        val offerCiphertext = noiseEncrypt(offerPlaintext, peerID) ?: run {
            Log.e(TAG, "sendFile: Noise encryption unavailable for ${peerID.take(8)}")
            return null
        }

        val transfer = OutgoingTransfer(peerID, file, transferId, tidHex, key, chunkCount)
        outgoing[tidHex] = transfer

        if (!writeFrame(peerID, BulkFrames.offerFrame(offerCiphertext))) {
            outgoing.remove(tidHex)
            Log.e(TAG, "sendFile: offer write failed for ${peerID.take(8)}")
            return null
        }

        TransferProgressManager.start(tidHex, chunkCount)
        scope.launch { runOutgoing(transfer) }
        Log.i(TAG, "🚀 Bulk send started: ${fileSize} bytes in $chunkCount chunks to ${peerID.take(8)} (tid=${tidHex.take(16)}…)")
        return tidHex
    }

    private suspend fun runOutgoing(t: OutgoingTransfer) {
        try {
            val resumeFrom = withTimeout(ACCEPT_TIMEOUT_MS) { t.accepted.await() }
                .coerceIn(0, t.chunkCount)
            if (resumeFrom > 0) {
                Log.i(TAG, "Bulk send resuming from chunk $resumeFrom/${t.chunkCount}")
            }

            RandomAccessFile(t.file, "r").use { raf ->
                val buffer = ByteArray(BulkFrames.CHUNK_SIZE)
                val fileSize = t.file.length()
                var index = resumeFrom
                raf.seek(index.toLong() * BulkFrames.CHUNK_SIZE)
                while (index < t.chunkCount) {
                    if (t.cancelled) throw IOException(CANCELLED_REASON)
                    val offset = index.toLong() * BulkFrames.CHUNK_SIZE
                    val toRead = minOf(BulkFrames.CHUNK_SIZE.toLong(), fileSize - offset).toInt()
                    if (toRead <= 0) throw IOException("file changed during transfer")
                    raf.readFully(buffer, 0, toRead)
                    val payload = BulkFrames.encodeChunk(index, buffer, toRead)
                    val frame = BulkFrames.encryptedFrame(BulkFrames.TYPE_CHUNK, t.transferId, t.key, payload)
                    if (!writeFrame(t.peerID, frame)) throw IOException("connection lost")
                    index++
                }
            }

            val ok = withTimeout(COMPLETE_TIMEOUT_MS) { t.completed.await() }
            if (!ok) throw IOException("receiver could not verify the file")
            TransferProgressManager.progress(t.tidHex, t.chunkCount, t.chunkCount)
            TransferProgressManager.complete(t.tidHex, t.chunkCount)
            Log.i(TAG, "✅ Bulk send complete (tid=${t.tidHex.take(16)}…)")
        } catch (e: TimeoutCancellationException) {
            val reason = if (!t.accepted.isCompleted) {
                "no response — the recipient's app may not support fast Wi-Fi transfer yet"
            } else {
                "timed out waiting for delivery confirmation"
            }
            failOutgoing(t, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failOutgoing(t, e.message ?: "transfer failed")
        } finally {
            outgoing.remove(t.tidHex)
        }
    }

    private fun failOutgoing(t: OutgoingTransfer, reason: String) {
        Log.w(TAG, "❌ Bulk send failed (tid=${t.tidHex.take(16)}…): $reason")
        sendCancelFrame(t.peerID, t.transferId, t.key)
        if (reason != CANCELLED_REASON) {
            try {
                onTransferFailed(t.peerID, t.tidHex, reason)
            } catch (_: Exception) {
            }
        }
    }

    /** Cancels an in-flight outgoing transfer. Returns true if one was found. */
    fun cancelOutgoing(transferIdHex: String): Boolean {
        val t = outgoing[transferIdHex] ?: return false
        t.cancelled = true
        t.accepted.completeExceptionally(IOException(CANCELLED_REASON))
        t.completed.completeExceptionally(IOException(CANCELLED_REASON))
        return true
    }

    // MARK: - Frame dispatch (called from the socket read loop)

    fun onFrameReceived(peerID: String, raw: ByteArray) {
        try {
            when (BulkFrames.frameType(raw)) {
                BulkFrames.TYPE_OFFER -> handleOffer(peerID, raw)
                BulkFrames.TYPE_ACCEPT -> handleAccept(peerID, raw)
                BulkFrames.TYPE_DECLINE -> handleDecline(peerID, raw)
                BulkFrames.TYPE_CHUNK -> handleChunk(peerID, raw)
                BulkFrames.TYPE_ACK -> handleAck(peerID, raw)
                BulkFrames.TYPE_COMPLETE -> handleComplete(peerID, raw)
                BulkFrames.TYPE_CANCEL -> handleCancel(peerID, raw)
                else -> Log.w(TAG, "Unknown bulk frame type from ${peerID.take(8)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bulk frame handling error from ${peerID.take(8)}: ${e.message}")
        }
    }

    // MARK: - Receiving

    private fun handleOffer(peerID: String, raw: ByteArray) {
        val plaintext = noiseDecrypt(BulkFrames.offerCiphertext(raw), peerID) ?: run {
            Log.w(TAG, "OFFER from ${peerID.take(8)} failed Noise decryption; ignoring")
            return
        }
        val offer = BulkFrames.decodeOffer(plaintext) ?: run {
            Log.w(TAG, "OFFER from ${peerID.take(8)} malformed; ignoring")
            return
        }
        val tidHex = BulkFrames.toHex(offer.transferId)

        // Re-offer of a transfer we already track: re-accept with our resume point.
        incoming[tidHex]?.let { existing ->
            if (existing.peerID == peerID) {
                sendControl(peerID, BulkFrames.TYPE_ACCEPT, existing.offer,
                    BulkFrames.encodeCount(existing.receivedChunks))
            }
            return
        }

        // Validate the offer before touching disk.
        if (offer.fileSize <= 0L || offer.fileSize > BulkFrames.MAX_FILE_BYTES) {
            sendControl(peerID, BulkFrames.TYPE_DECLINE, offer, BulkFrames.encodeReason("file too large"))
            return
        }
        if (offer.chunkSize !in 1024..BulkFrames.CHUNK_SIZE) {
            sendControl(peerID, BulkFrames.TYPE_DECLINE, offer, BulkFrames.encodeReason("unsupported chunk size"))
            return
        }
        if (offer.chunkCount != BulkFrames.chunkCountFor(offer.fileSize, offer.chunkSize)) {
            sendControl(peerID, BulkFrames.TYPE_DECLINE, offer, BulkFrames.encodeReason("inconsistent chunk count"))
            return
        }

        val dir = incomingDirProvider()
        try {
            dir.mkdirs()
            if (dir.usableSpace < offer.fileSize + MIN_FREE_SPACE_MARGIN_BYTES) {
                sendControl(peerID, BulkFrames.TYPE_DECLINE, offer, BulkFrames.encodeReason("not enough storage on the receiving phone"))
                return
            }
            val partFile = File(dir, "$tidHex.part")
            val raf = RandomAccessFile(partFile, "rw")
            raf.setLength(0)
            val transfer = IncomingTransfer(peerID, offer, partFile, raf)
            incoming[tidHex] = transfer
            sendControl(peerID, BulkFrames.TYPE_ACCEPT, offer, BulkFrames.encodeCount(0))
            Log.i(TAG, "📥 Bulk receive accepted: '${offer.fileName}' ${offer.fileSize} bytes from ${peerID.take(8)}")
        } catch (e: Exception) {
            Log.e(TAG, "OFFER accept failed: ${e.message}")
            sendControl(peerID, BulkFrames.TYPE_DECLINE, offer, BulkFrames.encodeReason("receiver storage error"))
        }
    }

    private fun handleChunk(peerID: String, raw: ByteArray) {
        val tidHex = BulkFrames.frameTransferId(raw)?.let { BulkFrames.toHex(it) } ?: return
        val t = incoming[tidHex] ?: return
        if (t.peerID != peerID) return

        val plaintext = BulkFrames.openEncryptedFrame(raw, t.offer.key) ?: run {
            failIncoming(t, tidHex, "chunk decryption failed")
            return
        }
        val (index, data) = BulkFrames.decodeChunk(plaintext) ?: run {
            failIncoming(t, tidHex, "malformed chunk")
            return
        }

        synchronized(t) {
            if (t.closed) return
            when {
                index < t.receivedChunks -> return // duplicate; ignore
                index > t.receivedChunks -> {
                    failIncoming(t, tidHex, "out-of-order chunk")
                    return
                }
            }
            val isLast = index == t.offer.chunkCount - 1
            val expectedSize = if (isLast) {
                (t.offer.fileSize - (t.offer.chunkCount - 1).toLong() * t.offer.chunkSize).toInt()
            } else {
                t.offer.chunkSize
            }
            if (data.size != expectedSize) {
                failIncoming(t, tidHex, "chunk size mismatch")
                return
            }
            try {
                t.raf.seek(index.toLong() * t.offer.chunkSize)
                t.raf.write(data)
            } catch (e: IOException) {
                failIncoming(t, tidHex, "write failed: ${e.message}")
                return
            }
            t.digest.update(data)
            t.receivedChunks++
            t.lastActivityAt = System.currentTimeMillis()

            if (!isLast && t.receivedChunks % ACK_EVERY_CHUNKS == 0) {
                sendControl(peerID, BulkFrames.TYPE_ACK, t.offer, BulkFrames.encodeCount(t.receivedChunks))
            }
            if (isLast) {
                finishIncoming(t, tidHex)
            }
        }
    }

    private fun finishIncoming(t: IncomingTransfer, tidHex: String) {
        t.closed = true
        incoming.remove(tidHex)
        try {
            t.raf.close()
        } catch (_: Exception) {
        }

        val actualSha = t.digest.digest()
        if (!MessageDigest.isEqual(actualSha, t.offer.fileSha256)) {
            Log.e(TAG, "❌ Bulk receive hash mismatch (tid=${tidHex.take(16)}…)")
            try { t.partFile.delete() } catch (_: Exception) { }
            sendControl(t.peerID, BulkFrames.TYPE_COMPLETE, t.offer, BulkFrames.encodeComplete(false, "hash mismatch"))
            return
        }

        val safeName = t.offer.fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .take(200)
            .ifBlank { "file" }
        val finalFile = File(t.partFile.parentFile, "recv_${System.currentTimeMillis()}_$safeName")
        val moved = try {
            t.partFile.renameTo(finalFile) || run {
                FileInputStream(t.partFile).use { input -> finalFile.outputStream().use { input.copyTo(it) } }
                t.partFile.delete()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bulk receive finalize failed: ${e.message}")
            false
        }
        if (!moved) {
            try { t.partFile.delete() } catch (_: Exception) { }
            sendControl(t.peerID, BulkFrames.TYPE_COMPLETE, t.offer, BulkFrames.encodeComplete(false, "could not store file"))
            return
        }

        sendControl(t.peerID, BulkFrames.TYPE_ACK, t.offer, BulkFrames.encodeCount(t.offer.chunkCount))
        sendControl(t.peerID, BulkFrames.TYPE_COMPLETE, t.offer, BulkFrames.encodeComplete(true, "ok"))
        Log.i(TAG, "✅ Bulk receive complete: ${finalFile.name} (${t.offer.fileSize} bytes) from ${t.peerID.take(8)}")
        try {
            onFileReceived(t.peerID, finalFile.absolutePath, safeName, t.offer.mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "onFileReceived callback failed: ${e.message}")
        }
    }

    private fun failIncoming(t: IncomingTransfer, tidHex: String, reason: String) {
        Log.w(TAG, "❌ Bulk receive failed (tid=${tidHex.take(16)}…): $reason")
        synchronized(t) {
            if (t.closed) return
            t.closed = true
        }
        incoming.remove(tidHex)
        try { t.raf.close() } catch (_: Exception) { }
        try { t.partFile.delete() } catch (_: Exception) { }
        sendControl(t.peerID, BulkFrames.TYPE_DECLINE, t.offer, BulkFrames.encodeReason(reason))
    }

    // MARK: - Sender-side control frames

    private fun handleAccept(peerID: String, raw: ByteArray) {
        val t = outgoingFor(peerID, raw) ?: return
        val plaintext = BulkFrames.openEncryptedFrame(raw, t.key) ?: return
        val resumeFrom = BulkFrames.decodeCount(plaintext) ?: return
        t.accepted.complete(resumeFrom)
    }

    private fun handleDecline(peerID: String, raw: ByteArray) {
        val t = outgoingFor(peerID, raw) ?: return
        val plaintext = BulkFrames.openEncryptedFrame(raw, t.key) ?: return
        val reason = BulkFrames.decodeReason(plaintext) ?: "declined"
        val error = IOException("receiver declined: $reason")
        if (!t.accepted.isCompleted) {
            t.accepted.completeExceptionally(error)
        } else {
            t.completed.completeExceptionally(error)
        }
    }

    private fun handleAck(peerID: String, raw: ByteArray) {
        val t = outgoingFor(peerID, raw) ?: return
        val plaintext = BulkFrames.openEncryptedFrame(raw, t.key) ?: return
        val received = BulkFrames.decodeCount(plaintext) ?: return
        TransferProgressManager.progress(t.tidHex, received.coerceIn(0, t.chunkCount), t.chunkCount)
    }

    private fun handleComplete(peerID: String, raw: ByteArray) {
        val t = outgoingFor(peerID, raw) ?: return
        val plaintext = BulkFrames.openEncryptedFrame(raw, t.key) ?: return
        val (ok, _) = BulkFrames.decodeComplete(plaintext) ?: return
        t.completed.complete(ok)
    }

    private fun handleCancel(peerID: String, raw: ByteArray) {
        val tidHex = BulkFrames.frameTransferId(raw)?.let { BulkFrames.toHex(it) } ?: return
        outgoing[tidHex]?.let { t ->
            if (t.peerID != peerID) return
            if (BulkFrames.openEncryptedFrame(raw, t.key) == null) return
            t.cancelled = true
            t.accepted.completeExceptionally(IOException("cancelled by receiver"))
            t.completed.completeExceptionally(IOException("cancelled by receiver"))
            return
        }
        incoming[tidHex]?.let { t ->
            if (t.peerID != peerID) return
            if (BulkFrames.openEncryptedFrame(raw, t.offer.key) == null) return
            Log.i(TAG, "Bulk receive cancelled by sender (tid=${tidHex.take(16)}…)")
            synchronized(t) {
                if (t.closed) return
                t.closed = true
            }
            incoming.remove(tidHex)
            try { t.raf.close() } catch (_: Exception) { }
            try { t.partFile.delete() } catch (_: Exception) { }
        }
    }

    private fun outgoingFor(peerID: String, raw: ByteArray): OutgoingTransfer? {
        val tidHex = BulkFrames.frameTransferId(raw)?.let { BulkFrames.toHex(it) } ?: return null
        val t = outgoing[tidHex] ?: return null
        return if (t.peerID == peerID) t else null
    }

    private fun sendControl(peerID: String, type: Byte, offer: BulkOffer, payload: ByteArray) {
        try {
            writeFrame(peerID, BulkFrames.encryptedFrame(type, offer.transferId, offer.key, payload))
        } catch (e: Exception) {
            Log.w(TAG, "Control frame write failed: ${e.message}")
        }
    }

    private fun sendCancelFrame(peerID: String, transferId: ByteArray, key: ByteArray) {
        try {
            writeFrame(peerID, BulkFrames.encryptedFrame(BulkFrames.TYPE_CANCEL, transferId, key, ByteArray(0)))
        } catch (_: Exception) {
        }
    }

    private fun sweepStaleIncoming() {
        val now = System.currentTimeMillis()
        incoming.entries.removeIf { (tidHex, t) ->
            val stale = now - t.lastActivityAt > INCOMING_IDLE_TIMEOUT_MS
            if (stale) {
                Log.i(TAG, "Sweeping stale incoming bulk transfer (tid=${tidHex.take(16)}…)")
                synchronized(t) { t.closed = true }
                try { t.raf.close() } catch (_: Exception) { }
                try { t.partFile.delete() } catch (_: Exception) { }
            }
            stale
        }
    }

    private fun sha256OfFile(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
