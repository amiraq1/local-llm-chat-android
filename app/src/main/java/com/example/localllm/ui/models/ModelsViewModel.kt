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

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(ModelsScreenState())
    val state: StateFlow<ModelsScreenState> = _state.asStateFlow()
    private val transientDownloads = MutableStateFlow<Map<String, TransientDownloadState>>(emptyMap())
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
                transientDownloads
            ) { modelStates, settings, downloads ->
                Triple(modelStates, settings, downloads)
            }.collect { (modelStates, settings, downloads) ->
                val mergedModelStates = modelStates.map { modelState ->
                    val transientState = downloads[modelState.model.id]
                    if (transientState == null) {
                        modelState.copy(
                            downloadProgress = if (modelState.isInstalled) 1f else modelState.downloadProgress
                        )
                    } else {
                        modelState.copy(
                            downloadState = transientState.downloadState,
                            downloadProgress = transientState.progress
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
                        isLoading = downloads.values.any { state ->
                            state.downloadState == ModelDownloadState.DOWNLOADING
                        }
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
                transientDownloads.update { downloads ->
                    downloads + (modelId to TransientDownloadState(ModelDownloadState.INSTALLED, 1f))
                }
                Timber.d("Model found in local storage and synced: $modelId at $path")
                _state.update { it.copy(errorMessage = null) }
                return@launch
            }

            transientDownloads.update { downloads ->
                downloads + (modelId to TransientDownloadState(ModelDownloadState.DOWNLOADING, 0f))
            }

            val job = viewModelScope.launch {
                try {
                    modelRepository.downloadModel(modelId) { progress ->
                        transientDownloads.update { downloads ->
                            val current = downloads[modelId]
                            downloads + (
                                modelId to TransientDownloadState(
                                    downloadState = ModelDownloadState.DOWNLOADING,
                                    progress = progress.coerceIn(
                                        current?.progress ?: 0f,
                                        1f
                                    )
                                )
                            )
                        }
                    }
                    transientDownloads.update { downloads ->
                        downloads + (modelId to TransientDownloadState(ModelDownloadState.INSTALLED, 1f))
                    }
                    Timber.d("Model installed: $modelId")
                } catch (cancelled: CancellationException) {
                    transientDownloads.update { downloads ->
                        downloads + (
                            modelId to TransientDownloadState(
                                downloadState = ModelDownloadState.PAUSED,
                                progress = downloads[modelId]?.progress ?: 0f
                            )
                        )
                    }
                    throw cancelled
                } catch (e: Exception) {
                    if (modelRepository.isInstallComplete(modelId)) {
                        modelRepository.markAsInstalled(model, path)
                        transientDownloads.update { downloads ->
                            downloads + (modelId to TransientDownloadState(ModelDownloadState.INSTALLED, 1f))
                        }
                        Timber.w(e, "Recovered from download error after completing model install: $modelId")
                        _state.update { it.copy(errorMessage = null) }
                    } else {
                        Timber.e(e, "Failed to download model")
                        transientDownloads.update { downloads ->
                            downloads + (
                                modelId to TransientDownloadState(
                                    downloadState = ModelDownloadState.ERROR,
                                    progress = downloads[modelId]?.progress ?: 0f
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

private data class TransientDownloadState(
    val downloadState: ModelDownloadState,
    val progress: Float
)
