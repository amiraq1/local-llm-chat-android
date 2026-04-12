package com.example.localllm.ui.models

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: ModelsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) { heading() }
                    ) {
                        Text("النماذج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.models.count { it.isInstalled }} مثبّت",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (state.models.isEmpty()) {
            ModelsEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    DeviceInfoBanner()
                    Spacer(Modifier.height(4.dp))
                }

                items(state.models, key = { it.model.id }) { modelState ->
                    ModelCard(
                        modelState = modelState,
                        isActive   = modelState.model.id == state.activeModelId,
                        onActivate = { viewModel.activateModel(modelState.model.id) },
                        onDownload = { viewModel.downloadModel(modelState.model.id) },
                        onCancelDownload = { viewModel.cancelDownload(modelState.model.id) },
                        onDelete   = { viewModel.deleteModel(modelState.model.id) }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // Error snackbar
        state.errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("خطأ") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = viewModel::clearError) { Text("حسنًا") } }
            )
        }
    }
}

// ─── Device Banner ────────────────────────────────────────────────────────────

@Composable
private fun DeviceInfoBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "اختر نموذجًا متوافقًا مع ذاكرة جهازك",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ─── Model Card ───────────────────────────────────────────────────────────────

@Composable
fun ModelCard(
    modelState: ModelUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val model = modelState.model

    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 0.5.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 0.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Family icon
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = familyColor(model.family).copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            familyIcon(model.family),
                            null,
                            tint = familyColor(model.family),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            model.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() }
                        )
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "نشط",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "${model.family.uppercase()} · ${model.quantization}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Install state badge
                when (modelState.downloadState) {
                    ModelDownloadState.INSTALLED ->
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "النموذج مثبت",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    ModelDownloadState.DOWNLOADING ->
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> Unit
                }
            }

            // ── Spec chips ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecPill(Icons.Outlined.Storage, "${"%.1f".format(model.sizeBytes / 1e9)}GB")
                SpecPill(Icons.Outlined.Memory,  "≥${model.minRamMb / 1024}GB RAM")
                SpecPill(Icons.Outlined.Forum,   "${model.contextLength / 1000}K ctx")
            }

            // ── Tags ──
            if (model.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // ── Incompatibility warning ──
            if (!modelState.isCompatible && modelState.incompatibilityReason != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            modelState.incompatibilityReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Action buttons ──
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ModelItem(
                modelState = modelState,
                isActive = isActive,
                onDownload = onDownload,
                onCancel = onCancelDownload,
                onLoad = onActivate,
                onDelete = onDelete
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun ModelsEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Memory, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text("جاري تحميل النماذج...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SpecPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun familyColor(family: String): Color = when (family.lowercase()) {
    "llama"   -> MaterialTheme.colorScheme.primary
    "phi"     -> MaterialTheme.colorScheme.tertiary
    "gemma"   -> MaterialTheme.colorScheme.secondary
    "mistral" -> Color(0xFFE8B84B)
    else      -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun familyIcon(family: String): ImageVector = when (family.lowercase()) {
    "llama"   -> Icons.Filled.Pets
    "phi"     -> Icons.Filled.Science
    "gemma"   -> Icons.Filled.Diamond
    "mistral" -> Icons.Filled.Air
    else      -> Icons.Filled.Memory
}
