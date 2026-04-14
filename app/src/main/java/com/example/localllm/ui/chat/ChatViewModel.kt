package com.example.localllm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.di.ApplicationScope
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ClassificationResult
import com.example.localllm.domain.tools.ToolCallClassifier
import com.example.localllm.engine.*
import com.example.localllm.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    private val modelRepository: ModelRepository,
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

    init {
        viewModelScope.launch {
            settingsDataStore.settings
                .map { it.activeModelId }
                .distinctUntilChanged()
                .collect { activeModelId ->
                    _uiState.update { it.copy(activeModelId = activeModelId) }
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
        _uiState.update { it.copy(inputText = "", isGenerating = true, streamingText = "") }

        generationJob = viewModelScope.launch {
            try {
                // ── Create conversation on first message ──────────────────────
                val convId = currentConversationId ?: run {
                    val title = text.take(40).let { if (text.length > 40) "$it…" else it }
                    val newId = conversationRepo.createConversation(title, _uiState.value.activeModelId)
                    loadConversation(newId)
                    newId
                }

                // ── Persist user message ──────────────────────────────────────
                conversationRepo.addMessage(convId, MessageRole.USER, text)

                // ── Classify: tool call or LLM conversation? ──────────────────
                when (val classification = classifier.classify(text)) {

                    is ClassificationResult.ToolCall -> {
                        // Show ephemeral trace in the StreamingBubble.
                        // yield() lets the UI frame update (and tests observe the state)
                        // before the tool starts executing.
                        _uiState.update { it.copy(streamingText = "⚙ جارٍ تنفيذ: ${classification.toolName}…") }
                        _events.emit(ChatEvent.ScrollToBottom)
                        yield()

                        val startTime = System.currentTimeMillis()
                        val toolResult = orchestrator.execute(classification.toolName)
                        val execTimeMs = System.currentTimeMillis() - startTime

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
                            _uiState.update { it.copy(isGenerating = false, streamingText = "") }
                            _events.emit(ChatEvent.ShowError("لا يوجد نموذج نشط. الرجاء اختيار نموذج من الإعدادات قبل بدء المحادثة."))
                            return@launch
                        }

                        // ── Load model session if needed ──────────────────────
                        if (currentSession == null || !inferenceEngine.isModelLoaded()) {
                            _uiState.update { it.copy(isModelLoading = true) }
                            val config = ModelConfig(contextLength = activeModel.contextLength)
                            currentSession = inferenceEngine.loadModel(activeModel.filePath, config).getOrThrow()
                            _uiState.update { it.copy(isModelLoading = false) }
                        }

                        val session = currentSession!!

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
        appScope.launch(NonCancellable + Dispatchers.IO) {
            withTimeoutOrNull(5_000L) {
                sessionToClose?.close()
                inferenceEngine.unloadModel()
            } ?: Timber.w("Model unload timed out after 5s — native resources may leak")
        }
    }
}
