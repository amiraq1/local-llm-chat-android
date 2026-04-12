package com.example.localllm.domain.tools

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receives a plain-text user request, selects the best matching [Tool] using
 * deterministic keyword matching, executes it, and returns a [ToolResult].
 *
 * Tool selection logic is intentionally simple for Day 1:
 * - Exact tool-name match (case-insensitive) wins first.
 * - Otherwise the tool whose [Tool.keywords] produce the most hits against the
 *   request string is chosen.
 * - LLM-based selection is not wired here yet; it can be added as an optional
 *   strategy behind a feature flag without touching this class.
 */
@Singleton
class ActionOrchestrator @Inject constructor(
    private val registry: ToolRegistry
) {

    suspend fun handle(request: String): ToolResult {
        val tool = selectTool(request)
            ?: return ToolResult(
                toolName = "none",
                success = false,
                resultText = "",
                errorMessage = "لا يوجد أداة مطابقة للطلب: \"$request\""
            )

        Timber.d("ActionOrchestrator: selected tool=${tool.name} for request=\"$request\"")
        return tool.execute()
    }

    /** Executes a specific tool by name, bypassing keyword matching. */
    suspend fun execute(toolName: String, params: Map<String, Any> = emptyMap()): ToolResult {
        val tool = registry.getByName(toolName)
            ?: return ToolResult(
                toolName = toolName,
                success = false,
                resultText = "",
                errorMessage = "أداة غير معرّفة: \"$toolName\""
            )
        return tool.execute(params)
    }

    private fun selectTool(request: String): Tool? {
        val lower = request.lowercase().trim()

        // 1. Exact name match
        registry.getByName(lower)?.let { return it }

        // 2. Keyword scoring — pick the tool with the highest number of keyword hits
        return registry.getAll()
            .map { tool -> tool to tool.keywords.count { kw -> lower.contains(kw.lowercase()) } }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }
}
