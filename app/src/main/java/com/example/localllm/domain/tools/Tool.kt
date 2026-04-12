package com.example.localllm.domain.tools

/**
 * Common interface for all app tools/actions.
 *
 * Each tool encapsulates a single device capability that the assistant can invoke.
 * Params are kept as a typed map for flexibility; concrete tools may ignore them
 * or document their expected keys in their own KDoc.
 */
interface Tool {
    /** Unique identifier used by [ToolRegistry] and [ActionOrchestrator]. */
    val name: String

    /** Human-readable description used for display and keyword matching. */
    val description: String

    /** Keywords used by [ActionOrchestrator] to select this tool from a plain-text request. */
    val keywords: List<String>

    /** Execute the tool and return a structured [ToolResult]. Always safe to call from a coroutine. */
    suspend fun execute(params: Map<String, Any> = emptyMap()): ToolResult
}
