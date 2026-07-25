package com.bitchat.android.nyaya.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.android.nyaya.ai.AiRouter
import com.bitchat.android.nyaya.ai.ChatTurn
import com.bitchat.android.nyaya.ai.CloudLlmEngine
import com.bitchat.android.nyaya.ai.LawyerSystemPrompt
import com.bitchat.android.nyaya.ai.LegalKnowledgeBase
import com.bitchat.android.nyaya.ai.LegalLibrary
import com.bitchat.android.nyaya.ai.ModelDownloadManager
import com.bitchat.android.nyaya.ai.OnDeviceLlmEngine
import com.bitchat.android.nyaya.history.ChatHistoryStore
import com.bitchat.android.nyaya.history.SavedChat
import com.bitchat.android.nyaya.history.SavedMessage
import com.bitchat.android.nyaya.memory.ConversationMemory
import com.bitchat.android.nyaya.settings.NyayaSettings
import com.bitchat.android.nyaya.voice.VoiceManager
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Nyaya AI lawyer. Owns the engines, memory, voice,
 * settings, the pre-warmed legal knowledge base, the user's saved conversations
 * and the offline library index; exposes a single immutable UiState to Compose.
 */
class NyayaViewModel(application: Application) : AndroidViewModel(application) {

    val settings = NyayaSettings(application)
    private val downloadManager = ModelDownloadManager(application)
    private val onDevice = OnDeviceLlmEngine(application)
    private val cloud = CloudLlmEngine(settings)
    private val router = AiRouter(settings, onDevice, cloud)

    /**
     * Offline legal knowledge base. Warmed up in the background the moment
     * the app opens, so the very first question is answered with real
     * bare-act text already indexed in memory.
     */
    private val knowledgeBase = LegalKnowledgeBase(application)

    /** Browsable index of the same bundled Acts, for the library screen. */
    private val library = LegalLibrary(application)

    /**
     * The user's conversations, on their own device. Nothing here is deleted
     * except when the user asks; see [deleteChat] and [deleteAllChats].
     */
    private val history = ChatHistoryStore.create(application)

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
        val micLevel: Float = 0f,
        val kbReady: Boolean = false,
        val error: String? = null,
        /** Saved conversations, most recently updated first. */
        val chats: List<SavedChat> = emptyList(),
        val activeChatId: String? = null,
        /** When true this conversation is never written to storage. */
        val incognito: Boolean = false,
        val libraryDocuments: List<LegalLibrary.Document> = emptyList(),
        val libraryLoading: Boolean = false,
        val openDocument: LegalLibrary.Document? = null,
        val openDocumentSections: List<LegalLibrary.Section> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Identity of the conversation being edited, so saves replace rather than duplicate. */
    private var activeChatId: String = UUID.randomUUID().toString()
    private var activeCreatedAt: Long = System.currentTimeMillis()

    /** The in-flight generation, kept so the user can stop it. */
    private var generationJob: Job? = null

    init {
        voice.init()
        refreshEngineState()
        viewModelScope.launch {
            knowledgeBase.warmUp()
            _state.update { it.copy(kbReady = knowledgeBase.isWarm) }
        }
        reloadHistory()
        if (downloadManager.isDownloaded(settings.modelFileName)) {
            loadModel()
        }
    }

    // -----------------------------------------------------------------------
    // Engine and model
    // -----------------------------------------------------------------------

    private fun refreshEngineState() {
        _state.update {
            it.copy(
                modelDownloaded = downloadManager.isDownloaded(
                    settings.modelFileName,
                    settings.modelExpectedBytes
                ),
                engineLabel = engineLabelWithBackend()
            )
        }
    }

    /** e.g. "On-device (offline, GPU)" once a backend has actually loaded. */
    private fun engineLabelWithBackend(): String {
        val base = router.activeLabel()
        val backend = onDevice.activeBackend
        return if (onDevice.isReady && backend.isNotBlank()) {
            "On-device (offline, $backend)"
        } else {
            base
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
                    hfToken = settings.hfToken,
                    expectedBytes = settings.modelExpectedBytes
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
                onDevice.load(
                    modelFile = downloadManager.modelFile(settings.modelFileName),
                    preferGpu = settings.preferGpu
                )
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not load model: " + e.message) }
            }
            _state.update { it.copy(modelLoading = false) }
            refreshEngineState()
        }
    }

    // -----------------------------------------------------------------------
    // Conversation
    // -----------------------------------------------------------------------

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
        generationJob = viewModelScope.launch {
            try {
                // Offline RAG: ground the answer in real bare-act text.
                val references = knowledgeBase.retrieve(trimmed)
                val systemPrompt = if (references.isEmpty()) {
                    LawyerSystemPrompt.PROMPT
                } else {
                    LawyerSystemPrompt.PROMPT +
                        "\n\nVERIFIED REFERENCE EXTRACTS from official Indian law " +
                        "(base your answer on these; cite section numbers only from " +
                        "these extracts; never invent citations):\n" +
                        knowledgeBase.asReferenceBlock(references)
                }
                val reply = engine.generate(systemPrompt, memory.contextForModel())
                memory.add(ChatTurn(ChatTurn.Role.ASSISTANT, reply))
                _state.update {
                    it.copy(
                        messages = it.messages + UiMessage(ChatTurn.Role.ASSISTANT, reply),
                        generating = false
                    )
                }
                if (speakReply && settings.voiceRepliesEnabled) voice.speak(reply)
                memory.compactIfNeeded()
                persistActiveChat()
            } catch (e: CancellationException) {
                // The user pressed stop. Their question stays on screen, and the
                // partial turn is kept out of memory so the next reply is clean.
                _state.update { it.copy(generating = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message) }
            }
        }
    }

    /** Stops an in-flight reply. */
    fun stopGenerating() {
        generationJob?.cancel()
        generationJob = null
        voice.stopSpeaking()
        _state.update { it.copy(generating = false) }
    }

    /** Starts a fresh conversation, optionally one that is never saved. */
    fun newChat(incognito: Boolean = false) {
        stopGenerating()
        memory.clear()
        activeChatId = UUID.randomUUID().toString()
        activeCreatedAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                messages = emptyList(),
                error = null,
                activeChatId = null,
                incognito = incognito
            )
        }
    }

    /**
     * Turns incognito on or off for the conversation in progress.
     *
     * Switching *on* mid-conversation removes what was already saved, because a
     * user who reaches for incognito is asking for this exchange not to be on the
     * phone — leaving the first half of it saved would defeat the point.
     */
    fun setIncognito(value: Boolean) {
        val previousId = activeChatId
        _state.update { it.copy(incognito = value, activeChatId = if (value) null else it.activeChatId) }
        if (value) {
            viewModelScope.launch {
                history.delete(previousId)
                reloadHistory()
            }
        } else {
            persistActiveChatAsync()
        }
    }

    /** Reopens a saved conversation, restoring its transcript and Case File. */
    fun openChat(id: String) {
        val chat = _state.value.chats.firstOrNull { it.id == id } ?: return
        stopGenerating()
        activeChatId = chat.id
        activeCreatedAt = chat.createdAt
        memory.restore(
            previousTurns = chat.messages.map { ChatTurn(it.role, it.text) },
            previousCaseFile = chat.caseFile
        )
        _state.update {
            it.copy(
                messages = chat.messages.map { m -> UiMessage(m.role, m.text) },
                activeChatId = chat.id,
                incognito = false,
                generating = false,
                error = null
            )
        }
    }

    /** Deletes one saved conversation. */
    fun deleteChat(id: String) {
        viewModelScope.launch {
            history.delete(id)
            // Deleting the conversation on screen has to clear the screen too,
            // otherwise it looks deleted in the list but is still readable.
            if (id == activeChatId) newChat(incognito = _state.value.incognito)
            reloadHistory()
        }
    }

    /** Deletes every saved conversation. Irreversible, and the UI confirms first. */
    fun deleteAllChats() {
        viewModelScope.launch {
            history.deleteAll()
            newChat(incognito = _state.value.incognito)
            reloadHistory()
        }
    }

    private fun persistActiveChatAsync() {
        viewModelScope.launch { persistActiveChat() }
    }

    private suspend fun persistActiveChat() {
        val current = _state.value
        if (current.incognito || current.messages.isEmpty()) return
        val now = System.currentTimeMillis()
        val chat = SavedChat(
            id = activeChatId,
            title = SavedChat.titleFrom(
                current.messages.firstOrNull { it.role == ChatTurn.Role.USER }?.text
            ),
            createdAt = activeCreatedAt,
            updatedAt = now,
            messages = current.messages.map { SavedMessage(it.role, it.text, now) },
            caseFile = memory.caseFile
        )
        try {
            history.upsert(chat)
            _state.update { it.copy(activeChatId = chat.id, chats = history.load()) }
        } catch (e: Exception) {
            // Saving must never lose the conversation the user is having, so the
            // failure is surfaced rather than swallowed and the transcript stays
            // on screen.
            _state.update {
                it.copy(error = "Could not save this conversation: " + (e.message ?: "unknown error"))
            }
        }
    }

    private fun reloadHistory() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(chats = history.load()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not read saved conversations: " + e.message) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Offline legal library
    // -----------------------------------------------------------------------

    /** Loads the library index once; cheap enough to call on every screen open. */
    fun loadLibrary() {
        if (_state.value.libraryDocuments.isNotEmpty() || _state.value.libraryLoading) return
        viewModelScope.launch {
            _state.update { it.copy(libraryLoading = true) }
            val docs = try {
                library.list()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not open the legal library: " + e.message) }
                emptyList()
            }
            _state.update { it.copy(libraryDocuments = docs, libraryLoading = false) }
        }
    }

    /** Opens one document, parsing it into sections off the main thread. */
    fun openDocument(document: LegalLibrary.Document) {
        viewModelScope.launch {
            _state.update {
                it.copy(openDocument = document, openDocumentSections = emptyList(), libraryLoading = true)
            }
            val sections = try {
                library.sections(document.assetName)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not read " + document.shortTitle) }
                emptyList()
            }
            _state.update { it.copy(openDocumentSections = sections, libraryLoading = false) }
        }
    }

    fun closeDocument() {
        _state.update { it.copy(openDocument = null, openDocumentSections = emptyList()) }
    }

    // -----------------------------------------------------------------------
    // Voice and settings
    // -----------------------------------------------------------------------

    fun setListening(value: Boolean) {
        _state.update { it.copy(listening = value, micLevel = if (value) it.micLevel else 0f) }
    }

    /**
     * Records the microphone amplitude for the voice orb. Android reports RMS in
     * dB, roughly -2 (silence) to 10 (loud), which is mapped to 0..1 here so the
     * UI never has to know about the units.
     */
    fun setMicLevel(rmsDb: Float) {
        val normalised = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        _state.update { it.copy(micLevel = normalised) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun updateSettings(
        engineMode: String,
        cloudBaseUrl: String,
        cloudApiKey: String,
        cloudModel: String,
        modelUrl: String,
        hfToken: String,
        voiceReplies: Boolean,
        preferGpu: Boolean = settings.preferGpu
    ) {
        settings.engineMode = engineMode
        settings.cloudBaseUrl = cloudBaseUrl
        settings.cloudApiKey = cloudApiKey
        settings.cloudModel = cloudModel
        settings.modelUrl = modelUrl
        settings.hfToken = hfToken
        settings.voiceRepliesEnabled = voiceReplies
        settings.preferGpu = preferGpu
        refreshEngineState()
    }

    override fun onCleared() {
        voice.release()
        onDevice.close()
        super.onCleared()
    }
}
