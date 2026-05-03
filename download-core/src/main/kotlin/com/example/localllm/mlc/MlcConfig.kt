package com.example.localllm.mlc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

const val MLC_MODEL_CONFIG_FILENAME = "mlc-chat-config.json"
const val MLC_TENSOR_CACHE_FILENAME = "tensor-cache.json"
const val MLC_LEGACY_TENSOR_CACHE_FILENAME = "ndarray-cache.json"

private const val HUGGING_FACE_RESOLVE_PREFIX = "resolve/main/"

private val mlcJson = Json { ignoreUnknownKeys = true }

@Serializable
data class MlcChatConfig(
    @SerialName("model_type") val modelType: String? = null,
    @SerialName("quantization") val quantization: String? = null,
    @SerialName("context_window_size") val contextWindowSize: Int = 2048,
    @SerialName("tokenizer_files") val tokenizerFiles: List<String> = emptyList()
)

@Serializable
data class MlcTensorCache(
    @SerialName("records") val records: List<MlcTensorRecord> = emptyList()
)

@Serializable
data class MlcTensorRecord(
    @SerialName("dataPath") val dataPath: String
)

fun readInstalledMlcChatConfig(modelDir: File): MlcChatConfig =
    mlcJson.decodeFromString(File(modelDir, MLC_MODEL_CONFIG_FILENAME).readText())

fun readInstalledMlcTensorCache(modelDir: File): MlcTensorCache =
    mlcJson.decodeFromString(resolveInstalledMlcTensorCacheFile(modelDir).readText())

fun resolveMlcModelAssetUrl(modelUrl: String, relativePath: String): String {
    val normalizedBase = if (modelUrl.endsWith("/")) modelUrl else "$modelUrl/"
    return normalizedBase + HUGGING_FACE_RESOLVE_PREFIX + relativePath.removePrefix("/")
}

fun resolveMlcRedirectUrl(requestUrl: String, redirectLocation: String): String =
    URI(requestUrl).resolve(redirectLocation).toString()

fun isInstalledMlcModelComplete(modelDir: File): Boolean = runCatching {
    if (!modelDir.isDirectory) return false

    val chatConfigFile = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
    val tensorCacheFile = resolveInstalledMlcTensorCacheFileOrNull(modelDir)
    if (!chatConfigFile.isFile || tensorCacheFile?.isFile != true) {
        return false
    }

    val chatConfig = readInstalledMlcChatConfig(modelDir)
    val tensorCache = readInstalledMlcTensorCache(modelDir)

    val tokenizerFilesPresent = chatConfig.tokenizerFiles.all { relativePath ->
        File(modelDir, relativePath).isFile
    }
    val tensorFilesPresent = tensorCache.records.all { tensorRecord ->
        File(modelDir, tensorRecord.dataPath).isFile
    }

    tokenizerFilesPresent && tensorFilesPresent
}.getOrDefault(false)

fun ensureInstalledMlcTensorCacheAlias(modelDir: File): File? {
    val tensorCacheFile = File(modelDir, MLC_TENSOR_CACHE_FILENAME)
    if (tensorCacheFile.isFile) {
        return tensorCacheFile
    }

    val legacyTensorCacheFile = File(modelDir, MLC_LEGACY_TENSOR_CACHE_FILENAME)
    if (!legacyTensorCacheFile.isFile) {
        return null
    }

    legacyTensorCacheFile.copyTo(tensorCacheFile, overwrite = true)
    return tensorCacheFile
}

private fun resolveInstalledMlcTensorCacheFile(modelDir: File): File =
    resolveInstalledMlcTensorCacheFileOrNull(modelDir)
        ?: error("Missing tensor cache manifest in ${modelDir.absolutePath}")

private fun resolveInstalledMlcTensorCacheFileOrNull(modelDir: File): File? =
    ensureInstalledMlcTensorCacheAlias(modelDir)
        ?: File(modelDir, MLC_LEGACY_TENSOR_CACHE_FILENAME).takeIf(File::isFile)
