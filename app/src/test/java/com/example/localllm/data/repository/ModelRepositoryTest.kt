package com.example.localllm.data.repository

import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.data.db.entity.InstalledModelEntity
import com.example.localllm.domain.model.LLMModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class ModelRepositoryTest {

    private val modelDao = mockk<ModelDao>(relaxed = true)
    // ApplicationContext is not strictly needed for the tested paths, pass a mock
    private val repository = ModelRepository(modelDao, mockk(relaxed = true))

    @Test
    fun `setActiveModel deactivates all others and sets the new one active`() = runTest {
        coEvery { modelDao.getModelById("test-model-id") } returns installedEntity(id = "test-model-id")

        // Act
        repository.setActiveModel("test-model-id")

        // Assert
        coVerify { modelDao.deactivateAll() }
        coVerify { modelDao.setActive("test-model-id") }
    }

    @Test
    fun `setActiveModel rejects unknown model ids`() = runTest {
        coEvery { modelDao.getModelById("missing-model") } returns null

        try {
            repository.setActiveModel("missing-model")
            fail("Expected setActiveModel to reject unknown model ids")
        } catch (_: IllegalStateException) {
            // Expected path.
        }

        coVerify(exactly = 0) { modelDao.deactivateAll() }
        coVerify(exactly = 0) { modelDao.setActive(any()) }
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
        coEvery { modelDao.getModelById("test_id") } returns null

        // Act
        repository.markAsInstalled(model, filePath)

        // Assert
        coVerify {
            modelDao.insert(match { 
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
        coEvery { modelDao.getModelById("test_id") } returns installedEntity(
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
            modelDao.insert(match {
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
    ) = InstalledModelEntity(
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
