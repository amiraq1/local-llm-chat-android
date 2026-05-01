package com.example.localllm.domain.tools

/**
 * Why a tool refused to execute (when [ToolResult.success] is false).
 */
enum class ToolRefusalReason {
    /** The tool name was not registered. */
    UNKNOWN_TOOL,
    /** The tool is SENSITIVE and the user has not enabled it in settings. */
    DISABLED_BY_USER,
    /** Enabled but the per-session approval is missing — UI must prompt the user. */
    NEEDS_USER_APPROVAL,
    /** Tool ran but threw an internal error. */
    INTERNAL_ERROR
}

/**
 * Structured result returned by every [Tool] execution.
 *
 * @param toolName       Name of the tool that produced this result.
 * @param success        True if the tool executed without errors.
 * @param resultText     Human-readable summary suitable for display in the UI.
 * @param payload        Optional structured key-value data for downstream processing.
 * @param errorMessage   Populated only when [success] is false.
 * @param refusalReason  When [success] is false, indicates why; UI uses this to
 *                       distinguish a permission prompt from a real error.
 */
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val resultText: String,
    val payload: Map<String, String>? = null,
    val errorMessage: String? = null,
    val refusalReason: ToolRefusalReason? = null
)
