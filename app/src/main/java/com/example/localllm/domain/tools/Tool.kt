package com.example.localllm.domain.tools

/**
 * Sensitivity classification for a [Tool].
 *
 * Drives consent gating in [ActionOrchestrator]:
 *  - [PUBLIC]    : runs without explicit user consent (e.g. battery level, OS version).
 *  - [SENSITIVE] : reads PII or device state that may contain personal data
 *                  (clipboard, screen content). Requires the user to enable the
 *                  tool in settings AND approve invocation per session.
 */
enum class ToolSensitivity {
    PUBLIC,
    SENSITIVE
}

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

    /**
     * Privacy/safety classification — defaults to [ToolSensitivity.PUBLIC].
     * Override and set to [ToolSensitivity.SENSITIVE] for any tool that reads
     * user data such as clipboard, screen content, contacts, or location.
     */
    val sensitivity: ToolSensitivity get() = ToolSensitivity.PUBLIC

    /** Execute the tool and return a structured [ToolResult]. Always safe to call from a coroutine. */
    suspend fun execute(params: Map<String, Any> = emptyMap()): ToolResult
}
