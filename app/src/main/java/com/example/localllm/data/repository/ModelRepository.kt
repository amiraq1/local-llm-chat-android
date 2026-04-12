package com.example.localllm.data.repository

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.room.withTransaction
import androidx.documentfile.provider.DocumentFile
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.data.db.entity.InstalledModelEntity
import com.example.localllm.domain.model.CatalogModel
import com.example.localllm.domain.model.CuratedModelCatalog
import com.example.localllm.domain.model.InstalledModel
import com.example.localllm.domain.model.LLMModel
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState
import com.example.localllm.mlc.MLC_MODEL_CONFIG_FILENAME
import com.example.localllm.mlc.MLC_TENSOR_CACHE_FILENAME
import com.example.localllm.mlc.MlcChatConfig
import com.example.localllm.mlc.MlcModelRecord
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import com.example.localllm.mlc.resolveMlcModelAssetUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val db: AppDatabase,
    private val modelDao: ModelDao,
    @ApplicationContext private val context: Context
) {

    private val installMutex = Mutex()

    private val _downloadProgress = MutableStateFlow<Map<String, ModelDownloadProgress>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val catalogModels: List<CatalogModel> by lazy(LazyThreadSafetyMode.NONE) {
        CuratedModelCatalog.load(context)
    }

    val availableModels: List<LLMModel>
        get() = catalogModels.map(CatalogModel::toLlmModel)

    fun getInstalledModels(): Flow<List<InstalledModel>> =
        modelDao.getAllInstalledModels().map { it.map(InstalledModelEntity::toDomain) }

    fun getModelUiStates(): Flow<List<ModelUiState>> =
        combine(
            modelDao.getAllInstalledModels(),
            _downloadProgress
        ) { installedList, progressMap ->
            val installedMap = installedList.associateBy { it.id }
            val ramMb = getAvailableRamMb()
            val storageMb = getAvailableStorageMb()

            availableModels.map { model ->
                val installed = installedMap[model.id]
                val progress = progressMap[model.id]

                ModelUiState(
                    model = model,
                    downloadState = when {
                        installed != null -> ModelDownloadState.INSTALLED
                        progress != null -> progress.state
                        else -> ModelDownloadState.NOT_DOWNLOADED
                    },
                    downloadProgress = progress?.progress ?: 0f,
                    isInstalled = installed != null,
                    isActive = installed?.isActive ?: false,
                    isCompatible = isCompatibleWith(model, ramMb, storageMb),
                    incompatibilityReason = getIncompatibilityReasonWith(model, ramMb, storageMb)
                )
            }
        }

    suspend fun getActiveModel(): InstalledModel? =
        modelDao.getActiveModel()?.toDomain()

    suspend fun ensureActiveModel(preferredModelId: String): InstalledModel? = withContext(Dispatchers.IO) {
        syncDiscoveredModels()

        modelDao.getActiveModel()?.toDomain()?.let { return@withContext it }

        modelDao.getModelById(preferredModelId)?.let { preferred ->
            setActiveModel(preferred.id)
            return@withContext preferred.copy(isActive = true).toDomain()
        }

        modelDao.getLatestInstalledModel()?.let { fallback ->
            setActiveModel(fallback.id)
            return@withContext fallback.copy(isActive = true).toDomain()
        }

        null
    }

    suspend fun setActiveModel(modelId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            modelDao.deactivateAll()
            modelDao.setActive(modelId)
        }
        Timber.d("Active model set to: %s", modelId)
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
                quantization = model.quantization,
                contextLength = model.contextLength
            )
        )
    }

    suspend fun syncDiscoveredModels() = withContext(Dispatchers.IO) {
        availableModels.forEach { model ->
            val path = getInstallPath(model.id)
            if (isInstalledModelDir(File(path))) {
                markAsInstalled(model, path)
            }
        }
    }

    suspend fun downloadModel(modelId: String): InstalledModel = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val catalogModel = catalogModels.firstOrNull { it.slug == modelId }
                ?: error("النموذج غير موجود في الكتالوج")

            val targetDir = File(getInstallPath(modelId))
            val stagingDir = File(targetDir.parentFile, "${targetDir.name}.staging")
            val existing = modelDao.getModelById(modelId)

            updateDownloadState(modelId, ModelDownloadState.DOWNLOADING, 0f)

            try {
                deleteDirectorySafely(stagingDir)
                stagingDir.mkdirs()

                val resolvedModel = when {
                    isInstalledModelDir(targetDir) -> {
                        Timber.d("Using existing local model folder for %s", modelId)
                        catalogModel.toLlmModel()
                    }

                    else -> {
                        val record = catalogModel.toMlcModelRecord()
                            ?: error(buildLocalImportMessage(modelId))

                        val installedConfig = installBoundModel(
                            record = record,
                            modelDir = stagingDir,
                            totalExpectedSize = catalogModel.sizeBytes,
                            onProgress = { progress ->
                                updateDownloadState(modelId, ModelDownloadState.DOWNLOADING, progress)
                            }
                        )

                        catalogModel.toLlmModel().copy(
                            contextLength = installedConfig.contextWindowSize,
                            quantization = installedConfig.quantization ?: catalogModel.quantization
                        )
                    }
                }

                updateDownloadState(modelId, ModelDownloadState.VERIFYING, 0.99f)

                if (!isInstalledModelDir(targetDir)) {
                    replaceDirectoryAtomically(stagingDir, targetDir)
                }

                val entity = InstalledModelEntity(
                    id = resolvedModel.id,
                    name = resolvedModel.name,
                    family = resolvedModel.family,
                    sizeBytes = resolvedModel.sizeBytes,
                    filePath = targetDir.absolutePath,
                    installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                    checksumVerified = false,
                    isActive = existing?.isActive ?: false,
                    quantization = resolvedModel.quantization,
                    contextLength = resolvedModel.contextLength
                )

                db.withTransaction {
                    modelDao.insert(entity)
                }

                Timber.i("Model %s is available at %s", modelId, targetDir.absolutePath)
                clearDownloadState(modelId)
                entity.toDomain()
            } catch (error: Throwable) {
                deleteDirectorySafely(stagingDir)
                updateDownloadState(modelId, ModelDownloadState.ERROR, 0f)
                Timber.e(error, "Failed to download/install model %s", modelId)
                throw error
            }
        }
    }

    suspend fun importModelFromTree(
        modelId: String,
        treeUri: Uri
    ): InstalledModel = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val catalogModel = requireCatalogModel(modelId)
            val sourceRoot = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("تعذر فتح المجلد المحدد")

            val sourceDir = resolveSelectedModelDirectory(sourceRoot, modelId)
            val targetDir = File(getInstallPath(modelId))
            val stagingDir = File(targetDir.parentFile, "${targetDir.name}.import_staging")
            val existing = modelDao.getModelById(modelId)

            try {
                deleteDirectorySafely(stagingDir)
                stagingDir.mkdirs()

                val copiedFiles = copyDocumentTreeContents(sourceDir, stagingDir)
                if (copiedFiles == 0) {
                    error("المجلد المحدد فارغ أو لا يمكن قراءة ملفاته")
                }

                if (!isInstalledModelDir(stagingDir)) {
                    error("المجلد المحدد لا يحتوي بنية نموذج صالحة")
                }

                replaceDirectoryAtomically(
                    sourceDir = stagingDir,
                    targetDir = targetDir
                )

                val entity = InstalledModelEntity(
                    id = catalogModel.slug,
                    name = catalogModel.name,
                    family = catalogModel.family,
                    sizeBytes = catalogModel.sizeBytes,
                    filePath = targetDir.absolutePath,
                    installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                    checksumVerified = existing?.checksumVerified ?: false,
                    isActive = existing?.isActive ?: false,
                    quantization = catalogModel.quantization,
                    contextLength = catalogModel.contextLength
                )

                db.withTransaction {
                    modelDao.insert(entity)
                }

                Timber.i("Imported local model %s from %s", modelId, treeUri)
                entity.toDomain()
            } catch (error: Throwable) {
                deleteDirectorySafely(stagingDir)
                Timber.e(error, "Failed to import model %s", modelId)
                throw error
            }
        }
    }

    suspend fun markChecksumVerified(modelId: String) =
        modelDao.setChecksumVerified(modelId, true)

    fun getModelsDir(): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInstallPath(modelId: String): String =
        File(getModelsDir(), modelId).absolutePath

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            val targetDir = File(getInstallPath(modelId))
            db.withTransaction {
                modelDao.deleteById(modelId)
            }
            deleteDirectorySafely(targetDir)
            Timber.d("Deleted model: %s", modelId)
        }
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

    fun getAvailableRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.availMem / 1_000_000).toInt()
    }

    fun getAvailableStorageMb(): Long {
        val stat = StatFs(getModelsDir().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong / 1_000_000
    }

    private fun updateDownloadState(modelId: String, state: ModelDownloadState, progress: Float) {
        _downloadProgress.update { current ->
            current + (modelId to ModelDownloadProgress(state, progress))
        }
    }

    private fun clearDownloadState(modelId: String) {
        _downloadProgress.update { current ->
            current - modelId
        }
    }

    private fun deleteDirectorySafely(dir: File) {
        if (dir.exists()) dir.deleteRecursively()
    }

    private fun replaceDirectoryAtomically(sourceDir: File, targetDir: File) {
        val backupDir = File(targetDir.parentFile, "${targetDir.name}.backup")

        deleteDirectorySafely(backupDir)

        if (targetDir.exists()) {
            if (!targetDir.renameTo(backupDir)) {
                targetDir.copyRecursively(backupDir, overwrite = true)
                deleteDirectorySafely(targetDir)
            }
        }

        if (!sourceDir.renameTo(targetDir)) {
            sourceDir.copyRecursively(targetDir, overwrite = true)
            deleteDirectorySafely(sourceDir)
        }

        deleteDirectorySafely(backupDir)
    }

    private fun isInstalledModelDir(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false

        val hasChatConfig = File(modelDir, MLC_MODEL_CONFIG_FILENAME).exists()
        val hasTensorCache = File(modelDir, MLC_TENSOR_CACHE_FILENAME).exists()
        val hasAnyFiles = !modelDir.list().isNullOrEmpty()

        return (hasChatConfig && hasTensorCache) || hasAnyFiles
    }

    private fun resolveSelectedModelDirectory(root: DocumentFile, modelId: String): DocumentFile {
        if (!root.exists() || !root.canRead()) {
            error("لا يمكن قراءة المجلد المحدد")
        }

        if (!root.isDirectory) {
            error("يرجى اختيار مجلد نموذج وليس ملفًا")
        }

        val matchingChild = root.findFile(modelId)
            ?.takeIf { it.isDirectory && it.canRead() }

        return matchingChild ?: root
    }

    private fun requireCatalogModel(modelId: String): CatalogModel =
        catalogModels.firstOrNull { it.slug == modelId }
            ?: error("النموذج غير موجود في الكتالوج")

    private fun buildLocalImportMessage(modelId: String): String =
        "يرجى نسخ مجلد النموذج إلى المسار التالي ثم المحاولة مجددًا:\n\n" +
            "${getModelsDir().absolutePath}\n\n" +
            "(يجب أن يكون اسم المجلد $modelId)"

    private fun copyDocumentTreeContents(sourceDir: DocumentFile, targetDir: File): Int {
        var copiedFiles = 0
        sourceDir.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val destination = File(targetDir, name)
            copiedFiles += copyDocumentNode(child, destination)
        }
        return copiedFiles
    }

    private fun copyDocumentNode(source: DocumentFile, destination: File): Int {
        return when {
            source.isDirectory -> {
                destination.mkdirs()
                source.listFiles().sumOf { child ->
                    val childName = child.name ?: return@sumOf 0
                    copyDocumentNode(child, File(destination, childName))
                }
            }

            source.isFile -> {
                destination.parentFile?.mkdirs()
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("تعذر قراءة الملف ${source.name}")
                1
            }

            else -> 0
        }
    }

    private fun installBoundModel(
        record: MlcModelRecord,
        modelDir: File,
        totalExpectedSize: Long,
        onProgress: (Float) -> Unit
    ): MlcChatConfig {
        modelDir.mkdirs()
        var currentBytes = 0L

        fun downloadWithProgress(url: String, destination: File) {
            downloadFileIfMissing(
                url = url,
                destination = destination,
                onProgress = { bytes ->
                    currentBytes += bytes
                    if (totalExpectedSize > 0) {
                        onProgress((currentBytes.toFloat() / totalExpectedSize).coerceIn(0f, 0.95f))
                    }
                }
            )
        }

        downloadWithProgress(
            url = resolveMlcModelAssetUrl(record.modelUrl, MLC_MODEL_CONFIG_FILENAME),
            destination = File(modelDir, MLC_MODEL_CONFIG_FILENAME)
        )
        downloadWithProgress(
            url = resolveMlcModelAssetUrl(record.modelUrl, MLC_TENSOR_CACHE_FILENAME),
            destination = File(modelDir, MLC_TENSOR_CACHE_FILENAME)
        )

        val chatConfig = readInstalledMlcChatConfig(modelDir)
        val tensorCache = readInstalledMlcTensorCache(modelDir)

        chatConfig.tokenizerFiles.forEach { relativePath ->
            downloadWithProgress(
                url = resolveMlcModelAssetUrl(record.modelUrl, relativePath),
                destination = File(modelDir, relativePath)
            )
        }

        tensorCache.records.forEach { tensorRecord ->
            downloadWithProgress(
                url = resolveMlcModelAssetUrl(record.modelUrl, tensorRecord.dataPath),
                destination = File(modelDir, tensorRecord.dataPath)
            )
        }

        onProgress(0.98f)
        return chatConfig
    }

    private fun downloadFileIfMissing(url: String, destination: File, onProgress: (Long) -> Unit = {}) {
        if (destination.exists()) {
            onProgress(destination.length())
            return
        }

        destination.parentFile?.mkdirs()
        val tempFile = File(destination.parentFile, "${destination.name}.part")

        var currentUrl = url
        var redirectCount = 0
        var connection: java.net.HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null

        try {
            while (redirectCount < 10) {
                connection = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect response missing Location header for $currentUrl")
                    currentUrl = resolveRedirectUrl(currentUrl, location)
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

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        onProgress(bytesRead.toLong())
                    }
                }
            }

            if (tempFile.exists()) {
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }
            } else {
                error("Downloaded temp file was not created properly: $tempFile")
            }
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {
            }
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveRedirectUrl(currentUrl: String, location: String): String {
        val trimmedLocation = location.trim()
        if (trimmedLocation.isEmpty()) {
            error("Redirect response included an empty Location header for $currentUrl")
        }
        return URI(currentUrl).resolve(trimmedLocation).toString()
    }
}

internal fun InstalledModelEntity.toDomain() = InstalledModel(
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

data class ModelDownloadProgress(
    val state: ModelDownloadState,
    val progress: Float
)
