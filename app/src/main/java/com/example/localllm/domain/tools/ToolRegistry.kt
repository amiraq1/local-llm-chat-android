package com.example.localllm.domain.tools

/**
 * Central registry for all available [Tool] instances.
 *
 * Populated via Hilt injection so new tools are added by contributing to the
 * multibinding set in [com.example.localllm.di.ToolsModule] — no changes
 * to this class are needed when a new tool is introduced.
 */
class ToolRegistry(private val tools: Set<Tool>) {

    /** Returns every registered tool. */
    fun getAll(): List<Tool> = tools.toList()

    /** Returns the tool with the given [name], or null if not found. */
    fun getByName(name: String): Tool? = tools.firstOrNull { it.name == name }
}
