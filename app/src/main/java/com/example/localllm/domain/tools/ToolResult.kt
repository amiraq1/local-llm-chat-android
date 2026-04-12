package com.example.localllm.domain.tools

/**
 * Structured result returned by every [Tool] execution.
 *
 * @param toolName     Name of the tool that produced this result.
 * @param success      True if the tool executed without errors.
 * @param resultText   Human-readable summary suitable for display in the UI.
 * @param payload      Optional structured key-value data for downstream processing.
 * @param errorMessage Populated only when [success] is false.
 */
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val resultText: String,
    val payload: Map<String, String>? = null,
    val errorMessage: String? = null
)
