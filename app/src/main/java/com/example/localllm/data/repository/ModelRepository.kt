package com.example.localllm.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.data.db.entity.InstalledModelEntity
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.LLMModel
import com.example.localllm.domain.model.ModelUiState
import com.example.localllm.domain.model.ModelDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    @ApplicationContext private val context: Context
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Hard-coded demo catalogue — replace with remote manifest fetch in production. */
    val availableModels: List<LLMModel> = listOf(
        LLMModel(
            id = "llama-3.2-1b-q4",
            name = "Llama 3.2 1B (Q4_0)",
            family = "llama",
            sizeBytes = 756_000_000L,
            downloadUrl = "https://example.com/models/llama-3.2-1b-q4.tar",
            checksumSha256 = "abc123placeholder",
            minRamMb = 2048,
            recommendedRamMb = 4096,
            contextLength = 4096,
            quantization = "Q4_0",
            tags = listOf("fast", "lightweight"),
            minAndroidApi = 28
        ),
        LLMModel(
            id = "phi-3-mini-q4",
            name = "Phi-3 Mini 3.8B (Q4_K_M)",
            family = "phi",
            sizeBytes = 2_200_000_000L,
            downloadUrl = "https://example.com/models/phi-3-mini-q4.tar",
            checksumSha256 = "def456placeholder",
            minRamMb = 4096,
            recommendedRamMb = 6144,
            contextLength = 4096,
            quantization = "Q4_K_M",
            tags = listOf("balanced", "coding"),
            minAndroidApi = 28
        ),
        LLMModel(
            id = "gemma-2-2b-q4",
            name = "Gemma 2 2B (Q4_0)",
            family = "gemma",
            sizeBytes = 1_500_000_000L,
            downloadUrl = "https://example.com/models/gemma-2-2b-q4.tar",
            checksumSha256 = "ghi789placeholder",
            minRamMb = 3072,
            recommendedRamMb = 6144,
            contextLength = 8192,
            quantization = "Q4_0",
            tags = listOf("google", "multilingual"),
            minAndroidApi = 28
        ),
        LLMModel(
            id = "mistral-7b-q4",
            name = "Mistral 7B (Q4_K_M)",
            family = "mistral",
            sizeBytes = 4_400_000_000L,
            downloadUrl = "https://example.com/models/mistral-7b-q4.tar",
            checksumSha256 = "jkl012placeholder",
            minRamMb = 6144,
            recommendedRamMb = 8192,
            contextLength = 8192,
            quantization = "Q4_K_M",
            tags = listOf("powerful", "large"),
            minAndroidApi = 28
        )
    )

    // ─── Installed Model Queries ───────────────────────────────────────────────

    fun getInstalledModels(): Flow<List<InstalledModel>> =
        modelDao.getAllInstalledModels().map { it.map(InstalledModelEntity::toDomain) }

    fun getModelUiStates(): Flow<List<ModelUiState>> =
        modelDao.getAllInstalledModels().map { installedList ->
            val installedMap = installedList.associateBy { it.id }
            // Cache device specs to avoid repeated system calls per-model
            val ramMb = getAvailableRamMb()
            val storageMb = getAvailableStorageMb()
            availableModels.map { model ->
                val installed = installedMap[model.id]
                ModelUiState(
                    model = model,
                    downloadState = if (installed != null) ModelDownloadState.INSTALLED
                                   else ModelDownloadState.NOT_DOWNLOADED,
                    isInstalled = installed != null,
                    isActive = installed?.isActive ?: false,
                    isCompatible = isCompatibleWith(model, ramMb, storageMb),
                    incompatibilityReason = getIncompatibilityReasonWith(model, ramMb, storageMb)
                )
            }
        }

    suspend fun getActiveModel(): InstalledModel? =
        modelDao.getActiveModel()?.toDomain()

    suspend fun setActiveModel(modelId: String) {
        modelDao.deactivateAll()
        modelDao.setActive(modelId)
        Timber.d("Active model set to: $modelId")
    }

    suspend fun markAsInstalled(model: LLMModel, filePath: String) {
        modelDao.insert(
            InstalledModelEntity(
                id = model.id,
                name = model.name,
                family = model.family,
                sizeBytes = model.sizeBytes,
                filePath = filePath,
                checksumVerified = false,
                isActive = false,
                quantization = model.quantization,
                contextLength = model.contextLength
            )
        )
    }

    suspend fun markChecksumVerified(modelId: String) =
        modelDao.setChecksumVerified(modelId, true)

    fun getInstallPath(modelId: String): String =
        File(context.filesDir, "models/$modelId.bin").absolutePath

    suspend fun deleteModel(modelId: String) {
        modelDao.deleteById(modelId)
        Timber.d("Deleted model: $modelId")
    }

    // ─── Device Compatibility ─────────────────────────────────────────────────

    fun isCompatible(model: LLMModel): Boolean =
        isCompatibleWith(model, getAvailableRamMb(), getAvailableStorageMb())

    fun isCompatibleWith(model: LLMModel, ramMb: Int, storageMb: Long): Boolean =
        ramMb >= model.minRamMb && storageMb >= (model.sizeBytes / 1_000_000)

    fun getIncompatibilityReason(model: LLMModel): String? =
        getIncompatibilityReasonWith(model, getAvailableRamMb(), getAvailableStorageMb())

    fun getIncompatibilityReasonWith(model: LLMModel, ramMb: Int, storageMb: Long): String? {
        return when {
            ramMb < model.minRamMb ->
                "RAM غير كافية: ${ramMb}MB متاح، ${model.minRamMb}MB مطلوب"
            storageMb < (model.sizeBytes / 1_000_000) ->
                "تخزين غير كافٍ: ${storageMb}MB متاح، ${model.sizeBytes / 1_000_000}MB مطلوب"
            else -> null
        }
    }

    fun getAvailableRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.availMem / 1_000_000).toInt()
    }

    fun getAvailableStorageMb(): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong / 1_000_000
    }
}

// ─── Mapper ───────────────────────────────────────────────────────────────────

fun InstalledModelEntity.toDomain() = InstalledModel(
    id = id,
    name = name,
    family = family,
    sizeBytes = sizeBytes,
    filePath = filePath,
    installedAt = installedAt,
    checksumVerified = checksumVerified,
    isActive = isActive,
    quantization = quantization,
    contextLength = contextLength
)
