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
import com.example.localllm.mlc.isInstalledMlcModelComplete
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import com.example.localllm.mlc.resolveMlcRedirectUrl
import com.example.localllm.mlc.resolveMlcModelAssetUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    @ApplicationContext private val context: Context
) {
    val availableModels: List<LLMModel>
        get() = gemmaModels

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
        onProgress: (Float) -> Unit = {}
    ): InstalledModel = withContext(Dispatchers.IO) {
        val model = availableModels.firstOrNull { it.id == modelId }
            ?: error("النموذج غير موجود في القائمة")

        ensureEnoughStorage(model)

        val modelDir = File(getInstallPath(modelId))
        val installedConfig = installModelFromUrl(
            model = model,
            modelDir = modelDir,
            onProgress = onProgress
        )
        val existing = modelDao.getModelById(modelId)
        val resolvedModel = model.copy(
            contextLength = installedConfig.contextWindowSize,
            quantization = installedConfig.quantization ?: model.quantization
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
        onProgress(1f)
        Timber.i("Installed MLC model %s at %s", modelId, modelDir.absolutePath)
        entity.toDomain()
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
        return (memInfo.availMem / 1_000_000).toInt()
    }

    fun getAvailableStorageMb(): Long {
        val stat = StatFs(installRootDir().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong / 1_000_000
    }

    private fun getAvailableStorageBytes(): Long {
        val stat = StatFs(installRootDir().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    private fun ensureEnoughStorage(model: LLMModel) {
        val availableBytes = getAvailableStorageBytes()
        require(availableBytes >= model.sizeBytes) {
            val requiredGb = "%.1f".format(model.sizeBytes / 1_000_000_000.0)
            val availableGb = "%.1f".format(availableBytes / 1_000_000_000.0)
            "المساحة غير كافية لتنزيل ${model.name}. المطلوب $requiredGb GB والمتاح $availableGb GB."
        }
    }

    private suspend fun installModelFromUrl(
        model: LLMModel,
        modelDir: File,
        onProgress: (Float) -> Unit
    ) = run {
        modelDir.mkdirs()

        val chatConfigFile = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
        val tensorCacheFile = File(modelDir, MLC_TENSOR_CACHE_FILENAME)

        downloadFileWithResume(
            url = resolveMlcModelAssetUrl(model.downloadUrl, MLC_MODEL_CONFIG_FILENAME),
            destination = chatConfigFile,
            onProgress = { _, _ -> onProgress(0.02f) }
        )
        downloadFileWithResume(
            url = resolveMlcModelAssetUrl(model.downloadUrl, MLC_TENSOR_CACHE_FILENAME),
            destination = tensorCacheFile,
            onProgress = { _, _ -> onProgress(0.05f) }
        )

        val chatConfig = readInstalledMlcChatConfig(modelDir)
        val tensorCache = readInstalledMlcTensorCache(modelDir)

        val downloadPlan = buildList {
            chatConfig.tokenizerFiles.forEach { relativePath ->
                add(
                    ModelAsset(
                        url = resolveMlcModelAssetUrl(model.downloadUrl, relativePath),
                        destination = File(modelDir, relativePath)
                    )
                )
            }

            tensorCache.records.forEach { tensorRecord ->
                add(
                    ModelAsset(
                        url = resolveMlcModelAssetUrl(model.downloadUrl, tensorRecord.dataPath),
                        destination = File(modelDir, tensorRecord.dataPath)
                    )
                )
            }
        }

        val totalBytes = downloadPlan.sumOf { asset ->
            resolveContentLength(asset.url).coerceAtLeast(existingBytes(asset.destination))
        }.coerceAtLeast(model.sizeBytes)

        var completedBytes = downloadPlan.sumOf { asset ->
            existingBytes(asset.destination).coerceAtMost(resolveContentLength(asset.url).takeIf { it > 0L } ?: Long.MAX_VALUE)
        }

        onProgress((completedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f))

        downloadPlan.forEach { asset ->
            val initialBytes = existingBytes(asset.destination)
            downloadFileWithResume(
                url = asset.url,
                destination = asset.destination
            ) { written, fileTotal ->
                val normalizedInitial = initialBytes.coerceAtMost(fileTotal.takeIf { it > 0L } ?: initialBytes)
                val currentCompleted = completedBytes - normalizedInitial + written
                val progress = if (totalBytes > 0L) {
                    currentCompleted.toDouble() / totalBytes.toDouble()
                } else {
                    0.0
                }
                onProgress(progress.toFloat().coerceIn(0f, 1f))
            }
            completedBytes = completedBytes - initialBytes + existingBytes(asset.destination)
            onProgress((completedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f))
        }

        chatConfig
    }

    private fun existingBytes(destination: File): Long {
        if (destination.exists()) return destination.length()
        val tempFile = File(checkNotNull(destination.parentFile), "${destination.name}.part")
        return if (tempFile.exists()) tempFile.length() else 0L
    }

    private suspend fun downloadFileWithResume(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        if (destination.exists()) {
            onProgress(destination.length(), destination.length())
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
        var outputStream: java.io.RandomAccessFile? = null

        try {
            while (redirectCount < 10) {
                connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                val resumeBytes = if (tempFile.exists()) tempFile.length() else 0L
                if (resumeBytes > 0L) {
                    connection.setRequestProperty("Range", "bytes=$resumeBytes-")
                }

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
                    inputStream = connection.inputStream
                    break
                } else {
                    error("Bad HTTP response: $responseCode for $currentUrl")
                }
            }

            if (inputStream == null) {
                error("Failed to connect or too many redirects")
            }

            val resumeBytes = if (tempFile.exists()) tempFile.length() else 0L
            if (!tempFile.exists() && !tempFile.createNewFile()) {
                error("Failed to create temp file for download: $tempFile")
            }

            val contentRange = connection?.getHeaderField("Content-Range")
            val contentLength = connection?.contentLengthLong ?: -1L
            val totalBytes = when {
                contentRange?.substringAfterLast('/')?.toLongOrNull() != null ->
                    contentRange.substringAfterLast('/').toLong()
                contentLength > 0L -> resumeBytes + contentLength
                else -> -1L
            }

            inputStream.use { input ->
                outputStream = java.io.RandomAccessFile(tempFile, "rw").apply {
                    seek(resumeBytes)
                }

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = resumeBytes

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    outputStream?.write(buffer, 0, read)
                    downloadedBytes += read
                    onProgress(downloadedBytes, totalBytes)
                }

                outputStream?.fd?.sync()
            }

            if (tempFile.exists()) {
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
                if (!destination.exists()) {
                    error("Downloaded file could not be finalized: $destination")
                }
            } else {
                error("Downloaded temp file was not created properly: $tempFile")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw error
        } finally {
            try { outputStream?.close() } catch (_: Exception) { }
            try { inputStream?.close() } catch (e: Exception) { }
            try { connection?.disconnect() } catch (e: Exception) { }
        }
    }

    private fun resolveContentLength(url: String): Long {
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < 10) {
            val connection = (java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val redirectLocation = connection.getHeaderField("Location")
                        ?: return -1L
                    currentUrl = resolveMlcRedirectUrl(currentUrl, redirectLocation)
                    redirectCount++
                    continue
                }

                return connection.contentLengthLong
            } catch (_: Exception) {
                return -1L
            } finally {
                connection.disconnect()
            }
        }

        return -1L
    }

    private fun installRootDir(): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    private companion object {
        val gemmaModels = listOf(
            LLMModel(
                id = "gemma-4-4b-it-q4f16_1-MLC",
                name = "Gemma 4 E4B",
                family = "gemma",
                sizeBytes = 3_600_000_000L,
                downloadUrl = "https://huggingface.co/mlc-ai/gemma-4-4b-it-q4f16_1-MLC",
                checksumSha256 = "",
                minRamMb = 6144,
                recommendedRamMb = 8192,
                contextLength = 8192,
                quantization = "Q4F16_1",
                tags = listOf("gemma-4", "on-device", "mlc"),
                minAndroidApi = 28
            ),
            LLMModel(
                id = "gemma-4-2b-it-q4f16_1-MLC",
                name = "Gemma 4 E2B",
                family = "gemma",
                sizeBytes = 2_600_000_000L,
                downloadUrl = "https://huggingface.co/mlc-ai/gemma-4-2b-it-q4f16_1-MLC",
                checksumSha256 = "",
                minRamMb = 4096,
                recommendedRamMb = 6144,
                contextLength = 8192,
                quantization = "Q4F16_1",
                tags = listOf("gemma-4", "on-device", "mlc"),
                minAndroidApi = 28
            )
        )
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

private data class ModelAsset(
    val url: String,
    val destination: File
)
