package com.example.localllm.domain.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToolRegistryTest {

    // ── Fakes ────────────────────────────────────────────────────────────────────

    private fun fakeTool(name: String, keywords: List<String> = emptyList()) = object : Tool {
        override val name        = name
        override val description = "desc for $name"
        override val keywords    = keywords
        override suspend fun execute(params: Map<String, Any>) =
            ToolResult(name, true, "ok from $name")
    }

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    fun `getAll returns every registered tool`() {
        val t1 = fakeTool("tool_a")
        val t2 = fakeTool("tool_b")
        val t3 = fakeTool("tool_c")
        val registry = ToolRegistry(setOf(t1, t2, t3))

        val names = registry.getAll().map { it.name }
        assertThat(names).containsExactlyElementsIn(listOf("tool_a", "tool_b", "tool_c"))
    }

    @Test
    fun `getAll on empty registry returns empty list`() {
        val registry = ToolRegistry(emptySet())

        assertThat(registry.getAll()).isEmpty()
    }

    @Test
    fun `getByName returns the correct tool`() {
        val target = fakeTool("get_device_info")
        val registry = ToolRegistry(setOf(fakeTool("get_clipboard"), target, fakeTool("get_battery_status")))

        val found = registry.getByName("get_device_info")

        assertThat(found).isNotNull()
        assertThat(found!!.name).isEqualTo("get_device_info")
    }

    @Test
    fun `getByName returns null for unknown name`() {
        val registry = ToolRegistry(setOf(fakeTool("tool_a"), fakeTool("tool_b")))

        assertThat(registry.getByName("does_not_exist")).isNull()
    }

    @Test
    fun `getByName is case-sensitive`() {
        val registry = ToolRegistry(setOf(fakeTool("MyTool")))

        assertThat(registry.getByName("mytool")).isNull()
        assertThat(registry.getByName("MyTool")).isNotNull()
    }

    @Test
    fun `tool returned by getByName is the same instance`() = runTest {
        val tool = fakeTool("echo")
        val registry = ToolRegistry(setOf(tool))

        assertThat(registry.getByName("echo")).isSameInstanceAs(tool)
    }
}
