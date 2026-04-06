package com.example.localllm.ui.chat

import app.cash.turbine.test
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.engine.FakeInferenceEngine
import com.example.localllm.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inferenceEngine = FakeInferenceEngine()
    private val conversationRepo = mockk<ConversationRepository>()
    private val modelRepository = mockk<ModelRepository>()
    private val settingsDataStore = mockk<SettingsDataStore>()

    private fun createViewModel(): ChatViewModel {
        // Default mock responses
        every { settingsDataStore.settings } returns flowOf(AppSettings(activeModelId = "dummy_model"))
        coEvery { conversationRepo.createConversation(any(), any()) } returns 1L
        coEvery { conversationRepo.addMessage(any(), any(), any(), any(), any()) } returns Unit
        
        return ChatViewModel(inferenceEngine, conversationRepo, modelRepository, settingsDataStore)
    }

    @Test
    fun `sendMessage with NO active model emits ShowError event`() = runTest {
        // Arrange: Repository returns null for active model
        coEvery { modelRepository.getActiveModel() } returns null
        val viewModel = createViewModel()
        viewModel.onInputChanged("Hello")

        // Act & Assert
        viewModel.events.test {
            viewModel.sendMessage()
            
            val event = awaitItem() as ChatEvent.ShowError
            assertTrue(event.message.contains("لا يوجد نموذج نشط"))
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessage with active model transitions through Generating and Streaming states`() = runTest {
        // Arrange: Repository returns a valid active model
        coEvery { modelRepository.getActiveModel() } returns InstalledModel(
            id = "dummy", name = "dummy", family = "dummy", sizeBytes = 0,
            filePath = "/fake/path", installedAt = 0, checksumVerified = true,
            isActive = true, quantization = "Q4", contextLength = 2048
        )
        
        val viewModel = createViewModel()
        viewModel.onInputChanged("Hello")

        // Act & Assert using Turbine
        viewModel.uiState.test {
            // Initial state
            val initialState = awaitItem()
            assertEquals("Hello", initialState.inputText)

            viewModel.sendMessage()

            // State changes after clicking send: inputText cleared, isGenerating = true
            val generatingState = awaitItem()
            assertEquals("", generatingState.inputText)
            assertTrue(generatingState.isGenerating)
            
            // Ignore loading and streaming state changes safely
            var finalState = awaitItem()
            while (finalState.isGenerating) {
                finalState = awaitItem()
            }
            
            // Finished state
            assertEquals(false, finalState.isGenerating)
            assertTrue(finalState.streamingText.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
