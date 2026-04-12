package com.example.localllm.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.ModelUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ModelsScreenState(
    val models: List<ModelUiState> = emptyList(),
    val isInitialLoading: Boolean = true,
    val busyModelId: String? = null,
    val currentAction: ModelAction? = null,
    val errorMessage: String? = null,
    val activeModelId: String = ""
) {
    val isBusy: Boolean
        get() = busyModelId != null || currentAction != null
}

enum class ModelAction {
    ACTIVATE,
    DOWNLOAD,
    IMPORT,
    DELETE,
    SYNC
}

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(ModelsScreenState())
    val state: StateFlow<ModelsScreenState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        observeState()
        syncInstalledModels()
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                modelRepository.getModelUiStates(),
                settingsDataStore.settings
            ) { modelStates, settings ->
                modelStates to settings.activeModelId
            }.collect { (modelStates, activeModelId) ->
                _state.update {
                    it.copy(
                        models = modelStates,
                        activeModelId = activeModelId,
                        isInitialLoading = false
                    )
                }
            }
        }
    }

    private fun syncInstalledModels() {
        launchExclusiveAction(
            action = ModelAction.SYNC,
            modelId = null,
            failureMessage = "فشل مزامنة النماذج المثبتة"
        ) {
            modelRepository.syncDiscoveredModels()
            Timber.d("Discovered models synced")
        }
    }

    fun activateModel(modelId: String) {
        if (shouldIgnoreActionFor(modelId)) return

        launchExclusiveAction(
            action = ModelAction.ACTIVATE,
            modelId = modelId,
            failureMessage = "فشل تفعيل النموذج"
        ) {
            modelRepository.setActiveModel(modelId)
            settingsDataStore.updateActiveModelId(modelId)
            Timber.d("Activated model: %s", modelId)
        }
    }

    fun downloadModel(modelId: String) {
        if (shouldIgnoreActionFor(modelId)) return

        launchExclusiveAction(
            action = ModelAction.DOWNLOAD,
            modelId = modelId,
            failureMessage = "فشل تنزيل النموذج"
        ) {
            modelRepository.downloadModel(modelId)
            _state.update { it.copy(errorMessage = null) }
            Timber.d("Model installed: %s", modelId)
        }
    }

    fun importModel(modelId: String, treeUri: Uri) {
        if (shouldIgnoreActionFor(modelId)) return

        launchExclusiveAction(
            action = ModelAction.IMPORT,
            modelId = modelId,
            failureMessage = "فشل استيراد النموذج"
        ) {
            modelRepository.importModelFromTree(modelId, treeUri)
            _state.update { it.copy(errorMessage = null) }
            Timber.d("Model imported locally: %s", modelId)
        }
    }

    fun deleteModel(modelId: String) {
        if (shouldIgnoreActionFor(modelId)) return

        launchExclusiveAction(
            action = ModelAction.DELETE,
            modelId = modelId,
            failureMessage = "فشل حذف النموذج"
        ) {
            val wasActive = _state.value.activeModelId == modelId

            modelRepository.deleteModel(modelId)

            if (wasActive) {
                settingsDataStore.updateActiveModelId("")
            }

            _state.update { it.copy(errorMessage = null) }
            Timber.d("Deleted model: %s", modelId)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun shouldIgnoreActionFor(modelId: String): Boolean {
        val state = _state.value
        return state.isBusy || state.busyModelId == modelId
    }

    private fun launchExclusiveAction(
        action: ModelAction,
        modelId: String?,
        failureMessage: String,
        block: suspend () -> Unit
    ) {
        if (activeJob?.isActive == true) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    currentAction = action,
                    busyModelId = modelId,
                    errorMessage = null
                )
            }

            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "%s failed for modelId=%s", action.name, modelId)
                _state.update {
                    it.copy(
                        errorMessage = e.message ?: failureMessage
                    )
                }
            } finally {
                _state.update {
                    it.copy(
                        currentAction = null,
                        busyModelId = null
                    )
                }
            }
        }
    }
}
