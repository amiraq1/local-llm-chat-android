package com.example.localllm.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AgentState {
    data object Idle : AgentState()
    data object AnalyzingScreen : AgentState()
    data object Thinking : AgentState()
    data class ExecutingAction(val actionDescription: String) : AgentState()
    data class Finished(val result: String) : AgentState()
    data class Error(val message: String) : AgentState()
}

class AgentSessionViewModel(
    private val agentLoopManager: AgentLoopManager
) : ViewModel() {

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private var agentJob: Job? = null

    fun startAgentTask(userGoal: String) {
        val trimmedGoal = userGoal.trim()
        if (trimmedGoal.isBlank()) {
            _agentState.value = AgentState.Error("User goal cannot be blank.")
            return
        }

        if (!AgentAccessibilityService.isConnected()) {
            _agentState.value = AgentState.Error(
                "AccessibilityService is not running. Enable the agent accessibility service first."
            )
            return
        }

        agentJob?.cancel()
        agentJob = viewModelScope.launch {
            _agentState.value = AgentState.AnalyzingScreen

            try {
                val result = agentLoopManager.run(
                    userGoal = trimmedGoal,
                    onUpdate = { update ->
                        when (update) {
                            is AgentLoopManager.LoopUpdate.AnalyzingScreen -> {
                                _agentState.update { AgentState.AnalyzingScreen }
                            }
                            is AgentLoopManager.LoopUpdate.Thinking -> {
                                _agentState.update { AgentState.Thinking }
                            }
                            is AgentLoopManager.LoopUpdate.ExecutingAction -> {
                                _agentState.update {
                                    AgentState.ExecutingAction(update.description)
                                }
                            }
                            is AgentLoopManager.LoopUpdate.Finished -> {
                                // Final state is set below from the returned result to keep one source of truth.
                            }
                        }
                    }
                )

                _agentState.value = when (result.status) {
                    AgentRunStatus.FINISHED,
                    AgentRunStatus.MAX_STEPS_REACHED -> AgentState.Finished(result.finalMessage)
                    AgentRunStatus.FAILED -> AgentState.Error(result.finalMessage)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                _agentState.value = AgentState.Error(
                    error.message ?: "Agent could not access the Android UI state."
                )
            } catch (error: Exception) {
                _agentState.value = AgentState.Error(
                    error.message ?: "Agent execution failed."
                )
            }
        }
    }

    fun resetState() {
        _agentState.value = AgentState.Idle
    }

    fun stopAgentTask() {
        agentJob?.cancel()
        agentJob = null
        _agentState.value = AgentState.Idle
    }

    override fun onCleared() {
        agentJob?.cancel()
        super.onCleared()
    }
}
