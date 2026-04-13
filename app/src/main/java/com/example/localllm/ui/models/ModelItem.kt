package com.example.localllm.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localllm.domain.model.ModelDownloadState
import com.example.localllm.domain.model.ModelUiState

@Composable
fun ModelItem(
    modelState: ModelUiState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = modelState.model.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${"%.1f".format(modelState.model.sizeBytes / 1_000_000_000.0)} GB",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (modelState.downloadState == ModelDownloadState.DOWNLOADING ||
            modelState.downloadState == ModelDownloadState.PAUSED ||
            modelState.downloadState == ModelDownloadState.ERROR
        ) {
            LinearProgressIndicator(
                progress = { modelState.downloadProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Text(
                text = "${(modelState.downloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            modelState.downloadState == ModelDownloadState.DOWNLOADING -> {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.StopCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("إلغاء")
                }
            }

            modelState.isInstalled -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLoad,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.PlayCircle, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isActive) "تشغيل" else "تشغيل")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.semantics {
                            contentDescription = "حذف ${modelState.model.name}"
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.DeleteOutline, null)
                    }
                }
            }

            else -> {
                Button(
                    onClick = onDownload,
                    enabled = modelState.isCompatible,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (modelState.downloadState) {
                            ModelDownloadState.PAUSED -> "تحميل"
                            ModelDownloadState.ERROR -> "تحميل"
                            else -> if (modelState.isCompatible) "تحميل" else "غير متوافق"
                        }
                    )
                }
            }
        }
    }
}
