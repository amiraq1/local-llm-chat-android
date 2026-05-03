package com.example.localllm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.di.ApplicationScope
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ClassificationResult
import com.example.localllm.domain.tools.ToolCallClassifier
import com.example.localllm.engine.*
import com.example.localllm.data.repository.MlcModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import timber.log.Timber
import javax.inject.Inject

data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isModelLoading: Boolean = false,
    val streamingText: String = "",          // live partial assistant response
    val activeModelId: String = "",
    val errorMessage: String? = null,
    val tokensPerSecond: Double? = null
)

sealed class ChatEvent {
    data class ShowError(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val conversationRepo: ConversationRepository,
    private val modelRepository: MlcModelRepository,
    private val settingsDataStore: SettingsDataStore,
    private val classifier: ToolCallClassifier,
    private val orchestrator: ActionOrchestrator,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var generationJob: Job? = null
    private var currentSession: ModelSession? = null
    private var currentConversationId: Long? = null
    private var messagesCollectionJob: Job? = null
    private var loadedModelId: String? = null
    private var sessionReleaseJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.settings
                .map { it.activeModelId }
                .distinctUntilChanged()
                .collect { activeModelId ->
                    _uiState.update { it.copy(activeModelId = activeModelId) }
                    if (loadedModelId != null && loadedModelId != activeModelId) {
                        invalidateLoadedSession("Active model selection changed")
                    }
                }
        }
    }

    fun loadConversation(conversationId: Long) {
        if (currentConversationId == conversationId) return
        currentConversationId = conversationId
        _uiState.update { it.copy(conversationId = conversationId) }

        messagesCollectionJob?.cancel()
        messagesCollectionJob = viewModelScope.launch {
            conversationRepo.getMessagesForConversation(conversationId)
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    _events.emit(ChatEvent.ScrollToBottom)
                }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSuggestionClicked(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        // TOOL messages are device-tool results; they must not be sent to the LLM.
        val history = _uiState.value.messages
            .filter { it.role != MessageRole.TOOL }
            .map(Message::toChatMessage)

        generationJob = viewModelScope.launch {
            try {
                // ── Classify: tool call or LLM conversation? ──────────────────
                when (val classification = classifier.classify(text)) {

                    is ClassificationResult.ToolCall -> {
                        _uiState.update {
                            it.copy(
                                inputText = "",
                                isGenerating = true,
                                streamingText = "",
                                tokensPerSecond = null
                            )
                        }
                        yield()

                        // Surface the tool trace before conversation persistence so fast
                        // local actions still render a visible "working" state.
                        val traceShownAtMs = System.currentTimeMillis()
                        _uiState.update { it.copy(streamingText = "⚙ جارٍ تنفيذ: ${classification.toolName}…") }
                        _events.emit(ChatEvent.ScrollToBottom)
                        yield()

                        val convId = ensureConversation(text, _uiState.value.activeModelId)
                        conversationRepo.addMessage(convId, MessageRole.USER, text)

                        val startTime = System.currentTimeMillis()
                        val toolResult = orchestrator.execute(classification.toolName)
                        val execTimeMs = System.currentTimeMillis() - startTime
                        val remainingTraceMs =
                            MIN_TOOL_TRACE_VISIBILITY_MS - (System.currentTimeMillis() - traceShownAtMs)
                        if (remainingTraceMs > 0) {
                            delay(remainingTraceMs)
                        }

                        val content = if (toolResult.success) {
                            toolResult.resultText
                        } else {
                            "⚠ ${toolResult.errorMessage ?: "حدث خطأ غير معروف"}"
                        }

                        conversationRepo.addMessage(
                            conversationId = convId,
                            role           = MessageRole.TOOL,
                            content        = content,
                            generationTimeMs = execTimeMs
                        )

                        _uiState.update { it.copy(isGenerating = false, streamingText = "") }
                        _events.emit(ChatEvent.ScrollToBottom)
                    }

                    is ClassificationResult.LlmChat -> {
                        // ── Fetch active model ────────────────────────────────
                        val activeModel = modelRepository.getActiveModel()
                        if (activeModel == null) {
                            _uiState.update {
                                it.copy(
                                    inputText = text,
                                    isGenerating = false,
                                    isModelLoading = false,
                                    streamingText = ""
                                )
                            }
                            _events.emit(ChatEvent.ShowError("لا يوجد نموذج نشط. الرجاء اختيار نموذج من الإعدادات قبل بدء المحادثة."))
                            return@launch
                        }

                        _uiState.update {
                            it.copy(
                                inputText = "",
                                isGenerating = true,
                                streamingText = "",
                                tokensPerSecond = null
                            )
                        }

                        val convId = ensureConversation(text, activeModel.id)
                        conversationRepo.addMessage(convId, MessageRole.USER, text)

                        val session = ensureModelSession(activeModel)

                        val request = GenerationRequest(
                            messages   = history + ChatMessage(role = MessageRole.USER, content = text),
                            maxTokens  = 512,
                            temperature = 0.7f
                        )

                        val startTime = System.currentTimeMillis()
                        var totalTokens = 0
                        val builder = StringBuilder()

                        session.generate(request).collect { response ->
                            when (response) {
                                is GenerationResponse.Token -> {
                                    builder.append(response.text)
                                    totalTokens++
                                    _uiState.update { it.copy(streamingText = builder.toString()) }
                                    _events.emit(ChatEvent.ScrollToBottom)
                                }
                                is GenerationResponse.Finished -> {
                                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                                    val tps = if (elapsedSec > 0) totalTokens / elapsedSec else 0.0

                                    conversationRepo.addMessage(
                                        conversationId  = convId,
                                        role            = MessageRole.ASSISTANT,
                                        content         = builder.toString(),
                                        tokensUsed      = response.usage.completionTokens,
                                        generationTimeMs = System.currentTimeMillis() - startTime
                                    )

                                    _uiState.update {
                                        it.copy(
                                            isGenerating    = false,
                                            streamingText   = "",
                                            tokensPerSecond = tps
                                        )
                                    }
                                    _events.emit(ChatEvent.ScrollToBottom)
                                }
                                is GenerationResponse.Error -> {
                                    Timber.e(response.throwable, "Generation error")
                                    _uiState.update { it.copy(isGenerating = false, streamingText = "") }
                                    _events.emit(ChatEvent.ShowError("حدث خطأ أثناء التوليد"))
                                }
                            }
                        }
                    }
                }

            } catch (_: kotlinx.coroutines.CancellationException) {
                Timber.i("sendMessage cancelled")
            } catch (e: Exception) {
                Timber.e(e, "sendMessage error")
                _uiState.update { it.copy(isGenerating = false, isModelLoading = false, streamingText = "") }
                _events.emit(ChatEvent.ShowError("خطأ: ${e.message}"))
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, streamingText = "") }
    }

    fun resetContext() {
        currentSession?.resetContext()
    }

    fun startNewConversation() {
        stopGeneration()
        messagesCollectionJob?.cancel()
        currentConversationId = null
        currentSession?.let { session ->
            viewModelScope.launch { session.resetContext() }
        }
        _uiState.update {
            ChatUiState(activeModelId = it.activeModelId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        messagesCollectionJob?.cancel()
        val sessionToClose = currentSession
        currentSession = null
        loadedModelId = null
        appScope.launch(NonCancellable + Dispatchers.IO) {
            withTimeoutOrNull(5_000L) {
                sessionToClose?.close()
                inferenceEngine.unloadModel()
            } ?: Timber.w("Model unload timed out after 5s — native resources may leak")
        }
    }

    private suspend fun ensureConversation(
        text: String,
        modelId: String
    ): Long = currentConversationId ?: run {
        val title = text.take(40).let { if (text.length > 40) "$it…" else it }
        val newId = conversationRepo.createConversation(title, modelId)
        loadConversation(newId)
        newId
    }

    private suspend fun ensureModelSession(activeModel: InstalledModel): ModelSession {
        awaitPendingSessionRelease()

        val reusableSession = currentSession
            ?.takeIf { inferenceEngine.isModelLoaded() && loadedModelId == activeModel.id }
        if (reusableSession != null) {
            return reusableSession
        }

        _uiState.update { it.copy(isModelLoading = true) }
        try {
            if (currentSession != null || inferenceEngine.isModelLoaded()) {
                releaseLoadedSession("Reloading model session")
            }

            val config = ModelConfig(contextLength = activeModel.contextLength)
            // The engine is responsible for its own blocking work. Avoid hopping
            // to an unmanaged dispatcher here so ViewModel behavior stays predictable.
            val session = inferenceEngine.loadModel(activeModel.filePath, config).getOrThrow()
            currentSession = session
            loadedModelId = activeModel.id
            return session
        } finally {
            _uiState.update { it.copy(isModelLoading = false) }
        }
    }

    private fun invalidateLoadedSession(reason: String) {
        if (currentSession == null && !inferenceEngine.isModelLoaded()) return
        if (sessionReleaseJob?.isActive == true) return

        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                isModelLoading = false,
                streamingText = "",
                tokensPerSecond = null
            )
        }

        sessionReleaseJob = appScope.launch(Dispatchers.IO) {
            releaseLoadedSession(reason)
        }
    }

    private suspend fun releaseLoadedSession(reason: String) {
        val sessionToClose = currentSession
        currentSession = null
        loadedModelId = null

        sessionToClose?.let { session ->
            runCatching { session.close() }
                .onFailure { Timber.w(it, "Failed to close model session: %s", reason) }
        }

        runCatching { inferenceEngine.unloadModel() }
            .onFailure { Timber.w(it, "Failed to unload inference engine: %s", reason) }
    }

    private suspend fun awaitPendingSessionRelease() {
        val pendingRelease = sessionReleaseJob ?: return
        pendingRelease.join()
        if (sessionReleaseJob === pendingRelease) {
            sessionReleaseJob = null
        }
    }

    private companion object {
        const val MIN_TOOL_TRACE_VISIBILITY_MS = 120L
    }
}
