package com.example.localllm.ui.chat

import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.data.repository.MlcModelRepository
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ClassificationResult
import com.example.localllm.domain.tools.ToolCallClassifier
import com.example.localllm.engine.EngineInfo
import com.example.localllm.engine.FinishReason
import com.example.localllm.engine.GenerationRequest
import com.example.localllm.engine.GenerationResponse
import com.example.localllm.engine.InferenceEngine
import com.example.localllm.engine.ModelConfig
import com.example.localllm.engine.ModelSession
import com.example.localllm.engine.TokenUsage
import com.example.localllm.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelRegressionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val conversationRepo = mockk<ConversationRepository>()
    private val modelRepository = mockk<MlcModelRepository>()
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val classifier = mockk<ToolCallClassifier>()
    private val orchestrator = mockk<ActionOrchestrator>()

    private fun createViewModel(
        inferenceEngine: InferenceEngine,
        appScope: CoroutineScope,
        settingsFlow: Flow<AppSettings> = flowOf(AppSettings(activeModelId = "dummy_model"))
    ): ChatViewModel {
        every { settingsDataStore.settings } returns settingsFlow
        every { classifier.classify(any()) } returns ClassificationResult.LlmChat
        return ChatViewModel(
            inferenceEngine = inferenceEngine,
            conversationRepo = conversationRepo,
            modelRepository = modelRepository,
            settingsDataStore = settingsDataStore,
            classifier = classifier,
            orchestrator = orchestrator,
            appScope = appScope
        )
    }

    @Test
    fun `first message starts collecting the newly created conversation`() = runTest {
        val engine = CapturingInferenceEngine()
        val messagesFlow = MutableStateFlow<List<Message>>(emptyList())

        coEvery { conversationRepo.createConversation(any(), any()) } returns 1L
        every { conversationRepo.getMessagesForConversation(1L) } returns messagesFlow
        coEvery { conversationRepo.addMessage(any(), any(), any(), any(), any()) } coAnswers {
            val conversationId = firstArg<Long>()
            val role = secondArg<MessageRole>()
            val content = thirdArg<String>()
            messagesFlow.value = messagesFlow.value + Message(
                id = (messagesFlow.value.size + 1).toLong(),
                conversationId = conversationId,
                role = role,
                content = content
            )
            1L
        }
        coEvery { modelRepository.getActiveModel() } returns installedModel()

        val viewModel = createViewModel(engine, backgroundScope)
        viewModel.onInputChanged("Hello")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.conversationId)
        assertTrue(
            viewModel.uiState.value.messages.any {
                it.role == MessageRole.USER && it.content == "Hello"
            }
        )
        verify(exactly = 1) { conversationRepo.getMessagesForConversation(1L) }
    }

    @Test
    fun `generation request does not duplicate the just persisted user message`() = runTest {
        val engine = CapturingInferenceEngine()
        val messagesFlow = MutableStateFlow(
            listOf(
                Message(
                    id = 1L,
                    conversationId = 7L,
                    role = MessageRole.ASSISTANT,
                    content = "Existing"
                )
            )
        )

        every { conversationRepo.getMessagesForConversation(7L) } returns messagesFlow
        coEvery { conversationRepo.addMessage(any(), any(), any(), any(), any()) } coAnswers {
            val conversationId = firstArg<Long>()
            val role = secondArg<MessageRole>()
            val content = thirdArg<String>()
            if (role == MessageRole.USER) {
                messagesFlow.value = messagesFlow.value + Message(
                    id = 2L,
                    conversationId = conversationId,
                    role = role,
                    content = content
                )
            }
            1L
        }
        coEvery { modelRepository.getActiveModel() } returns installedModel()

        val viewModel = createViewModel(engine, backgroundScope)
        viewModel.loadConversation(7L)
        advanceUntilIdle()
        viewModel.onInputChanged("Hello")

        viewModel.sendMessage()
        advanceUntilIdle()

        val request = checkNotNull(engine.session.lastRequest) {
            "Expected a generation request"
        }
        assertEquals(2, request.messages.size)
        assertEquals("Existing", request.messages.first().content)
        assertEquals(
            1,
            request.messages.count { it.role == MessageRole.USER && it.content == "Hello" }
        )
    }

    @Test
    fun `changing the active model unloads the previous session and reloads on the next request`() = runTest {
        val engine = CapturingInferenceEngine()
        val settingsFlow = MutableStateFlow(AppSettings(activeModelId = "model-one"))
        val messagesFlow = MutableStateFlow<List<Message>>(emptyList())

        every { classifier.classify(any()) } returns ClassificationResult.LlmChat
        every { conversationRepo.getMessagesForConversation(1L) } returns messagesFlow
        coEvery { conversationRepo.createConversation(any(), any()) } returns 1L
        coEvery { conversationRepo.addMessage(any(), any(), any(), any(), any()) } coAnswers {
            val conversationId = firstArg<Long>()
            val role = secondArg<MessageRole>()
            val content = thirdArg<String>()
            messagesFlow.value = messagesFlow.value + Message(
                id = (messagesFlow.value.size + 1).toLong(),
                conversationId = conversationId,
                role = role,
                content = content
            )
            1L
        }
        coEvery { modelRepository.getActiveModel() } returnsMany listOf(
            installedModel(id = "model-one", filePath = "/fake/path/one"),
            installedModel(id = "model-two", filePath = "/fake/path/two")
        )

        val viewModel = createViewModel(engine, backgroundScope, settingsFlow)

        viewModel.onInputChanged("First")
        viewModel.sendMessage()
        advanceUntilIdle()

        settingsFlow.value = AppSettings(activeModelId = "model-two")
        advanceUntilIdle()

        viewModel.onInputChanged("Second")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf("/fake/path/one", "/fake/path/two"), engine.loadPaths)
        assertEquals(2, engine.loadPaths.size)
        assertEquals(1, engine.unloadCount)
    }

    private fun installedModel(
        id: String = "dummy",
        filePath: String = "/fake/path"
    ) = InstalledModel(
        id = id,
        name = "dummy",
        family = "dummy",
        sizeBytes = 0,
        filePath = filePath,
        installedAt = 0,
        checksumVerified = true,
        isActive = true,
        quantization = "Q4",
        contextLength = 2048
    )

    private class CapturingInferenceEngine : InferenceEngine {
        val session = CapturingModelSession()
        val loadPaths = mutableListOf<String>()
        var unloadCount = 0
        private var loaded = false

        override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
            loaded = true
            loadPaths += modelPath
            return Result.success(session)
        }

        override fun isModelLoaded(): Boolean = loaded

        override suspend fun unloadModel() {
            loaded = false
            unloadCount++
        }

        override fun getEngineInfo(): EngineInfo = EngineInfo(
            name = "CapturingEngine",
            version = "test",
            backend = "test"
        )
    }

    private class CapturingModelSession : ModelSession {
        var lastRequest: GenerationRequest? = null

        override fun generate(request: GenerationRequest) = flow {
            lastRequest = request
            emit(
                GenerationResponse.Finished(
                    finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 1, completionTokens = 1)
                )
            )
        }

        override fun resetContext() = Unit

        override fun getContextLength(): Int = 2048

        override suspend fun close() = Unit
    }
}
