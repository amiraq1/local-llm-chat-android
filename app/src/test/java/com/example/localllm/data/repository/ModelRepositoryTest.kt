package com.example.localllm.data.repository

import android.content.Context
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.domain.model.LLMModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelRepositoryTest {

    private val db = mockk<AppDatabase>(relaxed = true)
    private val modelDao = mockk<ModelDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val repository = ModelRepository(db, modelDao, context)

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

    @Test
    fun `isCompatibleWith returns false when RAM is insufficient`() {
        val model = LLMModel(
            id = "test_id", name = "Test Model", family = "llama", sizeBytes = 2_000_000_000L,
            downloadUrl = "url", checksumSha256 = "hash", minRamMb = 4096,
            recommendedRamMb = 6144, contextLength = 2048, quantization = "Q4",
            tags = emptyList(), minAndroidApi = 28
        )

        assert(!repository.isCompatibleWith(model, ramMb = 2048, storageMb = 10_000L))
    }
}
