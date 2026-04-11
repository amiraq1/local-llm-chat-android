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
        viewModelScope.launch {
            modelRepository.syncDiscoveredModels()
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
            _state.update { it.copy(isLoading = true) }
            try {
                modelRepository.downloadModel(modelId)
                Timber.d("Model installed: $modelId")
                _state.update { it.copy(errorMessage = null) }
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
