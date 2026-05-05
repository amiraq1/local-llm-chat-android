package com.example.localllm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
<<<<<<< HEAD
<<<<<<< HEAD
import com.example.localllm.domain.model.AppSettings
=======
import com.example.localllm.di.ApplicationScope
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
import com.example.localllm.di.ApplicationScope
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ClassificationResult
import com.example.localllm.domain.tools.ToolCallClassifier
import com.example.localllm.engine.*
import com.example.localllm.data.repository.MlcModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
<<<<<<< HEAD
<<<<<<< HEAD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
=======
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
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
<<<<<<< HEAD
<<<<<<< HEAD
    private var currentSettings: AppSettings = AppSettings()

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                currentSettings = settings
                _uiState.update { it.copy(activeModelId = settings.activeModelId) }
            }
=======
    private var messagesCollectionJob: Job? = null

    init {
        viewModelScope.launch {
=======
    private var messagesCollectionJob: Job? = null

    init {
        viewModelScope.launch {
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
            settingsDataStore.settings
                .map { it.activeModelId }
                .distinctUntilChanged()
                .collect { activeModelId ->
                    _uiState.update { it.copy(activeModelId = activeModelId) }
                }
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
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
<<<<<<< HEAD

                    is ClassificationResult.ToolCall -> {
                        // Show ephemeral trace in the StreamingBubble.
                        // yield() lets the UI frame update (and tests observe the state)
                        // before the tool starts executing.
                        _uiState.update { it.copy(streamingText = "⚙ جارٍ تنفيذ: ${classification.toolName}…") }
                        _events.emit(ChatEvent.ScrollToBottom)
                        yield()

<<<<<<< HEAD
                val session = currentSession ?: run {
                    _events.emit(ChatEvent.ShowError("فشل تحميل جلسة النموذج"))
                    _uiState.update { it.copy(isGenerating = false) }
                    return@launch
                }

                // Build chat history for context
                val history = _uiState.value.messages.map { msg ->
                    ChatMessage(
                        role = msg.role.name.lowercase(),
                        content = msg.content
                    )
                }

                val request = GenerationRequest(
                    messages = history + ChatMessage("user", text),
                    maxTokens = currentSettings.maxTokens,
                    temperature = currentSettings.temperature,
                    topP = currentSettings.topP
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
=======
                        val startTime = System.currentTimeMillis()
                        val toolResult = orchestrator.execute(classification.toolName)
                        val execTimeMs = System.currentTimeMillis() - startTime

                        val content = if (toolResult.success) {
                            toolResult.resultText
                        } else {
                            "⚠ ${toolResult.errorMessage ?: "حدث خطأ غير معروف"}"
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
                        }

=======

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

>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
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
                            currentSession = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                inferenceEngine.loadModel(activeModel.filePath, config).getOrThrow()
                            }
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
<<<<<<< HEAD
<<<<<<< HEAD
        // viewModelScope is cancelled in onCleared, so we use a bounded cleanup scope
        // with a timeout to ensure native C++ models are safely unloaded.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(5_000L) {
                    currentSession?.close()
                    inferenceEngine.unloadModel()
                }
            } catch (e: Exception) {
                Timber.e(e, "Cleanup failed during onCleared")
            }
=======
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
        generationJob?.cancel()
        messagesCollectionJob?.cancel()
        val sessionToClose = currentSession
        currentSession = null
        appScope.launch(NonCancellable + Dispatchers.IO) {
            withTimeoutOrNull(5_000L) {
                sessionToClose?.close()
                inferenceEngine.unloadModel()
            } ?: Timber.w("Model unload timed out after 5s — native resources may leak")
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
        }
    }
}
