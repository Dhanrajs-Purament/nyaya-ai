package com.bitchat.android.nyaya.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.android.nyaya.ai.AiRouter
import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.ai.CloudLlmEngine
import com.bitchat.android.nyaya.ai.LawyerSystemPrompt
import com.bitchat.android.nyaya.ai.ModelDownloadManager
import com.bitchat.android.nyaya.ai.OnDeviceLlmEngine
import com.bitchat.android.nyaya.memory.ConversationMemory
import com.bitchat.android.nyaya.settings.NyayaSettings
import com.bitchat.android.nyaya.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Nyaya AI lawyer. Owns the engines, memory, voice and
 * settings; exposes a single immutable UiState to the Compose UI.
 */
class NyayaViewModel(application: Application) : AndroidViewModel(application) {

    val settings = NyayaSettings(application)
    private val downloadManager = ModelDownloadManager(application)
    private val onDevice = OnDeviceLlmEngine(application)
    private val cloud = CloudLlmEngine(settings)
    private val router = AiRouter(settings, onDevice, cloud)

    private val memory = ConversationMemory { prompt ->
        val engine = router.active() ?: throw IllegalStateException("No AI engine ready")
        engine.generate(
            "You are a precise, factual summarizer. Never invent information.",
            listOf(ChatTurn(ChatTurn.Role.USER, prompt))
        )
    }

    val voice = VoiceManager(application)

    data class UiMessage(val role: ChatTurn.Role, val text: String)

    data class UiState(
        val messages: List<UiMessage> = emptyList(),
        val generating: Boolean = false,
        val modelDownloaded: Boolean = false,
        val modelLoading: Boolean = false,
        val downloadProgress: Float? = null,
        val engineLabel: String = "",
        val listening: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        voice.init()
        refreshEngineState()
        if (downloadManager.isDownloaded(settings.modelFileName)) {
            loadModel()
        }
    }

    private fun refreshEngineState() {
        _state.update {
            it.copy(
                modelDownloaded = downloadManager.isDownloaded(settings.modelFileName),
                engineLabel = router.activeLabel()
            )
        }
    }

    fun downloadAndLoadModel() {
        if (_state.value.downloadProgress != null) return
        viewModelScope.launch {
            _state.update { it.copy(downloadProgress = 0f, error = null) }
            try {
                downloadManager.download(
                    url = settings.modelUrl,
                    fileName = settings.modelFileName,
                    hfToken = settings.hfToken
                ) { p -> _state.update { it.copy(downloadProgress = p) } }
                _state.update { it.copy(downloadProgress = null) }
                loadModel()
            } catch (e: Exception) {
                _state.update { it.copy(downloadProgress = null, error = e.message) }
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            _state.update { it.copy(modelLoading = true, error = null) }
            try {
                onDevice.load(downloadManager.modelFile(settings.modelFileName))
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not load model: " + e.message) }
            }
            _state.update { it.copy(modelLoading = false) }
            refreshEngineState()
        }
    }

    fun send(text: String, speakReply: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.generating) return
        val engine = router.active()
        if (engine == null) {
            _state.update {
                it.copy(
                    error = "No AI engine is ready yet. Download the offline model, " +
                        "or add your own API key in Settings."
                )
            }
            return
        }
        memory.add(ChatTurn(ChatTurn.Role.USER, trimmed))
        _state.update {
            it.copy(
                messages = it.messages + UiMessage(ChatTurn.Role.USER, trimmed),
                generating = true,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val reply = engine.generate(LawyerSystemPrompt.PROMPT, memory.contextForModel())
                memory.add(ChatTurn(ChatTurn.Role.ASSISTANT, reply))
                _state.update {
                    it.copy(
                        messages = it.messages + UiMessage(ChatTurn.Role.ASSISTANT, reply),
                        generating = false
                    )
                }
                if (speakReply && settings.voiceRepliesEnabled) voice.speak(reply)
                memory.compactIfNeeded()
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message) }
            }
        }
    }

    fun newChat() {
        memory.clear()
        voice.stopSpeaking()
        _state.update { it.copy(messages = emptyList(), error = null) }
    }

    fun setListening(value: Boolean) {
        _state.update { it.copy(listening = value) }
    }

    fun updateSettings(
        engineMode: String,
        cloudBaseUrl: String,
        cloudApiKey: String,
        cloudModel: String,
        modelUrl: String,
        hfToken: String,
        voiceReplies: Boolean
    ) {
        settings.engineMode = engineMode
        settings.cloudBaseUrl = cloudBaseUrl
        settings.cloudApiKey = cloudApiKey
        settings.cloudModel = cloudModel
        settings.modelUrl = modelUrl
        settings.hfToken = hfToken
        settings.voiceRepliesEnabled = voiceReplies
        refreshEngineState()
    }

    override fun onCleared() {
        voice.release()
        onDevice.close()
        super.onCleared()
    }
}
