package com.example.localllm.mlc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MlcConfigTest {

    @Test
    fun `resolveMlcRedirectUrl resolves relative Hugging Face redirects`() {
        val requestUrl =
            "https://huggingface.co/mlc-ai/Qwen3-0.6B-q0f16-MLC/resolve/main/mlc-chat-config.json"
        val redirectLocation =
            "/api/resolve-cache/models/mlc-ai/Qwen3-0.6B-q0f16-MLC/main/mlc-chat-config.json?etag=%2242%22"

        val resolvedUrl = resolveMlcRedirectUrl(requestUrl, redirectLocation)

        assertEquals(
            "https://huggingface.co/api/resolve-cache/models/mlc-ai/Qwen3-0.6B-q0f16-MLC/main/mlc-chat-config.json?etag=%2242%22",
            resolvedUrl
        )
    }

    @Test
    fun `isInstalledMlcModelComplete returns false for partial installs`() {
        val modelDir = Files.createTempDirectory("partial-mlc-model").toFile()
        writeText(
            File(modelDir, MLC_MODEL_CONFIG_FILENAME),
            """
            {
              "tokenizer_files": ["tokenizer.json"]
            }
            """.trimIndent()
        )
        writeText(
            File(modelDir, MLC_TENSOR_CACHE_FILENAME),
            """
            {
              "records": [{"dataPath": "params_shard_0.bin"}]
            }
            """.trimIndent()
        )

        assertFalse(isInstalledMlcModelComplete(modelDir))
    }

    @Test
    fun `isInstalledMlcModelComplete returns true when all required files exist`() {
        val modelDir = Files.createTempDirectory("complete-mlc-model").toFile()
        writeText(
            File(modelDir, MLC_MODEL_CONFIG_FILENAME),
            """
            {
              "tokenizer_files": ["tokenizer.json", "tokenizer_config.json"]
            }
            """.trimIndent()
        )
        writeText(
            File(modelDir, MLC_TENSOR_CACHE_FILENAME),
            """
            {
              "records": [{"dataPath": "params_shard_0.bin"}]
            }
            """.trimIndent()
        )
        writeText(File(modelDir, "tokenizer.json"), "{}")
        writeText(File(modelDir, "tokenizer_config.json"), "{}")
        writeText(File(modelDir, "params_shard_0.bin"), "weights")

        assertTrue(isInstalledMlcModelComplete(modelDir))
    }

    private fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
        file.deleteOnExit()
    }
}
