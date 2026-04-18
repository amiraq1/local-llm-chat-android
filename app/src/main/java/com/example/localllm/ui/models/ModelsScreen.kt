package com.example.localllm.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.ModelDownloadState
 codex/fix-audit-findings

import com.example.localllm.domain.model.ModelUiState
import com.example.localllm.ui.models.ModelItem
 main

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var pendingDeleteModelId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة النماذج") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),

            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.models.isEmpty()) {
                    item {
                        Text("جاري تحميل النماذج...")
                    }
                } else {
                    items(state.models, key = { it.model.id }) { modelState ->
                        ModelItem(
                            modelState = modelState,
                            isActive = modelState.model.id == state.activeModelId,
                            onDownload = { viewModel.downloadModel(modelState.model.id) },
                            onCancel = { viewModel.cancelDownload(modelState.model.id) },
                            onLoad = { viewModel.activateModel(modelState.model.id) },
                            onDelete = { pendingDeleteModelId = modelState.model.id }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        // Error snackbar/dialog logic omitted for brevity, can be added back
        state.errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("خطأ") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("حسناً")
                    }
                }
            )
        }

        pendingDeleteModelId?.let { modelId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteModelId = null },
                title = { Text("حذف النموذج") },
                text = { Text("هل أنت متأكد من حذف هذا النموذج؟ سيؤدي ذلك إلى مسح الملفات المحلية.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteModel(modelId)
                        pendingDeleteModelId = null
                    }) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
 codex/fix-audit-findings
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteModelId = null }) {
                        Text("إلغاء")

                }
            }

            // ── Incompatibility warning ──
            val incompatibilityReason = modelState.incompatibilityReason
            if (!modelState.isCompatible && incompatibilityReason != null) {
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
                            incompatibilityReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
 main
                    }
                }
            )
        }
    }
}
