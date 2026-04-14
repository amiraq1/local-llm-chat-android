package com.example.localllm.ui.models

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.localllm.domain.model.ModelDownloadState

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
        viewModel.importModel(modelId, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة النماذج") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.syncInstalledModels() }) {
                        Icon(Icons.Default.Sync, contentDescription = "مزامنة")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.isInitialLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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

                    item {
                        Spacer(Modifier.height(80.dp))
                    }
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
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteModelId = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}
