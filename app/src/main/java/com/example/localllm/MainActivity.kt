package com.example.localllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.ui.navigation.AppNavigation
import com.example.localllm.ui.theme.LocalLLMTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settings
                .collectAsState(initial = AppSettings())

            LocalLLMTheme(darkTheme = settings.darkMode) {
                AppNavigation()
            }
        }
    }
}
