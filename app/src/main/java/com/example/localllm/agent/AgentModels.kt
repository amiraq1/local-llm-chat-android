package com.example.localllm.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CurrentScreenState(
    val packageName: String? = null,
    val activityName: String? = null,
    val screenTitle: String? = null,
    val focusedNodeId: Int? = null,
    val nodes: List<UiNodeSnapshot> = emptyList()
)

@Serializable
data class UiNodeSnapshot(
    val id: Int,
    val text: String? = null,
    val hintText: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val checked: Boolean? = null,
    val boundsInScreen: String? = null
)

@Serializable
data class ToolParameterDefinition(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameterDefinition>,
    val example: JsonObject
)

@Serializable
data class AgentDecision(
    val thought: String,
    val action: ToolCall
)

@Serializable
data class ToolCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class AgentStepRecord(
    val step: Int,
    val thought: String,
    val actionName: String,
    val actionArgs: JsonObject,
    val execution: ActionExecutionResult? = null
)

@Serializable
data class ActionExecutionResult(
    val success: Boolean,
    val message: String,
    val metadata: JsonObject? = null
)

@Serializable
data class AgentRunResult(
    val status: AgentRunStatus,
    val totalSteps: Int,
    val finalMessage: String,
    val history: List<AgentStepRecord>
)

@Serializable
enum class AgentRunStatus {
    @SerialName("finished")
    FINISHED,

    @SerialName("max_steps_reached")
    MAX_STEPS_REACHED,

    @SerialName("failed")
    FAILED
}

interface GemmaInferenceEngine {
    suspend fun generate(prompt: String): String
}

interface ScreenStateProvider {
    suspend fun getCurrentScreenState(): CurrentScreenState
}

interface ActionExecutor {
    suspend fun execute(action: ToolCall): ActionExecutionResult
}
