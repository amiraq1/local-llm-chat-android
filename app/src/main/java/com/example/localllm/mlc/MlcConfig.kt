package com.example.localllm.mlc

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber

const val MLC_APP_CONFIG_ASSET = "mlc-app-config.json"

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

fun loadBundledMlcAppConfig(context: Context): MlcAppConfig =
    context.assets.open(MLC_APP_CONFIG_ASSET).bufferedReader().use { reader ->
        mlcJson.decodeFromString<MlcAppConfig>(reader.readText())
    }

fun findBundledMlcModel(context: Context, modelId: String): MlcModelRecord? = runCatching {
    loadBundledMlcAppConfig(context).modelList.firstOrNull { it.modelId == modelId }
}.onFailure { error ->
    Timber.e(error, "Failed to read bundled MLC config for model %s", modelId)
}.getOrNull()
