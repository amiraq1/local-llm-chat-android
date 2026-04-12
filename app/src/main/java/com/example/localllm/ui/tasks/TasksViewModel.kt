package com.example.localllm.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ToolResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val isLoading: Boolean = false,
    val result: ToolResult? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val orchestrator: ActionOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    fun runDeviceInfo() = executeTool("get_device_info")

    fun runClipboard() = executeTool("get_clipboard")

    fun runBatteryStatus() = executeTool("get_battery_status")

    fun clearResult() {
        _uiState.update { TasksUiState() }
    }

    private fun executeTool(toolName: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { TasksUiState(isLoading = true) }

        viewModelScope.launch {
            val result = orchestrator.execute(toolName)
            _uiState.update {
                if (result.success) {
                    TasksUiState(result = result)
                } else {
                    TasksUiState(errorMessage = result.errorMessage ?: "حدث خطأ غير معروف")
                }
            }
        }
    }
}
