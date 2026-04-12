package com.example.localllm.mlc

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

const val MLC_APP_CONFIG_ASSET = "mlc-app-config.json"
const val MLC_MODEL_CONFIG_FILENAME = "mlc-chat-config.json"
const val MLC_TENSOR_CACHE_FILENAME = "tensor-cache.json"

private const val HUGGING_FACE_RESOLVE_PREFIX = "resolve/main/"

private val mlcJson = Json { ignoreUnknownKeys = true }

@Serializable
data class MlcAppConfig(
    @SerialName("model_list") val modelList: List<MlcModelRecord> = emptyList()
)

@Serializable
data class MlcModelRecord(
    @SerialName("model_url") val modelUrl: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("estimated_vram_bytes") val estimatedVramBytes: Long? = null,
    @SerialName("model_lib") val modelLib: String,
    @SerialName("model_backend") val modelBackend: String = "opencl"
)

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

fun loadBundledMlcAppConfig(context: Context): MlcAppConfig =
    context.assets.open(MLC_APP_CONFIG_ASSET).bufferedReader().use { reader ->
        mlcJson.decodeFromString<MlcAppConfig>(reader.readText())
    }

fun findBundledMlcModel(context: Context, modelId: String): MlcModelRecord? = runCatching {
    loadBundledMlcAppConfig(context).modelList.firstOrNull { it.modelId == modelId }
}.onFailure { error ->
    Timber.e(error, "Failed to read bundled MLC config for model %s", modelId)
}.getOrNull()

fun readInstalledMlcChatConfig(modelDir: File): MlcChatConfig =
    mlcJson.decodeFromString(File(modelDir, MLC_MODEL_CONFIG_FILENAME).readText())

fun readInstalledMlcTensorCache(modelDir: File): MlcTensorCache =
    mlcJson.decodeFromString(File(modelDir, MLC_TENSOR_CACHE_FILENAME).readText())

fun resolveMlcModelAssetUrl(modelUrl: String, relativePath: String): String {
    val normalizedBase = if (modelUrl.endsWith("/")) modelUrl else "$modelUrl/"
    return normalizedBase + HUGGING_FACE_RESOLVE_PREFIX + relativePath.removePrefix("/")
}
