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

<<<<<<< HEAD
<<<<<<< HEAD
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
=======
    @Inject lateinit var settingsDataStore: SettingsDataStore
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
    @Inject lateinit var settingsDataStore: SettingsDataStore
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
<<<<<<< HEAD
<<<<<<< HEAD
            val settings by settingsDataStore.settings.collectAsState(initial = AppSettings())
=======
            val settings by settingsDataStore.settings
                .collectAsState(initial = AppSettings())

>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
            val settings by settingsDataStore.settings
                .collectAsState(initial = AppSettings())

>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
            LocalLLMTheme(darkTheme = settings.darkMode) {
                AppNavigation()
            }
        }
    }
}
