package com.example.localllm.data.repository

import com.example.localllm.domain.model.LLMModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ModelRepositoryTest {

    private val modelStore = mockk<ModelStore>(relaxed = true)
    private val repository = ModelRepository(modelStore, File("build/test-model-repository"))

    @Test
    fun `setActiveModel deactivates all others and sets the new one active`() = runTest {
        coEvery { modelStore.getModelById("test-model-id") } returns installedEntity(id = "test-model-id")

        // Act
        repository.setActiveModel("test-model-id")

        // Assert
        coVerify { modelStore.deactivateAll() }
        coVerify { modelStore.setActive("test-model-id") }
    }

    @Test
    fun `setActiveModel rejects unknown model ids`() = runTest {
        coEvery { modelStore.getModelById("missing-model") } returns null

        try {
            repository.setActiveModel("missing-model")
            fail("Expected setActiveModel to reject unknown model ids")
        } catch (_: IllegalStateException) {
            // Expected path.
        }

        coVerify(exactly = 0) { modelStore.deactivateAll() }
        coVerify(exactly = 0) { modelStore.setActive(any()) }
    }

    @Test
    fun `markAsInstalled inserts into ModelDao for a new install`() = runTest {
        // Arrange
        val model = LLMModel(
            id = "test_id", name = "Test Model", family = "llama", sizeBytes = 100L,
            downloadUrl = "url", checksumSha256 = "hash", minRamMb = 1024,
            recommendedRamMb = 2048, contextLength = 2048, quantization = "Q4",
            tags = emptyList(), minAndroidApi = 28
        )
        val filePath = "/test/path"
        coEvery { modelStore.getModelById("test_id") } returns null

        // Act
        repository.markAsInstalled(model, filePath)

        // Assert
        coVerify {
            modelStore.insert(match {
                it.id == "test_id" && it.filePath == "/test/path" && !it.isActive 
            })
        }
    }

    @Test
    fun `markAsInstalled preserves existing metadata during local sync`() = runTest {
        val model = LLMModel(
            id = "test_id", name = "Test Model", family = "llama", sizeBytes = 100L,
            downloadUrl = "url", checksumSha256 = "hash", minRamMb = 1024,
            recommendedRamMb = 2048, contextLength = 2048, quantization = "Q4",
            tags = emptyList(), minAndroidApi = 28
        )
        coEvery { modelStore.getModelById("test_id") } returns installedEntity(
            id = "test_id",
            filePath = "/old/path",
            installedAt = 1234L,
            checksumVerified = true,
            isActive = true,
            quantization = "Q4KM",
            contextLength = 4096
        )

        repository.markAsInstalled(model, "/new/path")

        coVerify {
            modelStore.insert(match {
                it.id == "test_id" &&
                    it.filePath == "/new/path" &&
                    it.installedAt == 1234L &&
                    it.checksumVerified &&
                    it.isActive &&
                    it.quantization == "Q4KM" &&
                    it.contextLength == 4096
            })
        }
    }

    private fun installedEntity(
        id: String,
        filePath: String = "/test/path",
        installedAt: Long = 0L,
        checksumVerified: Boolean = false,
        isActive: Boolean = false,
        quantization: String = "Q4",
        contextLength: Int = 2048
    ) = InstalledModelRecord(
        id = id,
        name = "Test Model",
        family = "llama",
        sizeBytes = 100L,
        filePath = filePath,
        installedAt = installedAt,
        checksumVerified = checksumVerified,
        isActive = isActive,
        quantization = quantization,
        contextLength = contextLength
    )
}
