package com.example.localllm.ui.models

import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.domain.model.LLMModel
import com.example.localllm.domain.model.ModelUiState
import com.example.localllm.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelsViewModelRegressionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val modelRepository = mockk<ModelRepository>()
    private val settingsDataStore = mockk<SettingsDataStore>()

    @Test
    fun `restores active model in database when datastore still points to installed model`() = runTest {
        every { modelRepository.availableModels } returns emptyList()
        every { modelRepository.getModelUiStates() } returns flowOf(
            listOf(modelState(isInstalled = true, isActive = false))
        )
        every { settingsDataStore.settings } returns flowOf(AppSettings(activeModelId = MODEL_ID))
        coEvery { modelRepository.setActiveModel(MODEL_ID) } just runs
        coEvery { settingsDataStore.updateActiveModelId(any()) } just runs

        ModelsViewModel(modelRepository, settingsDataStore)
        advanceUntilIdle()

        coVerify(exactly = 1) { modelRepository.setActiveModel(MODEL_ID) }
        coVerify(exactly = 0) { settingsDataStore.updateActiveModelId("") }
    }

    @Test
    fun `clears stale active model id when model is no longer installed`() = runTest {
        every { modelRepository.availableModels } returns emptyList()
        every { modelRepository.getModelUiStates() } returns flowOf(
            listOf(modelState(isInstalled = false, isActive = false))
        )
        every { settingsDataStore.settings } returns flowOf(AppSettings(activeModelId = MODEL_ID))
        coEvery { settingsDataStore.updateActiveModelId("") } just runs

        val viewModel = ModelsViewModel(modelRepository, settingsDataStore)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsDataStore.updateActiveModelId("") }
        assertEquals("", viewModel.state.value.activeModelId)
    }

    @Test
    fun `deleteModel clears active model id when deleting the selected model`() = runTest {
        every { modelRepository.availableModels } returns emptyList()
        every { modelRepository.getModelUiStates() } returns flowOf(
            listOf(modelState(isInstalled = true, isActive = true))
        )
        every { settingsDataStore.settings } returns flowOf(AppSettings(activeModelId = MODEL_ID))
        coEvery { modelRepository.deleteModel(MODEL_ID) } just runs
        coEvery { settingsDataStore.updateActiveModelId(any()) } just runs

        val viewModel = ModelsViewModel(modelRepository, settingsDataStore)
        advanceUntilIdle()

        viewModel.deleteModel(MODEL_ID)
        advanceUntilIdle()

        coVerify(exactly = 1) { modelRepository.deleteModel(MODEL_ID) }
        coVerify(atLeast = 1) { settingsDataStore.updateActiveModelId("") }
    }

    @Test
    fun `downloadModel recovers when install completed despite thrown exception`() = runTest {
        val model = modelState(isInstalled = false, isActive = false).model

        every { modelRepository.availableModels } returns listOf(model)
        every { modelRepository.getModelUiStates() } returns flowOf(emptyList())
        every { settingsDataStore.settings } returns flowOf(AppSettings())
        every { modelRepository.getInstallPath(MODEL_ID) } returns "C:/models/$MODEL_ID"
        every { modelRepository.isInstallComplete(MODEL_ID) } returnsMany listOf(false, false, true)
        coEvery { modelRepository.downloadModel(MODEL_ID) } throws IllegalStateException("false negative")
        coEvery { modelRepository.markAsInstalled(model, "C:/models/$MODEL_ID") } just runs

        val viewModel = ModelsViewModel(modelRepository, settingsDataStore)
        advanceUntilIdle()

        viewModel.downloadModel(MODEL_ID)
        advanceUntilIdle()

        coVerify(exactly = 1) { modelRepository.downloadModel(MODEL_ID) }
        coVerify(exactly = 1) { modelRepository.markAsInstalled(model, "C:/models/$MODEL_ID") }
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    private fun modelState(isInstalled: Boolean, isActive: Boolean) = ModelUiState(
        model = LLMModel(
            id = MODEL_ID,
            name = "Test Model",
            family = "llama",
            sizeBytes = 1_000_000L,
            downloadUrl = "asset://model",
            checksumSha256 = "",
            minRamMb = 1024,
            recommendedRamMb = 2048,
            contextLength = 2048,
            quantization = "Q4",
            tags = emptyList(),
            minAndroidApi = 28
        ),
        isInstalled = isInstalled,
        isActive = isActive
    )

    private companion object {
        const val MODEL_ID = "test-model-id"
    }
}
