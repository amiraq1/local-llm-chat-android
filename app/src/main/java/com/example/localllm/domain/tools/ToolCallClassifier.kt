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
 * **Algorithm:**
 *  1. **Exact tool-name match** (case-insensitive) → highest priority.
 *  2. **Substring keyword scoring** — each tool is scored by the number of
 *     distinct keywords that appear (case-insensitive) in the message.
 *     Substring (not whole-word) matching is used so Arabic prefixes like
 *     `ال` still match (`الحافظة` ⊇ `حافظة`).
 *  3. **Sensitivity-aware threshold** — privacy-affecting tools require more
 *     evidence to trigger:
 *       - **PUBLIC** tools (battery, device info) require score ≥ 1.
 *       - **SENSITIVE** tools (clipboard, screen, contacts, etc.) require
 *         either a hit on a *canonical* signal (the exact tool name OR the
 *         first keyword in the tool's list) OR score ≥ [SENSITIVE_MIN_HITS].
 *         A single common word is not enough to silently invoke them.
 *  4. **Tie-breaking** is deterministic: alphabetical by tool name. This keeps
 *     classifier output stable across DI iteration order changes.
 *
 * The scoring logic intentionally mirrors [ActionOrchestrator] so the two
 * components always agree on which tool is selected.
 *
 * **Future extension point:** inject a feature flag that swaps in an LLM-based
 * intent detector without touching the call sites.
 */
@Singleton
class ToolCallClassifier @Inject constructor(
    private val registry: ToolRegistry
) {

    fun classify(message: String): ClassificationResult {
        val lower = message.lowercase().trim()
        if (lower.isEmpty()) return ClassificationResult.LlmChat

        // 1. Exact tool-name match.
        registry.getByName(lower)?.let { return ClassificationResult.ToolCall(it.name) }

        // 2. Score each tool by distinct keyword hits (substring, case-insensitive).
        val scored = registry.getAll().map { tool ->
            val canonical = canonicalSignals(tool)
            val matches = tool.keywords
                .map { it.lowercase() }
                .filter { kw -> kw.isNotEmpty() && lower.contains(kw) }
                .toSet()

            val hasCanonical = matches.any { it in canonical }

            val passes = when (tool.sensitivity) {
                ToolSensitivity.PUBLIC ->
                    matches.isNotEmpty()
                ToolSensitivity.SENSITIVE ->
                    hasCanonical || matches.size >= SENSITIVE_MIN_HITS
            }

            ScoredTool(tool, score = matches.size, passes = passes)
        }

        val best = scored
            .filter { it.passes && it.score > 0 }
            // Higher score wins; on ties prefer the alphabetically-first name.
            .maxWithOrNull(
                compareBy<ScoredTool> { it.score }
                    .thenByDescending { it.tool.name }
            )

        return best?.let { ClassificationResult.ToolCall(it.tool.name) }
            ?: ClassificationResult.LlmChat
    }

    /**
     * "Canonical signals" for a tool: tokens that on their own constitute strong
     * evidence the user really wants this tool — used to bypass the higher
     * threshold for SENSITIVE tools when there is no ambiguity.
     */
    private fun canonicalSignals(tool: Tool): Set<String> = buildSet {
        add(tool.name.lowercase())
        tool.keywords.firstOrNull()?.lowercase()?.let { add(it) }
    }

    private data class ScoredTool(val tool: Tool, val score: Int, val passes: Boolean)

    companion object {
        /**
         * Sensitive tools require this many distinct keyword hits to trigger by
         * score alone (canonical-signal hits trigger regardless of count).
         */
        internal const val SENSITIVE_MIN_HITS = 2
    }
}
