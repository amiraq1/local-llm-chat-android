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
import com.example.localllm.mlc.MLC_MODEL_CONFIG_FILENAME
import com.example.localllm.mlc.MLC_TENSOR_CACHE_FILENAME
import com.example.localllm.mlc.MlcModelRecord
import com.example.localllm.mlc.loadBundledMlcAppConfig
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import com.example.localllm.mlc.resolveMlcModelAssetUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    @ApplicationContext private val context: Context
) {

    private val bundledModels: List<BundledMlcModel> by lazy(LazyThreadSafetyMode.NONE) {
        loadBundledModels()
    }

    val availableModels: List<LLMModel>
        get() = bundledModels.map(BundledMlcModel::uiModel)

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

    suspend fun downloadModel(modelId: String): InstalledModel = withContext(Dispatchers.IO) {
        val bundledModel = bundledModels.firstOrNull { it.uiModel.id == modelId }
            ?: error("النموذج غير موجود في mlc-app-config.json")

        val modelDir = File(getInstallPath(modelId))
        val installedConfig = installBundledModel(bundledModel.manifest, modelDir)
        val existing = modelDao.getModelById(modelId)
        val resolvedModel = bundledModel.uiModel.copy(
            contextLength = installedConfig.contextWindowSize,
            quantization = installedConfig.quantization ?: bundledModel.uiModel.quantization
        )

        val entity = InstalledModelEntity(
            id = resolvedModel.id,
            name = resolvedModel.name,
            family = resolvedModel.family,
            sizeBytes = resolvedModel.sizeBytes,
            filePath = modelDir.absolutePath,
            installedAt = existing?.installedAt ?: System.currentTimeMillis(),
            checksumVerified = false,
            isActive = existing?.isActive ?: false,
            quantization = resolvedModel.quantization,
            contextLength = resolvedModel.contextLength
        )
        modelDao.insert(entity)
        Timber.i("Installed MLC model %s at %s", modelId, modelDir.absolutePath)
        entity.toDomain()
    }

    suspend fun markChecksumVerified(modelId: String) =
        modelDao.setChecksumVerified(modelId, true)

    fun getInstallPath(modelId: String): String =
        File(installRootDir(), modelId).absolutePath

    suspend fun deleteModel(modelId: String) {
        File(getInstallPath(modelId)).deleteRecursively()
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
        val stat = StatFs(installRootDir().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong / 1_000_000
    }

    private fun loadBundledModels(): List<BundledMlcModel> = runCatching {
        loadBundledMlcAppConfig(context).modelList.map(::toBundledModel)
    }.onFailure { error ->
        Timber.e(error, "Failed to load bundled mlc-app-config.json")
    }.getOrDefault(emptyList())

    private fun toBundledModel(record: MlcModelRecord): BundledMlcModel {
        val modelSlug = record.modelId.removeSuffix("-MLC")
        val quantizationSegment = modelSlug.substringAfterLast('-', missingDelimiterValue = "")
        val quantization = if (quantizationSegment.startsWith("q", ignoreCase = true)) {
            quantizationSegment.uppercase()
        } else {
            "MLC"
        }
        val displayBase = if (quantizationSegment.startsWith("q", ignoreCase = true)) {
            modelSlug.removeSuffix("-$quantizationSegment")
        } else {
            modelSlug
        }
        val estimatedBytes = record.estimatedVramBytes ?: 0L
        val minRamMb = max(2048, (estimatedBytes / 1_000_000L).toInt())
        val recommendedRamMb = max(minRamMb + 1024, minRamMb * 2)

        return BundledMlcModel(
            uiModel = LLMModel(
                id = record.modelId,
                name = displayBase.replace('-', ' '),
                family = displayBase.substringBefore('-').lowercase(),
                sizeBytes = estimatedBytes,
                downloadUrl = record.modelUrl,
                checksumSha256 = "",
                minRamMb = minRamMb,
                recommendedRamMb = recommendedRamMb,
                contextLength = 2048,
                quantization = quantization,
                tags = listOf("mlc", "on-device"),
                minAndroidApi = 28
            ),
            manifest = record
        )
    }

    private fun installBundledModel(record: MlcModelRecord, modelDir: File) =
        run {
            modelDir.mkdirs()

            val chatConfigFile = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
            val tensorCacheFile = File(modelDir, MLC_TENSOR_CACHE_FILENAME)

            downloadFileIfMissing(
                url = resolveMlcModelAssetUrl(record.modelUrl, MLC_MODEL_CONFIG_FILENAME),
                destination = chatConfigFile
            )
            downloadFileIfMissing(
                url = resolveMlcModelAssetUrl(record.modelUrl, MLC_TENSOR_CACHE_FILENAME),
                destination = tensorCacheFile
            )

            val chatConfig = readInstalledMlcChatConfig(modelDir)
            val tensorCache = readInstalledMlcTensorCache(modelDir)

            chatConfig.tokenizerFiles.forEach { relativePath ->
                downloadFileIfMissing(
                    url = resolveMlcModelAssetUrl(record.modelUrl, relativePath),
                    destination = File(modelDir, relativePath)
                )
            }

            tensorCache.records.forEach { tensorRecord ->
                downloadFileIfMissing(
                    url = resolveMlcModelAssetUrl(record.modelUrl, tensorRecord.dataPath),
                    destination = File(modelDir, tensorRecord.dataPath)
                )
            }

            chatConfig
        }

    private fun downloadFileIfMissing(url: String, destination: File) {
        if (destination.exists()) {
            return
        }

        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.part")

        URL(url).openStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }
    }

    private fun installRootDir(): File =
        context.getExternalFilesDir(null) ?: context.filesDir
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

private data class BundledMlcModel(
    val uiModel: LLMModel,
    val manifest: MlcModelRecord
)
