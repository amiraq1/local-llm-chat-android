package com.example.localllm.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModelsScreenState(
    val models: List<ModelUiState> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeModelId: String = ""
)

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    data object Completed : DownloadStatus()
    data class Error(
        val message: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : DownloadStatus()
}

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(ModelsScreenState())
    val state: StateFlow<ModelsScreenState> = _state.asStateFlow()
    private val _downloadStatuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatuses: StateFlow<Map<String, DownloadStatus>> = _downloadStatuses.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        // Auto-sync local storage models on startup
        viewModelScope.launch {
            modelRepository.availableModels.forEach { model ->
                val path = modelRepository.getInstallPath(model.id)
                val dir = java.io.File(path)
                if (modelRepository.isInstallComplete(model.id)) {
                    modelRepository.markAsInstalled(model, path)
                } else if (dir.exists()) {
                    Timber.w("Ignoring incomplete local model install for %s at %s", model.id, path)
                }
            }
        }

        viewModelScope.launch {
            combine(
                modelRepository.getModelUiStates(),
                settingsDataStore.settings,
                downloadStatuses
            ) { modelStates, settings, downloads -> Triple(modelStates, settings, downloads) }
                .collect { (modelStates, settings, downloads) ->
                val mergedModelStates = modelStates.map { modelState ->
                    when (val status = downloads[modelState.model.id]) {
                        null,
                        DownloadStatus.Idle -> modelState.copy(
                            downloadProgress = if (modelState.isInstalled) 1f else modelState.downloadProgress
                        )
                        DownloadStatus.Completed -> modelState.copy(
                            downloadState = ModelDownloadState.INSTALLED,
                            downloadProgress = 1f
                        )
                        is DownloadStatus.Downloading -> modelState.copy(
                            downloadState = ModelDownloadState.DOWNLOADING,
                            downloadProgress = status.progressFloat()
                        )
                        is DownloadStatus.Paused -> modelState.copy(
                            downloadState = ModelDownloadState.PAUSED,
                            downloadProgress = status.progressFloat()
                        )
                        is DownloadStatus.Error -> modelState.copy(
                            downloadState = ModelDownloadState.ERROR,
                            downloadProgress = status.progressFloat()
                        )
                    }
                }

                val activeModelId = runCatching {
                    reconcileActiveModelState(mergedModelStates, settings.activeModelId)
                }.onFailure { error ->
                    Timber.e(error, "Failed to reconcile active model state")
                    _state.update { it.copy(errorMessage = "فشل مزامنة النموذج النشط") }
                }.getOrDefault("")

                _state.update {
                    it.copy(
                        models = mergedModelStates,
                        activeModelId = activeModelId,
                        isLoading = downloads.values.any { it is DownloadStatus.Downloading }
                    )
                }
            }
        }
    }

    fun activateModel(modelId: String) {
        viewModelScope.launch {
            try {
                modelRepository.setActiveModel(modelId)
                settingsDataStore.updateActiveModelId(modelId)
                Timber.d("Activated model: $modelId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to activate model")
                _state.update { it.copy(errorMessage = "فشل تفعيل النموذج") }
            }
        }
    }

    fun downloadModel(modelId: String) {
        if (downloadJobs.containsKey(modelId)) return

        viewModelScope.launch {
            val model = modelRepository.availableModels.find { it.id == modelId }
            if (model == null) {
                _state.update { it.copy(errorMessage = "النموذج غير موجود في القائمة") }
                return@launch
            }

            val path = modelRepository.getInstallPath(model.id)
            val dir = java.io.File(path)
            if (modelRepository.isInstallComplete(model.id)) {
                modelRepository.markAsInstalled(model, path)
                _downloadStatuses.update { downloads ->
                    downloads + (modelId to DownloadStatus.Completed)
                }
                Timber.d("Model found in local storage and synced: $modelId at $path")
                _state.update { it.copy(errorMessage = null) }
                return@launch
            }

            _downloadStatuses.update { downloads ->
                downloads + (modelId to DownloadStatus.Downloading(0L, model.sizeBytes))
            }

            val job = viewModelScope.launch {
                try {
                    modelRepository.downloadModel(modelId) { downloadedBytes, totalBytes ->
                        _downloadStatuses.update { downloads ->
                            downloads + (
                                modelId to DownloadStatus.Downloading(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes.coerceAtLeast(model.sizeBytes)
                                )
                            )
                        }
                    }
                    _downloadStatuses.update { downloads ->
                        downloads + (modelId to DownloadStatus.Completed)
                    }
                    Timber.d("Model installed: $modelId")
                } catch (cancelled: CancellationException) {
                    _downloadStatuses.update { downloads ->
                        val current = downloads[modelId]
                        val downloadedBytes = current.downloadedBytes()
                        val totalBytes = current.totalBytes(model.sizeBytes)
                        downloads + (modelId to DownloadStatus.Paused(downloadedBytes, totalBytes))
                    }
                    throw cancelled
                } catch (e: Exception) {
                    if (modelRepository.isInstallComplete(modelId)) {
                        modelRepository.markAsInstalled(model, path)
                        _downloadStatuses.update { downloads ->
                            downloads + (modelId to DownloadStatus.Completed)
                        }
                        Timber.w(e, "Recovered from download error after completing model install: $modelId")
                        _state.update { it.copy(errorMessage = null) }
                    } else {
                        Timber.e(e, "Failed to download model")
                        _downloadStatuses.update { downloads ->
                            val current = downloads[modelId]
                            downloads + (
                                modelId to DownloadStatus.Error(
                                    message = e.message ?: "فشل تنزيل النموذج",
                                    downloadedBytes = current.downloadedBytes(),
                                    totalBytes = current.totalBytes(model.sizeBytes)
                                )
                            )
                        }
                        _state.update {
                            it.copy(errorMessage = e.message ?: "فشل تنزيل النموذج")
                        }
                    }
                } finally {
                    downloadJobs.remove(modelId)
                }
            }

            downloadJobs[modelId] = job
        }
    }

    fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                modelRepository.deleteModel(modelId)
                if (_state.value.activeModelId == modelId) {
                    settingsDataStore.updateActiveModelId("")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete model")
                _state.update { it.copy(errorMessage = "فشل حذف النموذج") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private suspend fun reconcileActiveModelState(
        modelStates: List<ModelUiState>,
        storedActiveModelId: String
    ): String {
        val installedIds = modelStates
            .asSequence()
            .filter { it.isInstalled }
            .map { it.model.id }
            .toSet()
        val databaseActiveModelId = modelStates.firstOrNull { it.isActive }?.model?.id.orEmpty()

        return when {
            databaseActiveModelId.isNotBlank() && databaseActiveModelId != storedActiveModelId -> {
                settingsDataStore.updateActiveModelId(databaseActiveModelId)
                databaseActiveModelId
            }

            databaseActiveModelId.isBlank() &&
                storedActiveModelId.isNotBlank() &&
                storedActiveModelId in installedIds -> {
                modelRepository.setActiveModel(storedActiveModelId)
                storedActiveModelId
            }

            storedActiveModelId.isNotBlank() && storedActiveModelId !in installedIds -> {
                settingsDataStore.updateActiveModelId("")
                ""
            }

            else -> databaseActiveModelId.ifBlank { storedActiveModelId }
        }
    }
}

private fun DownloadStatus?.downloadedBytes(): Long = when (this) {
    is DownloadStatus.Downloading -> downloadedBytes
    is DownloadStatus.Paused -> downloadedBytes
    is DownloadStatus.Error -> downloadedBytes
    else -> 0L
}

private fun DownloadStatus?.totalBytes(fallback: Long): Long = when (this) {
    is DownloadStatus.Downloading -> totalBytes
    is DownloadStatus.Paused -> totalBytes
    is DownloadStatus.Error -> totalBytes
    else -> fallback
}

private fun DownloadStatus.Downloading.progressFloat(): Float =
    if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    else 0f

private fun DownloadStatus.Paused.progressFloat(): Float =
    if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    else 0f

private fun DownloadStatus.Error.progressFloat(): Float =
    if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    else 0f
