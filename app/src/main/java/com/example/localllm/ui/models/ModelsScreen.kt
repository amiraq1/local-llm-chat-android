package com.example.localllm.ui.models

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var pendingImportModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteModelId by rememberSaveable { mutableStateOf<String?>(null) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val modelId = pendingImportModelId
        pendingImportModelId = null

        if (uri == null || modelId == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        viewModel.importModel(modelId, uri)
    }

    Scaffold(
        topBar = {
            ModelsTopBar(installedCount = state.models.count { it.isInstalled })
        }
    ) { padding ->
        when {
            state.models.isEmpty() -> {
                ModelsEmptyState(modifier = Modifier.padding(padding))
            }

            else -> {
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

                    items(
                        items = state.models,
                        key = { it.model.id }
                    ) { modelState ->
                        ModelCard(
                            modelState = modelState,
                            isActive = modelState.model.id == state.activeModelId,
                            onActivate = { viewModel.activateModel(modelState.model.id) },
                            onDownload = {
                                if (modelState.model.downloadUrl.isBlank()) {
                                    pendingImportModelId = modelState.model.id
                                    modelPicker.launch(null)
                                } else {
                                    viewModel.downloadModel(modelState.model.id)
                                }
                            },
                            onDeleteRequest = {
                                pendingDeleteModelId = modelState.model.id
                            }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    state.errorMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = viewModel::clearError
        )
    }

    pendingDeleteModelId?.let { modelId ->
        val modelName = state.models
            .firstOrNull { it.model.id == modelId }
            ?.model
            ?.name
            .orEmpty()

        ConfirmDeleteModelDialog(
            modelName = modelName,
            onConfirm = {
                pendingDeleteModelId = null
                viewModel.deleteModel(modelId)
            },
            onDismiss = {
                pendingDeleteModelId = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelsTopBar(
    installedCount: Int
) {
    TopAppBar(
        title = {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { heading() }
            ) {
                Text(
                    text = "النماذج",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$installedCount مثبّت",
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

@Composable
private fun DeviceInfoBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "معلومة عن الجهاز. اختر نموذجًا متوافقًا مع ذاكرة جهازك."
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "اختر نموذجًا متوافقًا مع ذاكرة جهازك",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ModelCard(
    modelState: ModelUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val model = modelState.model

    val borderColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
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
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 0.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = buildModelAccessibilitySummary(modelState, isActive)
                },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModelCardHeader(
                    modelState = modelState,
                    isActive = isActive
                )

                ModelSpecsRow(modelState = modelState)

                if (model.tags.isNotEmpty()) {
                    ModelTagsRow(tags = model.tags.take(3))
                }

                if (!modelState.isCompatible && modelState.incompatibilityReason != null) {
                    IncompatibilityBanner(reason = modelState.incompatibilityReason)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            ModelCardActions(
                modelState = modelState,
                isActive = isActive,
                onActivate = onActivate,
                onDownload = onDownload,
                onDeleteRequest = onDeleteRequest
            )
        }
    }
}

@Composable
private fun ModelCardHeader(
    modelState: ModelUiState,
    isActive: Boolean
) {
    val model = modelState.model

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = familyColor(model.family).copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = familyIcon(model.family),
                    contentDescription = null,
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
                    text = model.name,
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
                            text = "نشط",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = buildString {
                    if (model.provider.isNotBlank()) {
                        append(model.provider)
                        append(" · ")
                    }
                    append(model.family.uppercase())
                    append(" · ")
                    append(model.quantization)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (model.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (modelState.downloadState) {
            ModelDownloadState.INSTALLED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            ModelDownloadState.DOWNLOADING, ModelDownloadState.VERIFYING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun ModelSpecsRow(
    modelState: ModelUiState
) {
    val model = modelState.model

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpecPill(Icons.Outlined.Storage, "${"%.1f".format(model.sizeBytes / 1e9)}GB")
        SpecPill(Icons.Outlined.Memory, "≥${model.minRamMb / 1024}GB RAM")
        SpecPill(Icons.Outlined.Forum, "${model.contextLength / 1000}K ctx")
    }
}

@Composable
private fun ModelTagsRow(
    tags: List<String>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun IncompatibilityBanner(
    reason: String
) {
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
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ModelCardActions(
    modelState: ModelUiState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDownload: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val model = modelState.model

    when {
        modelState.downloadState == ModelDownloadState.DOWNLOADING ||
        modelState.downloadState == ModelDownloadState.VERIFYING -> {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "حالة التنزيل للنموذج ${model.name}"
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { modelState.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                val progressPercent = (modelState.downloadProgress * 100).toInt()
                val statusText = if (modelState.downloadState == ModelDownloadState.VERIFYING) {
                    "جاري التحقق من التنزيل..."
                } else {
                    "جارٍ التنزيل... $progressPercent%"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        modelState.isInstalled && !isActive -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onActivate,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "تفعيل النموذج ${model.name}"
                        },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("تفعيل")
                }

                OutlinedButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier.semantics {
                        contentDescription = "حذف ${model.name}"
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        modelState.isInstalled && isActive -> {
            OutlinedButton(
                onClick = onDeleteRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "إلغاء تثبيت النموذج ${model.name}"
                    },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("إلغاء التثبيت")
            }
        }

        else -> {
            val actionLabel = if (model.downloadUrl.isBlank()) "استيراد محلي" else "تنزيل"

            Button(
                onClick = onDownload,
                enabled = modelState.isCompatible,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        contentDescription = when {
                            !modelState.isCompatible ->
                                "النموذج ${model.name} غير متوافق مع هذا الجهاز"

                            model.downloadUrl.isBlank() ->
                                "استيراد النموذج ${model.name} من مجلد على الجهاز"

                            else ->
                                "تنزيل النموذج ${model.name}"
                        }
                    },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (!modelState.isCompatible) "غير متوافق" else actionLabel
                )
            }
        }
    }
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خطأ") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("حسنًا")
            }
        }
    )
}

@Composable
private fun ConfirmDeleteModelDialog(
    modelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد الحذف") },
        text = {
            Text("هل تريد حذف النموذج $modelName؟")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("حذف")
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
private fun ModelsEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "جاري تحميل النماذج...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecPill(
    icon: ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun familyColor(
    family: String
): Color = when (family.lowercase()) {
    "llama" -> MaterialTheme.colorScheme.primary
    "phi" -> MaterialTheme.colorScheme.tertiary
    "gemma" -> MaterialTheme.colorScheme.secondary
    "mistral" -> Color(0xFFE8B84B)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun familyIcon(
    family: String
): ImageVector = when (family.lowercase()) {
    "llama" -> Icons.Filled.Pets
    "phi" -> Icons.Filled.Science
    "gemma" -> Icons.Filled.Diamond
    "mistral" -> Icons.Filled.Air
    else -> Icons.Filled.Memory
}

private fun buildModelAccessibilitySummary(
    modelState: ModelUiState,
    isActive: Boolean
): String {
    val model = modelState.model
    return buildString {
        append("النموذج ${model.name}. ")
        if (model.provider.isNotBlank()) {
            append("المزوّد ${model.provider}. ")
        }
        append("الفئة ${model.family}. ")
        append("التكميم ${model.quantization}. ")
        append("الحجم ${"%.1f".format(model.sizeBytes / 1e9)} جيجابايت. ")
        append("الحد الأدنى للذاكرة ${model.minRamMb / 1024} جيجابايت رام. ")
        append("السياق ${model.contextLength / 1000} ألف رمز. ")
        if (model.description.isNotBlank()) {
            append("${model.description}. ")
        }
        if (model.tags.isNotEmpty()) {
            append("الوسوم ${model.tags.take(3).joinToString(separator = "، ")}. ")
        }

        when {
            modelState.downloadState == ModelDownloadState.DOWNLOADING ->
                append("حالة النموذج: قيد التنزيل. ")

            modelState.isInstalled ->
                append("حالة النموذج: مثبت. ")

            model.downloadUrl.isBlank() ->
                append("حالة النموذج: متاح للاستيراد المحلي. ")

            else ->
                append("حالة النموذج: غير مثبت. ")
        }

        if (isActive) {
            append("هذا هو النموذج النشط حاليًا. ")
        }

        if (!modelState.isCompatible) {
            append("غير متوافق مع هذا الجهاز")
            modelState.incompatibilityReason?.let { append(". $it") }
            append(".")
        }
    }.trim()
}
