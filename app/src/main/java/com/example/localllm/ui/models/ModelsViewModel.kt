package com.example.localllm.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.ModelUiState
import dagger.hilt.android.lifecycle.HiltViewModel
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
                settingsDataStore.settings
            ) { modelStates, settings ->
                Pair(modelStates, settings)
            }.collect { (modelStates, settings) ->
                val activeModelId = runCatching {
                    reconcileActiveModelState(modelStates, settings.activeModelId)
                }.onFailure { error ->
                    Timber.e(error, "Failed to reconcile active model state")
                    _state.update { it.copy(errorMessage = "فشل مزامنة النموذج النشط") }
                }.getOrDefault("")

                _state.update { it.copy(models = modelStates, activeModelId = activeModelId) }
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
                Timber.d("Model found in local storage and synced: $modelId at $path")
                _state.update { it.copy(errorMessage = null) }
                return@launch
            }

            if (dir.exists()) {
                dir.deleteRecursively()
            }

            _state.update { it.copy(isLoading = true) }
            try {
                modelRepository.downloadModel(modelId)
                Timber.d("Model installed: $modelId")
            } catch (e: Exception) {
                if (modelRepository.isInstallComplete(modelId)) {
                    modelRepository.markAsInstalled(model, path)
                    Timber.w(e, "Recovered from download error after completing model install: $modelId")
                    _state.update { it.copy(errorMessage = null) }
                } else {
                    Timber.e(e, "Failed to download model")
                    _state.update {
                        it.copy(errorMessage = e.message ?: "فشل تنزيل النموذج")
                    }
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
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
