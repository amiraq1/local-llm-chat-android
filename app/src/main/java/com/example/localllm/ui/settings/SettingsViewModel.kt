package com.example.localllm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.engine.EngineInfo
import com.example.localllm.engine.InferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val engineInfo: EngineInfo = EngineInfo(
        name = "Unknown",
        version = "unknown",
        backend = "unknown"
    ),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    private val internalState = MutableStateFlow(
        SettingsUiState(
            engineInfo = inferenceEngine.getEngineInfo()
        )
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.settings,
        internalState
    ) { settings, state ->
        state.copy(
            settings = settings,
            engineInfo = inferenceEngine.getEngineInfo()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = internalState.value.copy(
            settings = AppSettings(),
            engineInfo = inferenceEngine.getEngineInfo()
        )
    )

    fun setTemperature(value: Float) {
        launchSettingsUpdate("setTemperature") {
            settingsDataStore.updateTemperature(value)
        }
    }

    fun setTopP(value: Float) {
        launchSettingsUpdate("setTopP") {
            settingsDataStore.updateTopP(value)
        }
    }

    fun setMaxTokens(value: Int) {
        launchSettingsUpdate("setMaxTokens") {
            settingsDataStore.updateMaxTokens(value)
        }
    }

    fun setContextLength(value: Int) {
        launchSettingsUpdate("setContextLength") {
            settingsDataStore.updateContextLength(value)
        }
    }

    fun setWifiOnlyDownload(value: Boolean) {
        launchSettingsUpdate("setWifiOnlyDownload") {
            settingsDataStore.updateWifiOnlyDownload(value)
        }
    }

    fun setDarkMode(value: Boolean) {
        launchSettingsUpdate("setDarkMode") {
            settingsDataStore.updateDarkMode(value)
        }
    }

    fun resetDefaults() {
        launchSettingsUpdate("resetDefaults") {
            settingsDataStore.resetToDefaults()
        }
    }

    fun clearError() {
        internalState.update { it.copy(errorMessage = null) }
    }

    fun refreshEngineInfo() {
        internalState.update {
            it.copy(engineInfo = inferenceEngine.getEngineInfo())
        }
    }

    private fun launchSettingsUpdate(
        operationName: String,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null
                )
            }

            try {
                block()
                internalState.update {
                    it.copy(
                        isSaving = false,
                        engineInfo = inferenceEngine.getEngineInfo()
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Settings operation failed: %s", operationName)
                internalState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "فشل تحديث الإعدادات"
                    )
                }
            }
        }
    }
}
