package com.example.localllm.ui.chat

import app.cash.turbine.test
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.data.repository.MlcModelRepository
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ToolCallClassifier
import com.example.localllm.domain.tools.ToolRegistry
import com.example.localllm.domain.tools.ToolResult
import com.example.localllm.engine.FakeInferenceEngine
import com.example.localllm.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inferenceEngine   = FakeInferenceEngine()
    private val conversationRepo  = mockk<ConversationRepository>()
    private val modelRepository   = mockk<MlcModelRepository>()
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val orchestrator      = mockk<ActionOrchestrator>()

    /**
     * Default classifier backed by an empty tool registry — always returns LlmChat.
     * Tests that need ToolCall behaviour can pass an [orchestrator] stub plus a
     * [ToolCallClassifier] built from a registry that includes the relevant tool.
     */
    private val alwaysLlmClassifier = ToolCallClassifier(ToolRegistry(emptySet()))

    private fun createViewModel(
        appScope: CoroutineScope,
        classifier: ToolCallClassifier = alwaysLlmClassifier
    ): ChatViewModel {
        every { settingsDataStore.settings } returns flowOf(AppSettings(activeModelId = "dummy_model"))
        coEvery { conversationRepo.createConversation(any(), any()) } returns 1L
        coEvery { conversationRepo.addMessage(any(), any(), any(), any(), any()) } returns 1L
        every { conversationRepo.getMessagesForConversation(any()) } returns flowOf(emptyList<Message>())

        return ChatViewModel(
            inferenceEngine  = inferenceEngine,
            conversationRepo = conversationRepo,
            modelRepository  = modelRepository,
            settingsDataStore = settingsDataStore,
            classifier       = classifier,
            orchestrator     = orchestrator,
            appScope         = appScope
        )
    }

    // ── LLM path (pre-existing) ───────────────────────────────────────────────

    @Test
    fun `sendMessage with NO active model preserves the draft and emits ShowError event`() = runTest {
        coEvery { modelRepository.getActiveModel() } returns null
        val viewModel = createViewModel(this.backgroundScope)
        viewModel.onInputChanged("Hello")

        viewModel.events.test {
            viewModel.sendMessage()

            while (true) {
                when (val event = awaitItem()) {
                    is ChatEvent.ShowError   -> {
                        assertTrue(event.message.contains("لا يوجد نموذج نشط"))
                        break
                    }
                    ChatEvent.ScrollToBottom -> Unit
                }
            }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("Hello", viewModel.uiState.value.inputText)
        io.mockk.coVerify(exactly = 0) { conversationRepo.createConversation(any(), any()) }
        io.mockk.coVerify(exactly = 0) { conversationRepo.addMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage with active model transitions through Generating and Streaming states`() = runTest {
        coEvery { modelRepository.getActiveModel() } returns InstalledModel(
            id = "dummy", name = "dummy", family = "dummy", sizeBytes = 0,
            filePath = "/fake/path", installedAt = 0, checksumVerified = true,
            isActive = true, quantization = "Q4", contextLength = 2048
        )
        val viewModel = createViewModel(this.backgroundScope)
        viewModel.onInputChanged("Hello")

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals("Hello", initialState.inputText)

            viewModel.sendMessage()

            val generatingState = awaitItem()
            assertEquals("", generatingState.inputText)
            assertTrue(generatingState.isGenerating)

            var finalState = awaitItem()
            while (finalState.isGenerating || finalState.isModelLoading) {
                finalState = awaitItem()
            }

            assertEquals(false, finalState.isGenerating)
            assertTrue(finalState.streamingText.isEmpty())
            assertNotNull(finalState.conversationId)
            assertNotNull(finalState.tokensPerSecond)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tool path ─────────────────────────────────────────────────────────────

    /**
     * Builds a [ToolCallClassifier] whose registry contains one fake tool
     * matching the given [toolName] and [keywords].
     */
    private fun classifierFor(toolName: String, vararg keywords: String): ToolCallClassifier {
        val fakeTool = object : com.example.localllm.domain.tools.Tool {
            override val name        = toolName
            override val description = "fake $toolName"
            override val keywords    = keywords.toList()
            override suspend fun execute(params: Map<String, Any>) =
                ToolResult(toolName, true, "ok")
        }
        return ToolCallClassifier(ToolRegistry(setOf(fakeTool)))
    }

    @Test
    fun `tool call sets streamingText to trace message then clears it`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            ToolResult("get_battery_status", true, "البطارية: 85%")

        val classifier = classifierFor("get_battery_status", "battery", "بطارية")
        val viewModel = createViewModel(this.backgroundScope, classifier)
        viewModel.onInputChanged("كم نسبة البطارية battery؟")

        viewModel.uiState.test {
            awaitItem()          // initial

            viewModel.sendMessage()

            var inFlight = awaitItem()
            assertThat(inFlight.isGenerating).isTrue()

            // Depending on StateFlow delivery timing, the first in-flight emission may
            // already include the trace text. Keep consuming until the trace appears.
            while (
                inFlight.isGenerating &&
                !inFlight.streamingText.contains("get_battery_status")
            ) {
                inFlight = awaitItem()
            }
            assertThat(inFlight.streamingText).contains("get_battery_status")

            var done = awaitItem()
            while (done.isGenerating) {
                done = awaitItem()
            }
            assertThat(done.isGenerating).isFalse()
            assertThat(done.streamingText).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tool result is persisted with TOOL role`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            ToolResult("get_battery_status", true, "البطارية: 85%")

        val classifier = classifierFor("get_battery_status", "battery", "بطارية")
        val viewModel = createViewModel(this.backgroundScope, classifier)
        viewModel.onInputChanged("battery level")

        viewModel.sendMessage()
        advanceUntilIdle()

        io.mockk.coVerify {
            conversationRepo.addMessage(
                conversationId   = any(),
                role             = MessageRole.TOOL,
                content          = "البطارية: 85%",
                tokensUsed       = any(),
                generationTimeMs = any()
            )
        }
    }

    @Test
    fun `tool failure persists error text with TOOL role`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            ToolResult("get_battery_status", false, "", errorMessage = "خطأ في قراءة البطارية")

        val classifier = classifierFor("get_battery_status", "battery", "بطارية")
        val viewModel = createViewModel(this.backgroundScope, classifier)
        viewModel.onInputChanged("battery level")

        viewModel.sendMessage()
        advanceUntilIdle()

        io.mockk.coVerify {
            conversationRepo.addMessage(
                conversationId   = any(),
                role             = MessageRole.TOOL,
                content          = match { it.contains("خطأ في قراءة البطارية") },
                tokensUsed       = any(),
                generationTimeMs = any()
            )
        }
    }

    @Test
    fun `tool call never attempts to load the inference engine`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            ToolResult("get_battery_status", true, "البطارية: 85%")

        val classifier = classifierFor("get_battery_status", "battery", "بطارية")
        val viewModel = createViewModel(this.backgroundScope, classifier)
        viewModel.onInputChanged("battery level")

        viewModel.sendMessage()
        advanceUntilIdle()

        // getActiveModel must never be called for a tool-routed message
        io.mockk.coVerify(exactly = 0) { modelRepository.getActiveModel() }
    }

    @Test
    fun `blank message is ignored regardless of classifier result`() = runTest {
        val viewModel = createViewModel(this.backgroundScope)
        viewModel.onInputChanged("   ")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isGenerating).isFalse()
        io.mockk.coVerify(exactly = 0) { conversationRepo.addMessage(any(), any(), any(), any(), any()) }
    }
}
