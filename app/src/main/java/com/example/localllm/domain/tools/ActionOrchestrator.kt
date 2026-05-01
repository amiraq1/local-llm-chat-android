package com.example.localllm.domain.tools

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a named [Tool] by exact name lookup and returns a [ToolResult].
 *
 * Tool selection (keyword matching / LLM intent) is the responsibility of
 * [ToolCallClassifier]. This class only handles dispatch + consent gating
 * once a tool name is already known.
 *
 * **Consent flow for SENSITIVE tools** (clipboard, screen, etc.):
 *   1. Persistent enable flag: user must opt-in in settings, otherwise the
 *      orchestrator returns a [ToolRefusalReason.DISABLED_BY_USER] result.
 *   2. Session approval: even after opt-in, the user must explicitly approve
 *      this tool for the current process session, otherwise the orchestrator
 *      returns [ToolRefusalReason.NEEDS_USER_APPROVAL] for the UI to prompt.
 *
 * PUBLIC tools (battery, device info) skip both checks.
 */
@Singleton
class ActionOrchestrator @Inject constructor(
    private val registry: ToolRegistry,
    private val consentStore: ToolConsentGate
) {

    suspend fun execute(toolName: String, params: Map<String, Any> = emptyMap()): ToolResult {
        val tool = registry.getByName(toolName)
            ?: return ToolResult(
                toolName = toolName,
                success = false,
                resultText = "",
                errorMessage = "أداة غير معرّفة: \"$toolName\"",
                refusalReason = ToolRefusalReason.UNKNOWN_TOOL
            )

        if (tool.sensitivity == ToolSensitivity.SENSITIVE) {
            val gate = checkConsent(tool)
            if (gate != null) return gate
        }

        Timber.d(
            "ActionOrchestrator: executing tool=%s sensitivity=%s",
            toolName,
            tool.sensitivity
        )
        return tool.execute(params)
    }

    /** Returns a refusal [ToolResult] if the tool is gated, or null if it may run. */
    private suspend fun checkConsent(tool: Tool): ToolResult? {
        if (!consentStore.isPersistentlyEnabledNow(tool.name)) {
            Timber.w("ActionOrchestrator: %s blocked — disabled in settings", tool.name)
            return ToolResult(
                toolName = tool.name,
                success = false,
                resultText = "",
                errorMessage =
                    "هذه الأداة (${tool.name}) معطّلة. فعّلها من الإعدادات للسماح باستخدامها.",
                refusalReason = ToolRefusalReason.DISABLED_BY_USER
            )
        }
        if (!consentStore.isSessionApproved(tool.name)) {
            Timber.w("ActionOrchestrator: %s blocked — needs session approval", tool.name)
            return ToolResult(
                toolName = tool.name,
                success = false,
                resultText = "",
                errorMessage =
                    "تحتاج هذه الأداة (${tool.name}) إلى موافقتك للاستخدام في هذه الجلسة.",
                refusalReason = ToolRefusalReason.NEEDS_USER_APPROVAL
            )
        }
        return null
    }
}
