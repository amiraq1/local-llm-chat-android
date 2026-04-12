package com.example.localllm.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.domain.tools.ActionOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val isLoading: Boolean = false,
    val result: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val orchestrator: ActionOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    fun runDeviceInfo() = executeTool("get_device_info")

    fun runClipboard() = executeTool("get_clipboard")

    fun runBatteryStatus() = executeTool("get_battery_status")

    fun clearResult() {
        currentJob?.cancel()
        currentJob = null
        _uiState.update { TasksUiState() }
    }

    private fun executeTool(toolName: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { TasksUiState(isLoading = true) }

        currentJob = viewModelScope.launch {
            try {
                val result = orchestrator.execute(toolName)
                _uiState.update {
                    if (result.success) {
                        TasksUiState(result = result.resultText)
                    } else {
                        TasksUiState(errorMessage = result.errorMessage ?: "حدث خطأ غير معروف")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    TasksUiState(errorMessage = e.message ?: "حدث خطأ غير معروف")
                }
            }
        }
    }
}
