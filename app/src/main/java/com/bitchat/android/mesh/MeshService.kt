package com.bitchat.android.mesh

import com.bitchat.android.model.BitchatFilePacket

/**
 * Transport-agnostic mesh service API for UI and routing layers.
 */
interface MeshService {
    val myPeerID: String
    var delegate: MeshDelegate?

    fun startServices()
    fun stopServices()

    fun sendMessage(content: String, mentions: List<String> = emptyList(), channel: String? = null)
    fun sendPrivateMessage(content: String, recipientPeerID: String, recipientNickname: String, messageID: String? = null)
    fun sendReadReceipt(messageID: String, recipientPeerID: String, readerNickname: String)
    fun sendDeliveryAck(messageID: String, recipientPeerID: String) {}
    fun sendFavoriteNotification(peerID: String, isFavorite: Boolean) {}
    fun sendVerifyChallenge(peerID: String, noiseKeyHex: String, nonceA: ByteArray)
    fun sendVerifyResponse(peerID: String, noiseKeyHex: String, nonceA: ByteArray)
    fun sendFileBroadcast(file: BitchatFilePacket)
    fun sendFilePrivate(recipientPeerID: String, file: BitchatFilePacket)
    fun cancelFileTransfer(transferId: String): Boolean

    /**
     * Nyaya fast path: whether a large file can be sent to this peer over a
     * high-bandwidth local link (e.g. the Wi-Fi Aware TCP socket) instead of
     * the fragment-limited mesh path. Default: unsupported.
     */
    fun canSendFileBulk(peerID: String): Boolean = false

    /**
     * Nyaya fast path: starts a bulk file transfer to the peer. Returns the
     * transfer ID used by TransferProgressManager, or null when the fast path
     * is unavailable. Default: unsupported.
     */
    fun sendFileBulk(recipientPeerID: String, filePath: String, fileName: String, mimeType: String): String? = null

    fun sendBroadcastAnnounce()
    fun sendAnnouncementToPeer(peerID: String)

    fun getPeerNicknames(): Map<String, String>
    fun getPeerRSSI(): Map<String, Int>
    fun getActivePeerCount(): Int
    fun hasEstablishedSession(peerID: String): Boolean
    fun getSessionState(peerID: String): com.bitchat.android.noise.NoiseSession.NoiseSessionState
    fun initiateNoiseHandshake(peerID: String)
    fun getPeerFingerprint(peerID: String): String?
    fun getPeerInfo(peerID: String): PeerInfo?
    fun updatePeerInfo(
        peerID: String,
        nickname: String,
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray,
        isVerified: Boolean
    ): Boolean
    fun getIdentityFingerprint(): String
    fun getStaticNoisePublicKey(): ByteArray?
    fun shouldShowEncryptionIcon(peerID: String): Boolean
    fun getEncryptedPeers(): List<String>

    fun getDeviceAddressForPeer(peerID: String): String?
    fun getDeviceAddressToPeerMapping(): Map<String, String>
    fun printDeviceAddressesForPeers(): String
    fun getDebugStatus(): String

    fun clearAllInternalData()
    fun clearAllEncryptionData()
}
