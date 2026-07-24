package com.bitchat.android.nyaya.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-encrypted settings for the Nyaya module. BYOK API keys and the
 * optional Hugging Face token never leave the device unencrypted.
 */
class NyayaSettings(context: Context) {

    companion object {
        const val MODE_ON_DEVICE = "on_device"
        const val MODE_CLOUD = "cloud"

        // Small, public LiteRT Gemma bundle that fits low-end Indian phones.
        // Users can point to a bigger Gemma bundle (E2B/E4B) in Settings.
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task"
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
        set(value) = prefs.edit().putString("engine_mode", value).apply()

    var cloudBaseUrl: String
        get() = prefs.getString("cloud_base_url", "https://api.openai.com/v1") ?: ""
        set(value) = prefs.edit().putString("cloud_base_url", value.trim()).apply()

    var cloudApiKey: String
        get() = prefs.getString("cloud_api_key", "") ?: ""
        set(value) = prefs.edit().putString("cloud_api_key", value.trim()).apply()

    var cloudModel: String
        get() = prefs.getString("cloud_model", "gpt-4o-mini") ?: ""
        set(value) = prefs.edit().putString("cloud_model", value.trim()).apply()

    var modelUrl: String
        get() = prefs.getString("model_url", DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL
        set(value) = prefs.edit().putString("model_url", value.trim()).apply()

    var hfToken: String
        get() = prefs.getString("hf_token", "") ?: ""
        set(value) = prefs.edit().putString("hf_token", value.trim()).apply()

    var voiceRepliesEnabled: Boolean
        get() = prefs.getBoolean("voice_replies", true)
        set(value) = prefs.edit().putBoolean("voice_replies", value).apply()

    /** Local file name derived from the configured model URL. */
    val modelFileName: String
        get() = modelUrl.substringAfterLast('/').substringBefore('?').ifBlank { "nyaya-model.task" }
}
