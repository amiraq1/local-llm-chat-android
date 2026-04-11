package com.example.localllm.ui.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon  = { Icon(Icons.Outlined.RestartAlt, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("إعادة الضبط") },
            text  = {
                Text(
                    "هل تريد إعادة جميع الإعدادات إلى قيمها الافتراضية؟ لا يمكن التراجع.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetDefaults(); showResetDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("إعادة الضبط") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("إلغاء") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) { heading() }
                    ) {
                        Text("الإعدادات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("تخصيص سلوك النموذج", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "إعادة الضبط")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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

            // ── Engine info ──
            item {
                val info = viewModel.engineInfo
                SettingsGroup(
                    title = "المحرك",
                    icon  = Icons.Outlined.Memory
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(info.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "الإصدار ${info.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (info.backend) {
                                "Fake" -> MaterialTheme.colorScheme.tertiaryContainer
                                "MLC"  -> MaterialTheme.colorScheme.primaryContainer
                                else   -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                info.backend,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (info.backend) {
                                    "Fake" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    "MLC"  -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else   -> MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Inference ──
            item {
                SettingsGroup(title = "الاستدلال", icon = Icons.Outlined.Tune) {
                    SliderRow(
                        icon        = Icons.Outlined.Thermostat,
                        label       = "Temperature",
                        description = "يتحكم في إبداعية الردود",
                        value       = settings.temperature,
                        displayValue= "${"%.2f".format(settings.temperature)}",
                        valueRange  = 0f..2f,
                        onValueChange = viewModel::setTemperature
                    )

                    SettingsDivider()

                    SliderRow(
                        icon        = Icons.Outlined.FilterList,
                        label       = "Top-P",
                        description = "نطاق الكلمات المقترحة (Nucleus Sampling)",
                        value       = settings.topP,
                        displayValue= "${"%.2f".format(settings.topP)}",
                        valueRange  = 0.1f..1f,
                        onValueChange = viewModel::setTopP
                    )

                    SettingsDivider()

                    SliderRow(
                        icon        = Icons.Outlined.Article,
                        label       = "Max Tokens",
                        description = "الحد الأقصى لطول الرد",
                        value       = settings.maxTokens.toFloat(),
                        displayValue= "${settings.maxTokens}",
                        valueRange  = 64f..2048f,
                        steps       = 30,
                        onValueChange = { viewModel.setMaxTokens(it.roundToInt()) }
                    )

                    SettingsDivider()

                    SliderRow(
                        icon        = Icons.Outlined.Forum,
                        label       = "Context Length",
                        description = "حجم نافذة السياق (تأثير على استهلاك الذاكرة)",
                        value       = settings.contextLength.toFloat(),
                        displayValue= "${settings.contextLength}",
                        valueRange  = 512f..8192f,
                        steps       = 14,
                        onValueChange = { viewModel.setContextLength(it.roundToInt()) }
                    )
                }
            }

            // ── Network ──
            item {
                SettingsGroup(title = "الشبكة والتنزيل", icon = Icons.Outlined.Download) {
                    ToggleRow(
                        icon        = Icons.Outlined.Wifi,
                        label       = "Wi-Fi فقط",
                        description = "تنزيل النماذج عبر Wi-Fi لتجنب استهلاك البيانات",
                        checked     = settings.wifiOnlyDownload,
                        onToggle    = viewModel::setWifiOnlyDownload
                    )
                }
            }

            // ── Appearance ──
            item {
                SettingsGroup(title = "المظهر", icon = Icons.Outlined.Palette) {
                    ToggleRow(
                        icon        = Icons.Outlined.DarkMode,
                        label       = "الوضع الداكن",
                        description = "تفعيل Dark Mode",
                        checked     = settings.darkMode,
                        onToggle    = viewModel::setDarkMode
                    )
                }
            }

            // ── About ──
            item {
                SettingsGroup(title = "حول التطبيق", icon = Icons.Outlined.Info) {
                    AboutRow(Icons.Outlined.Tag,          "الإصدار",        "1.0.0-MVP")
                    SettingsDivider()
                    AboutRow(Icons.Outlined.Memory,       "المحرك",         "FakeEngine (للتطوير)")
                    SettingsDivider()
                    AboutRow(Icons.Outlined.Code,         "المنصة",         "Kotlin + Compose")
                    SettingsDivider()
                    AboutRow(Icons.Outlined.Storage,      "قاعدة البيانات", "Room + DataStore")
                    SettingsDivider()
                    AboutRow(Icons.Outlined.Lock,         "الخصوصية",       "100% محلي")
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─── Settings Group ───────────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

// ─── Slider Row ───────────────────────────────────────────────────────────────

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
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    displayValue,
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

// ─── Toggle Row ───────────────────────────────────────────────────────────────

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
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ─── About Row ────────────────────────────────────────────────────────────────

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: $value"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Divider ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 28.dp),
        color    = MaterialTheme.colorScheme.outlineVariant
    )
}
