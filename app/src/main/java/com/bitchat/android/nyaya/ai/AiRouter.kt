package com.bitchat.android.nyaya.ai

import com.bitchat.android.nyaya.settings.NyayaSettings

/**
 * Chooses which engine answers a request based on user preference and readiness.
 * On-device is the default (privacy + offline); BYOK cloud is opt-in.
 */
class AiRouter(
    private val settings: NyayaSettings,
    private val onDevice: OnDeviceLlmEngine,
    private val cloud: CloudLlmEngine
) {

    /** The engine that should serve the next request, or null if none is ready. */
    fun active(): LlmEngine? = when (settings.engineMode) {
        NyayaSettings.MODE_CLOUD -> when {
            cloud.isReady -> cloud
            onDevice.isReady -> onDevice
            else -> null
        }
        else -> when {
            onDevice.isReady -> onDevice
            cloud.isReady -> cloud
            else -> null
        }
    }

    fun activeLabel(): String = when (active()) {
        onDevice -> "On-device (offline)"
        cloud -> "Cloud (your key)"
        else -> "No engine ready"
    }
}
