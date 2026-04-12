package com.example.localllm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.mergeDescendants
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.engine.EngineInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val engineInfo = uiState.engineInfo
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    if (showResetDialog) {
        ResetDefaultsDialog(
            onConfirm = {
                viewModel.resetDefaults()
                showResetDialog = false
            },
            onDismiss = {
                showResetDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            SettingsTopBar(
                onResetClick = { showResetDialog = true }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                EngineInfoSection(engineInfo = engineInfo)
            }

            item {
                InferenceSection(
                    settings = settings,
                    onTemperatureChange = viewModel::setTemperature,
                    onTopPChange = viewModel::setTopP,
                    onMaxTokensChange = { viewModel.setMaxTokens(it.roundToInt()) },
                    onContextLengthChange = { viewModel.setContextLength(it.roundToInt()) }
                )
            }

            item {
                NetworkSection(
                    wifiOnlyDownload = settings.wifiOnlyDownload,
                    onWifiOnlyChanged = viewModel::setWifiOnlyDownload
                )
            }

            item {
                AppearanceSection(
                    darkMode = settings.darkMode,
                    onDarkModeChanged = viewModel::setDarkMode
                )
            }

            item {
                AboutSection(engineInfoName = engineInfo.name)
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onResetClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { heading() }
            ) {
                Text(
                    text = "الإعدادات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "تخصيص سلوك النموذج",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onResetClick) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "إعادة الضبط"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ResetDefaultsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("إعادة الضبط")
        },
        text = {
            Text(
                text = "هل تريد إعادة جميع الإعدادات إلى قيمها الافتراضية؟ لا يمكن التراجع.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("إعادة الضبط")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun EngineInfoSection(
    engineInfo: EngineInfo
) {
    SettingsGroup(
        title = "المحرك",
        icon = Icons.Outlined.Memory
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = engineInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "الإصدار ${engineInfo.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when (engineInfo.backend) {
                    "Fake" -> MaterialTheme.colorScheme.tertiaryContainer
                    "MLC" -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    text = engineInfo.backend,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (engineInfo.backend) {
                        "Fake" -> MaterialTheme.colorScheme.onTertiaryContainer
                        "MLC" -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InferenceSection(
    settings: AppSettings,
    onTemperatureChange: (Float) -> Unit,
    onTopPChange: (Float) -> Unit,
    onMaxTokensChange: (Float) -> Unit,
    onContextLengthChange: (Float) -> Unit
) {
    SettingsGroup(
        title = "الاستدلال",
        icon = Icons.Outlined.Tune
    ) {
        SliderRow(
            icon = Icons.Outlined.Thermostat,
            label = "Temperature",
            description = "يتحكم في إبداعية الردود",
            value = settings.temperature,
            displayValue = "${"%.2f".format(settings.temperature)}",
            valueRange = 0f..2f,
            onValueChange = onTemperatureChange
        )

        SettingsDivider()

        SliderRow(
            icon = Icons.Outlined.FilterList,
            label = "Top-P",
            description = "نطاق الكلمات المقترحة (Nucleus Sampling)",
            value = settings.topP,
            displayValue = "${"%.2f".format(settings.topP)}",
            valueRange = 0.1f..1f,
            onValueChange = onTopPChange
        )

        SettingsDivider()

        SliderRow(
            icon = Icons.Outlined.Article,
            label = "Max Tokens",
            description = "الحد الأقصى لطول الرد",
            value = settings.maxTokens.toFloat(),
            displayValue = "${settings.maxTokens}",
            valueRange = 64f..2048f,
            steps = 30,
            onValueChange = onMaxTokensChange
        )

        SettingsDivider()

        SliderRow(
            icon = Icons.Outlined.Forum,
            label = "Context Length",
            description = "حجم نافذة السياق (تأثير على استهلاك الذاكرة)",
            value = settings.contextLength.toFloat(),
            displayValue = "${settings.contextLength}",
            valueRange = 512f..8192f,
            steps = 14,
            onValueChange = onContextLengthChange
        )
    }
}

@Composable
private fun NetworkSection(
    wifiOnlyDownload: Boolean,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    SettingsGroup(
        title = "الشبكة والتنزيل",
        icon = Icons.Outlined.Download
    ) {
        ToggleRow(
            icon = Icons.Outlined.Wifi,
            label = "Wi-Fi فقط",
            description = "تنزيل النماذج عبر Wi-Fi لتجنب استهلاك البيانات",
            checked = wifiOnlyDownload,
            onToggle = onWifiOnlyChanged
        )
    }
}

@Composable
private fun AppearanceSection(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit
) {
    SettingsGroup(
        title = "المظهر",
        icon = Icons.Outlined.Palette
    ) {
        ToggleRow(
            icon = Icons.Outlined.DarkMode,
            label = "الوضع الداكن",
            description = "تفعيل Dark Mode",
            checked = darkMode,
            onToggle = onDarkModeChanged
        )
    }
}

@Composable
private fun AboutSection(
    engineInfoName: String
) {
    SettingsGroup(
        title = "حول التطبيق",
        icon = Icons.Outlined.Info
    ) {
        AboutRow(Icons.Outlined.Tag, "الإصدار", "1.0.0")
        SettingsDivider()
        AboutRow(Icons.Outlined.Memory, "المحرك", engineInfoName)
        SettingsDivider()
        AboutRow(Icons.Outlined.Code, "المنصة", "Kotlin + Compose")
        SettingsDivider()
        AboutRow(Icons.Outlined.Storage, "قاعدة البيانات", "Room + DataStore")
        SettingsDivider()
        AboutRow(Icons.Outlined.Lock, "الخصوصية", "100% محلي")
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    description: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics {
                    contentDescription = "$label. $description"
                    stateDescription = displayValue
                }
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onToggle,
                role = Role.Switch
            )
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. $description"
                stateDescription = if (checked) "مفعّل" else "غير مفعّل"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: $value"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 28.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
