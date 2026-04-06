package com.example.localllm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.engine.InferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val engineInfo = inferenceEngine.getEngineInfo()

    fun setTemperature(value: Float) = viewModelScope.launch { settingsDataStore.updateTemperature(value) }
    fun setTopP(value: Float) = viewModelScope.launch { settingsDataStore.updateTopP(value) }
    fun setMaxTokens(value: Int) = viewModelScope.launch { settingsDataStore.updateMaxTokens(value) }
    fun setContextLength(value: Int) = viewModelScope.launch { settingsDataStore.updateContextLength(value) }
    fun setWifiOnlyDownload(value: Boolean) = viewModelScope.launch { settingsDataStore.updateWifiOnlyDownload(value) }
    fun setDarkMode(value: Boolean) = viewModelScope.launch { settingsDataStore.updateDarkMode(value) }
    fun resetDefaults() = viewModelScope.launch { settingsDataStore.resetToDefaults() }
}
