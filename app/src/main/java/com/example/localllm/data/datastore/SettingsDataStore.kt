package com.example.localllm.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.localllm.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val WIFI_ONLY_DOWNLOAD = booleanPreferencesKey("wifi_only_download")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            Timber.e(e, "Error reading settings")
            emit(emptyPreferences())
        }
        .map { prefs ->
            AppSettings(
                activeModelId = prefs[Keys.ACTIVE_MODEL_ID] ?: "gemma-4-e2b",
                temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
                topP = prefs[Keys.TOP_P] ?: 0.9f,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: 512,
                contextLength = prefs[Keys.CONTEXT_LENGTH] ?: 2048,
                wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: true,
                darkMode = prefs[Keys.DARK_MODE] ?: false
            )
        }

    suspend fun updateActiveModelId(modelId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_MODEL_ID] = modelId }
    }

    suspend fun updateTemperature(value: Float) {
        context.dataStore.edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 2f) }
    }

    suspend fun updateTopP(value: Float) {
        context.dataStore.edit { it[Keys.TOP_P] = value.coerceIn(0f, 1f) }
    }

    suspend fun updateMaxTokens(value: Int) {
        context.dataStore.edit { it[Keys.MAX_TOKENS] = value.coerceIn(64, 4096) }
    }

    suspend fun updateContextLength(value: Int) {
        context.dataStore.edit { it[Keys.CONTEXT_LENGTH] = value.coerceIn(512, 8192) }
    }

    suspend fun updateWifiOnlyDownload(value: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOAD] = value }
    }

    suspend fun updateDarkMode(value: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = value }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
        Timber.d("Settings reset to defaults")
    }
}
