package com.example.localllm.domain.tools

import javax.inject.Inject
import javax.inject.Singleton

// ─── Result type ──────────────────────────────────────────────────────────────

/**
 * The outcome of classifying a single chat message.
 *
 * [ToolCall] — the message maps to a known tool; skip the LLM entirely.
 * [LlmChat]  — ordinary conversation; route to the inference engine as usual.
 */
sealed class ClassificationResult {
    data class ToolCall(val toolName: String) : ClassificationResult()
    object LlmChat : ClassificationResult()
}

// ─── Classifier ───────────────────────────────────────────────────────────────

/**
 * Deterministic, zero-latency classifier that decides whether a chat message
 * should be handled by a [Tool] or routed to the LLM.
 *
 * **Algorithm (Day 2 — keyword matching):**
 * 1. Exact tool-name match (case-insensitive).
 * 2. Highest keyword-hit count across all registered tools (score ≥ 1 required).
 * 3. Tie: first tool returned by the registry wins (set iteration order, stable per run).
 *
 * The scoring logic intentionally mirrors [ActionOrchestrator] so the two
 * components always agree on which tool is selected.
 *
 * **Day 3 extension point:** inject a feature flag that swaps in an LLM-based
 * intent detector without touching [ChatViewModel] or [ActionOrchestrator].
 */
@Singleton
class ToolCallClassifier @Inject constructor(
    private val registry: ToolRegistry
) {
    fun classify(message: String): ClassificationResult {
        val lower = message.lowercase().trim()

        // 1. Exact name match (user typed "get_battery_status" literally, for example)
        registry.getByName(lower)?.let { return ClassificationResult.ToolCall(it.name) }

        // 2. Keyword scoring
        val best = registry.getAll()
            .map { tool -> tool to tool.keywords.count { kw -> lower.contains(kw.lowercase()) } }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first

        return if (best != null) ClassificationResult.ToolCall(best.name)
        else ClassificationResult.LlmChat
    }
}
