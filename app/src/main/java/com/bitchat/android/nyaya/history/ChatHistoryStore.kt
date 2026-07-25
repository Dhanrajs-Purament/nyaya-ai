package com.bitchat.android.nyaya.history

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stores the user's Nyaya conversations on their own device.
 *
 * Design rules, in priority order:
 *
 * 1. **The data is the user's.** Nothing is deleted unless the user asks. There
 *    is no expiry, no cap that silently drops old chats, and nothing is removed
 *    when the app closes or when the mesh messenger wipes *its* data — the two
 *    modes own their own storage.
 * 2. **Deleting must be easy and complete.** [delete] removes one conversation,
 *    [deleteAll] removes every trace including the file itself.
 * 3. **Encrypted at rest.** The file is written through [EncryptedFile] with a
 *    key held in the Android Keystore, so an offline extraction of app storage
 *    yields ciphertext. This is Google's implementation, not hand-rolled crypto.
 *
 *    `androidx.security:security-crypto` is deprecated — Google now points to
 *    the platform Keystore APIs directly — and using it here is a deliberate
 *    choice rather than an oversight. bitchat's own `EncryptionService` and
 *    `SecureIdentityStateManager` already store the mesh identity through the
 *    same library, so it ships in this APK either way, and those files are held
 *    byte-identical to upstream so they cannot be migrated here. Introducing a
 *    second, parallel AES-GCM implementation for this one file would mean two
 *    crypto paths in one app, the new one written by us and impossible to
 *    execute without a physical device. Consistency with the rest of the app is
 *    the lower-risk answer until bitchat migrates upstream, at which point this
 *    should follow in the same change.
 * 4. **Incognito never touches storage.** [upsert] refuses to write a chat
 *    flagged incognito, and that is asserted by a unit test rather than left to
 *    reviewer discipline.
 *
 * The whole history is one JSON document, rewritten atomically after each
 * completed reply. A per-chat file scheme would write less per turn, but it adds
 * an index that can drift out of sync with the files; with a write happening
 * once per assistant reply rather than per keystroke, the simpler scheme is the
 * right trade until measurement says otherwise.
 */
class ChatHistoryStore(
    private val codec: ChatFileCodec,
    private val gson: Gson = Gson(),
    /**
     * Diagnostics sink. Defaults to a no-op so this class stays free of Android
     * dependencies and can be unit-tested on the JVM; [create] wires it to
     * logcat for the real app.
     */
    private val logWarning: (String, Throwable?) -> Unit = { _, _ -> }
) {

    /** Storage payload. Versioned so the format can be migrated later. */
    private data class Envelope(
        @SerializedName("version") val version: Int = SCHEMA_VERSION,
        @SerializedName("chats") val chats: List<SavedChat> = emptyList()
    )

    private val mutex = Mutex()

    @Volatile
    private var cache: List<SavedChat>? = null

    /** All saved conversations, most recently updated first. */
    suspend fun load(): List<SavedChat> = mutex.withLock { loadLocked() }

    /**
     * Inserts or replaces [chat]. Incognito and empty chats are ignored, so the
     * caller can save unconditionally after every turn without special-casing.
     */
    suspend fun upsert(chat: SavedChat) {
        if (chat.incognito || chat.isEmpty) return
        mutex.withLock {
            val next = loadLocked().filterNot { it.id == chat.id } + chat
            persistLocked(next)
        }
    }

    /** Deletes one conversation. */
    suspend fun delete(id: String) = mutex.withLock {
        val current = loadLocked()
        val next = current.filterNot { it.id == id }
        if (next.size != current.size) persistLocked(next)
    }

    /** Deletes every conversation and the underlying file. */
    suspend fun deleteAll() = mutex.withLock {
        withContext(Dispatchers.IO) { codec.delete() }
        cache = emptyList()
    }

    // -----------------------------------------------------------------------

    private suspend fun loadLocked(): List<SavedChat> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            val raw = try {
                codec.read()
            } catch (e: Exception) {
                // A read failure must not look like "the user has no chats",
                // because the next write would then overwrite recoverable data.
                logWarning("Could not read chat history", e)
                throw e
            }
            if (raw.isNullOrBlank()) return@withContext emptyList()
            try {
                gson.fromJson(raw, Envelope::class.java)?.chats.orEmpty()
            } catch (e: JsonSyntaxException) {
                // Never silently discard the user's data: keep the unreadable
                // file aside so it can be recovered, and carry on.
                logWarning("Chat history is unreadable; setting it aside", e)
                codec.quarantine()
                emptyList()
            }
        }.sortedByDescending { it.updatedAt }
        cache = loaded
        return loaded
    }

    private suspend fun persistLocked(chats: List<SavedChat>) {
        val ordered = chats.sortedByDescending { it.updatedAt }
        withContext(Dispatchers.IO) {
            codec.write(gson.toJson(Envelope(chats = ordered)))
        }
        cache = ordered
    }

    companion object {
        private const val TAG = "ChatHistoryStore"
        private const val SCHEMA_VERSION = 1
        private const val FILE_NAME = "nyaya_chats.json"

        /** Production store: Keystore-encrypted, in the app's private storage. */
        fun create(context: Context): ChatHistoryStore = ChatHistoryStore(
            codec = EncryptedChatFileCodec(context, File(context.filesDir, FILE_NAME)),
            logWarning = { message, error -> android.util.Log.e(TAG, message, error) }
        )
    }
}

/**
 * The storage seam. Production uses [EncryptedChatFileCodec]; unit tests use
 * [PlainChatFileCodec], because [MasterKey] needs the Android Keystore, which
 * Robolectric does not implement (the same reason bitchat's own keystore test is
 * an instrumentation test). The serialisation, ordering and incognito rules —
 * the parts that can actually contain logic bugs — are covered either way.
 */
interface ChatFileCodec {
    /** Returns the stored document, or null when nothing has been stored yet. */
    fun read(): String?

    /** Replaces the stored document atomically. */
    fun write(json: String)

    /** Removes the stored document entirely. */
    fun delete()

    /** Moves an unreadable document aside instead of destroying it. */
    fun quarantine()
}

/** Keystore-encrypted implementation used by the app. */
class EncryptedChatFileCodec(
    private val context: Context,
    private val file: File
) : ChatFileCodec {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val tempFile: File get() = File(file.parentFile, file.name + ".tmp")

    private fun encrypted(target: File) = EncryptedFile.Builder(
        context,
        target,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    override fun read(): String? {
        // Recover from a crash between "delete target" and "rename temp": the
        // temp file is the newer document, so promote it before reading.
        if (!file.exists() && tempFile.exists()) {
            tempFile.renameTo(file)
        }
        if (!file.exists()) return null
        return encrypted(file).openFileInput().use { it.readBytes().decodeToString() }
    }

    override fun write(json: String) {
        // EncryptedFile refuses to open an output stream over an existing file,
        // so write beside the target and swap. That also makes the replacement
        // atomic, so a crash mid-write cannot truncate the user's history.
        if (tempFile.exists() && !tempFile.delete()) {
            throw java.io.IOException("Could not clear ${tempFile.name} before writing")
        }
        encrypted(tempFile).openFileOutput().use { it.write(json.encodeToByteArray()) }
        if (file.exists() && !file.delete()) {
            tempFile.delete()
            throw java.io.IOException("Could not replace ${file.name}")
        }
        if (!tempFile.renameTo(file)) {
            throw java.io.IOException("Could not move ${tempFile.name} into place")
        }
    }

    override fun delete() {
        file.delete()
        tempFile.delete()
    }

    override fun quarantine() {
        file.renameTo(File(file.parentFile, file.name + ".unreadable-" + System.currentTimeMillis()))
    }
}

/** Plain-file implementation, for unit tests only. */
class PlainChatFileCodec(private val file: File) : ChatFileCodec {
    override fun read(): String? = if (file.exists()) file.readText() else null

    override fun write(json: String) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json)
        if (!temp.renameTo(file)) throw java.io.IOException("Could not move ${temp.name}")
    }

    override fun delete() {
        file.delete()
    }

    override fun quarantine() {
        file.renameTo(File(file.parentFile, file.name + ".unreadable"))
    }
}
