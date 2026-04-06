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

    /** Simulates a download. Replace with real WorkManager-based download in production. */
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            Timber.d("Starting fake download for model: $modelId")
            val model = modelRepository.availableModels.find { it.id == modelId }
            if (model != null) {
                // Simulate a fast download to allow UI testing of model selection
                kotlinx.coroutines.delay(800)
                // Fake a file path based on the ID
                val fakePath = "/data/local/tmp/${model.id}.bin"
                modelRepository.markAsInstalled(model, fakePath)
                Timber.d("Model fake-installed: $modelId at $fakePath")
            } else {
                _state.update { it.copy(errorMessage = "النموذج غير موجود") }
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
