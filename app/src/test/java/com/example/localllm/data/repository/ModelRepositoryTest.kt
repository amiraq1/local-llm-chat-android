package com.example.localllm.data.repository

import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.domain.model.LLMModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelRepositoryTest {

    private val modelDao = mockk<ModelDao>(relaxed = true)
    // ApplicationContext is not strictly needed for the tested paths, pass a mock
    private val repository = ModelRepository(modelDao, mockk(relaxed = true))

    @Test
    fun `setActiveModel deactivates all others and sets the new one active`() = runTest {
        // Act
        repository.setActiveModel("test-model-id")

        // Assert
        coVerify { modelDao.deactivateAll() }
        coVerify { modelDao.setActive("test-model-id") }
    }

    @Test
    fun `markAsInstalled inserts into ModelDao`() = runTest {
        // Arrange
        val model = LLMModel(
            id = "test_id", name = "Test Model", family = "llama", sizeBytes = 100L,
            downloadUrl = "url", checksumSha256 = "hash", minRamMb = 1024,
            recommendedRamMb = 2048, contextLength = 2048, quantization = "Q4",
            tags = emptyList(), minAndroidApi = 28
        )
        val filePath = "/test/path"

        // Act
        repository.markAsInstalled(model, filePath)

        // Assert
        coVerify {
            modelDao.insert(match { 
                it.id == "test_id" && it.filePath == "/test/path" && !it.isActive 
            })
        }
    }
}
