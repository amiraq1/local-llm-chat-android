package com.example.localllm.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ModelRepository
    private lateinit var tempDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        tempDir = File.createTempFile("models", "dir").apply {
            delete()
            mkdirs()
        }

        repository = ModelRepository(
            modelStore = object : ModelStore {
                override fun getAllInstalledModels(): Flow<List<InstalledModelRecord>> = flowOf(emptyList())
                override suspend fun getModelById(id: String): InstalledModelRecord? = null
                override suspend fun getActiveModel(): InstalledModelRecord? = null
                override suspend fun insert(model: InstalledModelRecord) = Unit
                override suspend fun deactivateAll() = Unit
                override suspend fun setActive(id: String) = Unit
                override suspend fun setChecksumVerified(id: String, verified: Boolean) = Unit
                override suspend fun deleteById(id: String) = Unit
            },
            installRootDir = tempDir
        )
    }

    @Test
    fun `verify range header is sent correctly for resumed download`() = runTest {
        val destination = File(tempDir, "gemma.bin")
        val partFile = File(tempDir, "${destination.name}.part")
        partFile.writeBytes(ByteArray(100))

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 100-121/122")
                .setBody("Remaining data content")
        )

        repository.downloadFileForTest(
            url = server.url("/gemma.bin").toString(),
            destination = destination
        )

        val recordedRequest = server.takeRequest()
        assertThat(recordedRequest.getHeader("Range")).isEqualTo("bytes=100-")
    }

    @After
    fun teardown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }
}
