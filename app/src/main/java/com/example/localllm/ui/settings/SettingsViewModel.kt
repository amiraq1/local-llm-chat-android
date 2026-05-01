package com.example.localllm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.domain.tools.ToolConsentStore
import com.example.localllm.domain.tools.ToolRegistry
import com.example.localllm.domain.tools.ToolSensitivity
import com.example.localllm.engine.EngineInfo
import com.example.localllm.engine.InferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * View-state for a single SENSITIVE tool consent toggle.
 *
 * @param toolName    Stable identifier (e.g. `get_clipboard`).
 * @param description Human-readable summary shown in the Privacy section.
 * @param enabled     Persistent enable flag — when false the orchestrator refuses the tool entirely.
 */
data class SensitiveToolConsent(
    val toolName: String,
    val description: String,
    val enabled: Boolean
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val engineInfo: EngineInfo = EngineInfo(
        name = "Unknown",
        version = "unknown",
        backend = "unknown"
    ),
    val sensitiveToolConsents: List<SensitiveToolConsent> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val inferenceEngine: InferenceEngine,
    private val toolRegistry: ToolRegistry,
    private val toolConsentStore: ToolConsentStore
) : ViewModel() {

    /** Static list of SENSITIVE tools — order is stable across launches. */
    private val sensitiveTools by lazy {
        toolRegistry.getAll()
            .filter { it.sensitivity == ToolSensitivity.SENSITIVE }
            .sortedBy { it.name }
    }

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

    init {
        refreshSensitiveToolConsents()
    }

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

    /**
     * Toggle the persistent enable flag for a SENSITIVE tool. When [enabled] is
     * false, [ToolConsentStore] also clears any in-memory session approval, so
     * subsequent invocations will be refused with `DISABLED_BY_USER`.
     */
    fun setSensitiveToolEnabled(toolName: String, enabled: Boolean) {
        launchSettingsUpdate("setSensitiveToolEnabled:$toolName") {
            toolConsentStore.setPersistentlyEnabled(toolName, enabled)
            refreshSensitiveToolConsents()
        }
    }

    /**
     * Re-read the persistent enable flag for every SENSITIVE tool from DataStore
     * and publish the snapshot via [SettingsUiState.sensitiveToolConsents].
     * Called once on init and after every toggle.
     */
    private fun refreshSensitiveToolConsents() {
        viewModelScope.launch {
            try {
                val consents = sensitiveTools.map { tool ->
                    SensitiveToolConsent(
                        toolName = tool.name,
                        description = tool.description,
                        enabled = toolConsentStore.isPersistentlyEnabled(tool.name).first()
                    )
                }
                internalState.update { it.copy(sensitiveToolConsents = consents) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh sensitive-tool consents")
            }
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
