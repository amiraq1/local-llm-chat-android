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
import com.example.localllm.mlc.isInstalledMlcModelComplete
import com.example.localllm.mlc.loadBundledMlcAppConfig
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import com.example.localllm.mlc.resolveMlcRedirectUrl
import com.example.localllm.mlc.resolveMlcModelAssetUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlcModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    @ApplicationContext private val context: Context,
    private val httpClient: okhttp3.OkHttpClient
) {
    private val modelDownloadMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

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
        val installedModel = modelDao.getModelById(modelId)
            ?: error("لا يمكن تفعيل نموذج غير مثبّت: $modelId")
        if (installedModel.isActive) {
            Timber.d("Active model already set to: $modelId")
            return
        }

        modelDao.deactivateAll()
        modelDao.setActive(modelId)
        Timber.d("Active model set to: $modelId")
    }

    suspend fun markAsInstalled(model: LLMModel, filePath: String) {
        val existing = modelDao.getModelById(model.id)
        modelDao.insert(
            InstalledModelEntity(
                id = model.id,
                name = model.name,
                family = model.family,
                sizeBytes = model.sizeBytes,
                filePath = filePath,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                checksumVerified = existing?.checksumVerified ?: false,
                isActive = existing?.isActive ?: false,
                quantization = existing?.quantization ?: model.quantization,
                contextLength = existing?.contextLength ?: model.contextLength
            )
        )
    }

    suspend fun downloadModel(
        modelId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): InstalledModel {
        val mutex = modelDownloadMutexes.getOrPut(modelId) { Mutex() }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val bundledModel = bundledModels.firstOrNull { it.uiModel.id == modelId }
                    ?: error("النموذج غير موجود في mlc-app-config.json")

                val modelDir = File(getInstallPath(modelId))
                val installedConfig = installBundledModel(bundledModel.manifest, modelDir, onProgress)
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
        }
    }

    suspend fun markChecksumVerified(modelId: String) =
        modelDao.setChecksumVerified(modelId, true)

    fun getModelsDir(): File {
        val dir = File(installRootDir(), "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstallPath(modelId: String): String =
        File(getModelsDir(), modelId).absolutePath

    fun isInstallComplete(modelId: String): Boolean =
        isInstalledMlcModelComplete(File(getInstallPath(modelId)))

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
        // Use totalMem instead of availMem to check hardware capability
        // since Android aggressively fills availMem with background apps.
        return (memInfo.totalMem / 1_000_000).toInt()
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

    private suspend fun installBundledModel(
        record: MlcModelRecord,
        modelDir: File,
        onProgress: (Long, Long) -> Unit
    ) =
        run {
            modelDir.mkdirs()

            val chatConfigFile = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
            val tensorCacheFile = File(modelDir, MLC_TENSOR_CACHE_FILENAME)

            val estimatedTotal = record.estimatedVramBytes ?: 0L
            var currentDownloaded = 0L

            downloadFileIfMissing(
                url = resolveMlcModelAssetUrl(record.modelUrl, MLC_MODEL_CONFIG_FILENAME),
                destination = chatConfigFile
            ) { downloaded, _ ->
                // Small file, just update progress
                onProgress(currentDownloaded + downloaded, estimatedTotal.coerceAtLeast(currentDownloaded + downloaded))
            }
            currentDownloaded += chatConfigFile.length()

            downloadFileIfMissing(
                url = resolveMlcModelAssetUrl(record.modelUrl, MLC_TENSOR_CACHE_FILENAME),
                destination = tensorCacheFile
            ) { downloaded, _ ->
                onProgress(currentDownloaded + downloaded, estimatedTotal.coerceAtLeast(currentDownloaded + downloaded))
            }
            currentDownloaded += tensorCacheFile.length()

            val chatConfig = readInstalledMlcChatConfig(modelDir)
            val tensorCache = readInstalledMlcTensorCache(modelDir)

            chatConfig.tokenizerFiles.forEach { relativePath ->
                val dest = File(modelDir, relativePath)
                downloadFileIfMissing(
                    url = resolveMlcModelAssetUrl(record.modelUrl, relativePath),
                    destination = dest
                ) { downloaded, _ ->
                    onProgress(currentDownloaded + downloaded, estimatedTotal.coerceAtLeast(currentDownloaded + downloaded))
                }
                currentDownloaded += dest.length()
            }

            tensorCache.records.forEach { tensorRecord ->
                val dest = File(modelDir, tensorRecord.dataPath)
                downloadFileIfMissing(
                    url = resolveMlcModelAssetUrl(record.modelUrl, tensorRecord.dataPath),
                    destination = dest
                ) { downloaded, _ ->
                    onProgress(currentDownloaded + downloaded, estimatedTotal.coerceAtLeast(currentDownloaded + downloaded))
                }
                currentDownloaded += dest.length()
            }

            onProgress(currentDownloaded, currentDownloaded)
            chatConfig
        }

    private suspend fun downloadFileIfMissing(
        url: String,
        destination: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        if (destination.exists()) {
            return
        }

        val parentDir = checkNotNull(destination.parentFile) {
            "Destination has no parent directory: $destination"
        }
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            error("Failed to create destination directory: $parentDir")
        }
        val tempFile = File(destination.parentFile, "${destination.name}.part")

        var currentUrl = url
        var redirectCount = 0
        var connection: java.net.HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null

        try {
            while (redirectCount < 10) {
                connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 60000

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val redirectLocation = connection.getHeaderField("Location")
                        ?: error("Redirect response missing Location header for $currentUrl")
                    currentUrl = resolveMlcRedirectUrl(currentUrl, redirectLocation)
                    connection.disconnect()
                    redirectCount++
                    continue
                }

                if (responseCode in 200..299) {
                    Timber.d("Starting download from $currentUrl (code $responseCode)")
                    inputStream = connection.inputStream
                    break
                } else {
                    error("Bad HTTP response: $responseCode for $currentUrl")
                }
            }

            if (inputStream == null) {
                error("Failed to connect or too many redirects")
            }

            tempFile.delete()
            Timber.d("Creating temp file: $tempFile")
            if (!tempFile.createNewFile()) {
                error("Failed to create temp file for download: $tempFile")
            }

            var bytesCopiedCount: Long = 0
            inputStream.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopiedCount += bytesRead
                        onProgress(bytesCopiedCount, -1L) // Total unknown here
                    }
                    output.flush()
                    output.fd.sync()
                    Timber.d("Downloaded $bytesCopiedCount bytes to $tempFile. Stream closed.")
                }
            }

            val finalTempExists = tempFile.exists()
            val finalTempSize = tempFile.length()
            Timber.d("Post-download check: exists=$finalTempExists, size=$finalTempSize")

            if (finalTempExists) {
                Timber.d("Renaming $tempFile to $destination")
                if (!tempFile.renameTo(destination)) {
                    Timber.w("Rename failed, attempting copy to $destination")
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                if (!destination.exists()) {
                    error("Downloaded file could not be finalized: $destination")
                }
            } else {
                error("Downloaded temp file was not created properly: $tempFile (Bytes copied: $bytesCopiedCount)")
            }
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        } finally {
            try { inputStream?.close() } catch (e: Exception) { }
            try { connection?.disconnect() } catch (e: Exception) { }
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
