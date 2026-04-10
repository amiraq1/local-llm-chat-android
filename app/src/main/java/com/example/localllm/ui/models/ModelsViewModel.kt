package com.example.localllm.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.ModelUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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
        viewModelScope.launch {
            modelRepository.availableModels.forEach { model ->
                val path = modelRepository.getInstallPath(model.id)
                val dir = File(path)
                if (dir.exists() && dir.isDirectory && !dir.list().isNullOrEmpty()) {
                    modelRepository.markAsInstalled(model, path)
                }
            }
        }

        viewModelScope.launch {
            combine(
                modelRepository.getModelUiStates(),
                settingsDataStore.settings
            ) { modelStates, settings ->
                Pair(modelStates, settings.activeModelId)
            }.collect { (modelStates, activeModelId) ->
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
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val existingModel = modelRepository.availableModels.find { it.id == modelId }
                if (existingModel != null) {
                    val path = modelRepository.getInstallPath(existingModel.id)
                    val dir = File(path)
                    if (dir.exists() && dir.isDirectory && !dir.list().isNullOrEmpty()) {
                        modelRepository.markAsInstalled(existingModel, path)
                        Timber.d("Model found in local storage and synced: $modelId at $path")
                        return@launch
                    }
                }

                modelRepository.downloadModel(modelId)
                Timber.d("Model installed: $modelId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to download model")
                _state.update {
                    it.copy(errorMessage = e.message ?: "فشل تنزيل النموذج")
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete model")
                _state.update { it.copy(errorMessage = "فشل حذف النموذج") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }
}
