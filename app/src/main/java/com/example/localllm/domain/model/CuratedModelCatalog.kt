package com.example.localllm.domain.model

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.localllm.mlc.MlcModelRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.max

private const val MODEL_CATALOG_ASSET = "model_catalog.json"

private val catalogJson = Json { ignoreUnknownKeys = true }

@Serializable
enum class DeviceTier { ENTRY, MID, PRO, DESKTOP }

@Serializable
@Immutable
data class CatalogModel(
    val slug: String,
    val name: String,
    val family: String,
    val provider: String,
    val sizeGb: Double,
    val description: String,
    val tags: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val contextLength: Int = 8192,
    val quantization: String = "Q4",
    val mlcModelId: String? = null,
    val mlcModelUrl: String? = null,
    val mlcModelLib: String? = null,
    val checksumSha256: String = "",
    val minAndroidApi: Int = 28,
    val isRecommended: Boolean = false
) {
    val sizeBytes: Long
        get() = (sizeGb * 1_000_000_000L).toLong()

    val deviceTier: DeviceTier
        get() = when {
            sizeGb <= 1.2 -> DeviceTier.ENTRY
            sizeGb <= 3.0 -> DeviceTier.MID
            sizeGb <= 6.0 -> DeviceTier.PRO
            else -> DeviceTier.DESKTOP
        }

    val iconKey: String
        get() = when (family.lowercase()) {
            "aamil" -> "language"
            "gemma" -> "diamond"
            "qwen" -> "auto_awesome"
            "llama" -> "pets"
            "phi" -> "science"
            "mistral" -> "air"
            "deepseek" -> "psychology"
            "bitnet" -> "memory"
            "glm" -> "bolt"
            else -> "memory"
        }

    val hasMlcBinding: Boolean
        get() = !mlcModelUrl.isNullOrBlank() && !mlcModelLib.isNullOrBlank()

    fun toLlmModel(): LLMModel = LLMModel(
        id = slug,
        name = name,
        family = family,
        sizeBytes = sizeBytes,
        downloadUrl = mlcModelUrl.orEmpty(),
        checksumSha256 = checksumSha256,
        minRamMb = estimateMinRamMb(sizeGb),
        recommendedRamMb = estimateRecommendedRamMb(sizeGb),
        contextLength = contextLength,
        quantization = quantization,
        tags = buildList {
            addAll(tags)
            if (isRecommended) add("موصى به")
        }.distinct(),
        minAndroidApi = minAndroidApi
    )

    fun toMlcModelRecord(): MlcModelRecord? {
        if (!hasMlcBinding) return null
        return MlcModelRecord(
            modelUrl = mlcModelUrl.orEmpty(),
            modelId = mlcModelId ?: slug,
            estimatedVramBytes = sizeBytes,
            modelLib = mlcModelLib.orEmpty(),
            modelBackend = "opencl"
        )
    }
}

@Immutable
data class CatalogFilters(
    val query: String = "",
    val family: String? = null,
    val capability: String? = null,
    val maxSizeGb: Double? = null,
    val deviceTier: DeviceTier? = null,
    val recommendedOnly: Boolean = false
)

fun List<CatalogModel>.applyCatalogFilters(filters: CatalogFilters): List<CatalogModel> {
    val q = filters.query.trim().lowercase()
    val family = filters.family?.trim()?.lowercase()
    val capability = filters.capability?.trim()?.lowercase()

    return asSequence()
        .filter { model ->
            if (family != null && model.family.lowercase() != family) return@filter false
            if (capability != null && model.capabilities.none { it.lowercase() == capability }) return@filter false
            if (filters.maxSizeGb != null && model.sizeGb > filters.maxSizeGb) return@filter false
            if (filters.deviceTier != null && model.deviceTier != filters.deviceTier) return@filter false
            if (filters.recommendedOnly && !model.isRecommended) return@filter false
            if (q.isBlank()) return@filter true

            listOf(model.name, model.family, model.provider, model.description)
                .any { it.lowercase().contains(q) } ||
                model.tags.any { it.lowercase().contains(q) } ||
                model.capabilities.any { it.lowercase().contains(q) }
        }
        .sortedWith(
            compareByDescending<CatalogModel> { it.isRecommended }
                .thenBy { it.sizeGb }
                .thenBy { it.name }
        )
        .toList()
}

private fun estimateMinRamMb(sizeGb: Double): Int =
    max(2048, ceil(sizeGb * 1.6 * 1024).toInt())

private fun estimateRecommendedRamMb(sizeGb: Double): Int =
    max(estimateMinRamMb(sizeGb) + 1024, ceil(sizeGb * 2.4 * 1024).toInt())

object CuratedModelCatalog {
    fun load(context: Context): List<CatalogModel> = runCatching {
        context.assets.open(MODEL_CATALOG_ASSET).bufferedReader().use { reader ->
            catalogJson.decodeFromString<List<CatalogModel>>(reader.readText())
        }
    }.onFailure { error ->
        Timber.e(error, "Failed to load %s", MODEL_CATALOG_ASSET)
    }.getOrDefault(emptyList())
}
