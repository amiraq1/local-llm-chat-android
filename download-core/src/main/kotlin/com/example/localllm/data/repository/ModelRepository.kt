package com.example.localllm.data.repository

import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.LLMModel
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState
import com.example.localllm.mlc.MLC_MODEL_CONFIG_FILENAME
import com.example.localllm.mlc.MLC_TENSOR_CACHE_FILENAME
import com.example.localllm.mlc.isInstalledMlcModelComplete
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import com.example.localllm.mlc.resolveMlcModelAssetUrl
import com.example.localllm.mlc.resolveMlcRedirectUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

class ModelRepository(
    private val modelStore: ModelStore,
    private val installRootDir: File,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val availableStorageBytesProvider: () -> Long = { installRootDir.usableSpace },
    private val availableRamMbProvider: () -> Int = {
        (Runtime.getRuntime().maxMemory() / 1_000_000L).toInt()
    },
    private val logger: (String) -> Unit = {}
) {
    val availableModels: List<LLMModel>
        get() = gemmaModels

    fun getInstalledModels(): Flow<List<InstalledModel>> =
        modelStore.getAllInstalledModels().map { list -> list.map(InstalledModelRecord::toDomain) }

    fun getModelUiStates(): Flow<List<ModelUiState>> =
        modelStore.getAllInstalledModels().map { installedList ->
            val installedMap = installedList.associateBy { it.id }
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
        modelStore.getActiveModel()?.toDomain()

    suspend fun setActiveModel(modelId: String) {
        val installedModel = modelStore.getModelById(modelId)
            ?: error("لا يمكن تفعيل نموذج غير مثبّت: $modelId")
        if (installedModel.isActive) return

        modelStore.deactivateAll()
        modelStore.setActive(modelId)
    }

    suspend fun markAsInstalled(model: LLMModel, filePath: String) {
        val existing = modelStore.getModelById(model.id)
        modelStore.insert(
            InstalledModelRecord(
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
        val existing = modelStore.getModelById(modelId)
        val resolvedModel = model.copy(
            contextLength = installedConfig.contextWindowSize,
            quantization = installedConfig.quantization ?: model.quantization
        )

        val record = InstalledModelRecord(
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
        modelStore.insert(record)
        onProgress(model.sizeBytes, model.sizeBytes)
        logger("Installed model $modelId at ${modelDir.absolutePath}")
        record.toDomain()
    }

    suspend fun markChecksumVerified(modelId: String) =
        modelStore.setChecksumVerified(modelId, true)

    fun getModelsDir(): File {
        val dir = File(installRootDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstallPath(modelId: String): String =
        File(getModelsDir(), modelId).absolutePath

    fun isInstallComplete(modelId: String): Boolean =
        isInstalledMlcModelComplete(File(getInstallPath(modelId)))

    suspend fun deleteModel(modelId: String) {
        File(getInstallPath(modelId)).deleteRecursively()
        modelStore.deleteById(modelId)
        logger("Deleted model: $modelId")
    }

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

    fun getAvailableRamMb(): Int = availableRamMbProvider()

    fun getAvailableStorageMb(): Long = availableStorageBytesProvider() / 1_000_000L

    private fun getAvailableStorageBytes(): Long = availableStorageBytesProvider()

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
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) = run {
        modelDir.mkdirs()

        val chatConfigFile = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
        val tensorCacheFile = File(modelDir, MLC_TENSOR_CACHE_FILENAME)

        downloadFileWithResume(
            url = resolveMlcModelAssetUrl(model.downloadUrl, MLC_MODEL_CONFIG_FILENAME),
            destination = chatConfigFile,
            onProgress = { downloadedBytes, totalBytes ->
                onProgress(downloadedBytes, totalBytes.coerceAtLeast(model.sizeBytes))
            }
        )
        downloadFileWithResume(
            url = resolveMlcModelAssetUrl(model.downloadUrl, MLC_TENSOR_CACHE_FILENAME),
            destination = tensorCacheFile,
            onProgress = { downloadedBytes, totalBytes ->
                onProgress(downloadedBytes, totalBytes.coerceAtLeast(model.sizeBytes))
            }
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
            existingBytes(asset.destination).coerceAtMost(
                resolveContentLength(asset.url).takeIf { it > 0L } ?: Long.MAX_VALUE
            )
        }

        onProgress(completedBytes, totalBytes)

        downloadPlan.forEach { asset ->
            val initialBytes = existingBytes(asset.destination)
            downloadFileWithResume(asset.url, asset.destination) { written, fileTotal ->
                val normalizedInitial =
                    initialBytes.coerceAtMost(fileTotal.takeIf { it > 0L } ?: initialBytes)
                val currentCompleted = completedBytes - normalizedInitial + written
                onProgress(currentCompleted, totalBytes)
            }
            completedBytes = completedBytes - initialBytes + existingBytes(asset.destination)
            onProgress(completedBytes, totalBytes)
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

        val tempFile = File(parentDir, "${destination.name}.part")
        val resumeBytes = if (tempFile.exists()) tempFile.length() else 0L
        val response = openRequest(url, resumeBytes, headOnly = false)
        val totalBytes = resolveTotalBytes(response.headers["Content-Range"], response.bodyLength, resumeBytes)

        try {
            response.bodyStream.use { input ->
                if (!tempFile.exists() && !tempFile.createNewFile()) {
                    error("Failed to create temp file for download: $tempFile")
                }

                RandomAccessFile(tempFile, "rw").use { output ->
                    output.seek(resumeBytes)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = resumeBytes

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress(downloadedBytes, totalBytes)
                    }

                    output.fd.sync()
                }
            }

            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }
            check(destination.exists()) { "Downloaded file could not be finalized: $destination" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            response.close()
        }
    }

    private fun resolveTotalBytes(contentRange: String?, contentLength: Long, resumeBytes: Long): Long {
        val rangeTotal = contentRange?.substringAfterLast('/')?.toLongOrNull()
        return when {
            rangeTotal != null -> rangeTotal
            contentLength > 0L -> resumeBytes + contentLength
            else -> -1L
        }
    }

    private fun resolveContentLength(url: String): Long {
        val response = runCatching { openRequest(url, resumeBytes = 0L, headOnly = true) }.getOrNull()
            ?: return -1L
        return try {
            response.bodyLength
        } finally {
            response.close()
        }
    }

    private fun openRequest(url: String, resumeBytes: Long, headOnly: Boolean): HttpResponse {
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < 10) {
            val request = Request.Builder()
                .url(currentUrl)
                .apply {
                    if (headOnly) head() else get()
                    if (!headOnly && resumeBytes > 0L) {
                        header("Range", "bytes=$resumeBytes-")
                    }
                }
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isRedirect) {
                val redirectLocation = response.header("Location")
                    ?: error("Redirect response missing Location header for $currentUrl")
                response.close()
                currentUrl = resolveMlcRedirectUrl(currentUrl, redirectLocation)
                redirectCount++
                continue
            }

            if (!response.isSuccessful) {
                response.close()
                error("Bad HTTP response: ${response.code} for $currentUrl")
            }

            return HttpResponse(
                response = response,
                bodyStream = response.body?.byteStream() ?: InputStream.nullInputStream(),
                bodyLength = response.body?.contentLength()
                    ?: response.header("Content-Length")?.toLongOrNull()
                    ?: -1L,
                headers = response.headers.toMultimap().mapValues { it.value.lastOrNull().orEmpty() }
            )
        }

        error("Failed to connect or too many redirects")
    }

    internal suspend fun downloadFileForTest(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        downloadFileWithResume(url, destination, onProgress)
    }

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

interface ModelStore {
    fun getAllInstalledModels(): Flow<List<InstalledModelRecord>>
    suspend fun getModelById(id: String): InstalledModelRecord?
    suspend fun getActiveModel(): InstalledModelRecord?
    suspend fun insert(model: InstalledModelRecord)
    suspend fun deactivateAll()
    suspend fun setActive(id: String)
    suspend fun setChecksumVerified(id: String, verified: Boolean)
    suspend fun deleteById(id: String)
}

data class InstalledModelRecord(
    val id: String,
    val name: String,
    val family: String,
    val sizeBytes: Long,
    val filePath: String,
    val installedAt: Long,
    val checksumVerified: Boolean,
    val isActive: Boolean,
    val quantization: String,
    val contextLength: Int
)

private fun InstalledModelRecord.toDomain() = InstalledModel(
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

private data class HttpResponse(
    val response: okhttp3.Response,
    val bodyStream: java.io.InputStream,
    val bodyLength: Long,
    val headers: Map<String, String>
) {
    fun close() {
        bodyStream.close()
        response.close()
    }
}
