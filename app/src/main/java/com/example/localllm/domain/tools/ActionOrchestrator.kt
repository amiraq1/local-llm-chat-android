package com.example.localllm.domain.tools

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes a named [Tool] by exact name lookup and returns a [ToolResult].
 *
 * Tool selection (keyword matching / LLM intent) is the responsibility of
 * [com.example.localllm.domain.tools.ToolCallClassifier]. This class only
 * handles dispatch and execution once a tool name is already known.
 */
@Singleton
class ActionOrchestrator @Inject constructor(
    private val registry: ToolRegistry
) {

    suspend fun execute(toolName: String, params: Map<String, Any> = emptyMap()): ToolResult {
        val tool = registry.getByName(toolName)
            ?: return ToolResult(
                toolName = toolName,
                success = false,
                resultText = "",
                errorMessage = "أداة غير معرّفة: \"$toolName\""
            )
        Timber.d("ActionOrchestrator: executing tool=%s", toolName)
        return tool.execute(params)
    }
}
