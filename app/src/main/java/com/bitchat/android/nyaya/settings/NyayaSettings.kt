package com.bitchat.android.nyaya.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitchat.android.nyaya.ai.NyayaModel
import com.bitchat.android.nyaya.ai.NyayaModelCatalog

/**
 * Keystore-encrypted settings for the Nyaya module. BYOK API keys and the
 * optional Hugging Face token never leave the device unencrypted.
 */
class NyayaSettings(context: Context) {

    companion object {
        const val MODE_ON_DEVICE = "on_device"
        const val MODE_CLOUD = "cloud"

        /**
         * Gemma 4 E2B as a LiteRT-LM bundle: ungated and Apache-2.0, so offline
         * mode works without a Hugging Face account. Users can point this at a
         * bigger Gemma 4 bundle (E4B) in Settings.
         */
        val DEFAULT_MODEL_URL: String get() = NyayaModelCatalog.default.url
    }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nyaya_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var engineMode: String
        get() = prefs.getString("engine_mode", MODE_ON_DEVICE) ?: MODE_ON_DEVICE
        set(value) = prefs.edit { putString("engine_mode", value) }

    var cloudBaseUrl: String
        get() = prefs.getString("cloud_base_url", "https://api.openai.com/v1") ?: ""
        set(value) = prefs.edit { putString("cloud_base_url", value.trim()) }

    var cloudApiKey: String
        get() = prefs.getString("cloud_api_key", "") ?: ""
        set(value) = prefs.edit { putString("cloud_api_key", value.trim()) }

    var cloudModel: String
        get() = prefs.getString("cloud_model", "gpt-4o-mini") ?: ""
        set(value) = prefs.edit { putString("cloud_model", value.trim()) }

    var modelUrl: String
        get() = prefs.getString("model_url", DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL
        set(value) = prefs.edit { putString("model_url", value.trim()) }

    var hfToken: String
        get() = prefs.getString("hf_token", "") ?: ""
        set(value) = prefs.edit { putString("hf_token", value.trim()) }

    var voiceRepliesEnabled: Boolean
        get() = prefs.getBoolean("voice_replies", true)
        set(value) = prefs.edit { putBoolean("voice_replies", value) }

    /**
     * Try the GPU backend before CPU when loading the on-device model. GPU is
     * both faster and lighter on RAM for Gemma 4 bundles; the engine falls back
     * to CPU automatically when a device cannot initialise it.
     */
    var preferGpu: Boolean
        get() = prefs.getBoolean("prefer_gpu", true)
        set(value) = prefs.edit { putBoolean("prefer_gpu", value) }

    /** Local file name derived from the configured model URL. */
    val modelFileName: String
        get() = NyayaModelCatalog.fileNameForUrl(modelUrl)

    /** Catalog entry for the configured URL, or null for a custom URL. */
    val selectedModel: NyayaModel?
        get() = NyayaModelCatalog.byUrl(modelUrl)

    /** Exact expected download size, when the configured model is known. */
    val modelExpectedBytes: Long?
        get() = NyayaModelCatalog.expectedBytesForUrl(modelUrl)
}
