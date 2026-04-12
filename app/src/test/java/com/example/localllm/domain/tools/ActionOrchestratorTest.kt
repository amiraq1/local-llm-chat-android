package com.example.localllm.domain.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ActionOrchestratorTest {

    // ── Fakes ────────────────────────────────────────────────────────────────────

    private fun fakeTool(
        name: String,
        keywords: List<String> = emptyList(),
        result: ToolResult = ToolResult(name, true, "result from $name")
    ) = object : Tool {
        override val name        = name
        override val description = "description for $name"
        override val keywords    = keywords
        override suspend fun execute(params: Map<String, Any>) = result
    }

    private fun failingTool(name: String, keywords: List<String> = emptyList()) =
        fakeTool(name, keywords, ToolResult(name, false, "", errorMessage = "tool $name failed"))

    private fun orchestratorWith(vararg tools: Tool): ActionOrchestrator {
        val registry = ToolRegistry(tools.toSet())
        return ActionOrchestrator(registry)
    }

    // ── handle() – keyword matching ──────────────────────────────────────────────

    @Test
    fun `handle matches tool by single keyword`() = runTest {
        val orchestrator = orchestratorWith(
            fakeTool("device_info", keywords = listOf("device", "info")),
            fakeTool("clipboard",   keywords = listOf("clipboard", "paste"))
        )

        val result = orchestrator.handle("show me clipboard content")

        assertThat(result.success).isTrue()
        assertThat(result.toolName).isEqualTo("clipboard")
    }

    @Test
    fun `handle selects tool with highest keyword score`() = runTest {
        val batteryTool = fakeTool("battery", keywords = listOf("battery", "charge", "power"))
        val deviceTool  = fakeTool("device",  keywords = listOf("device", "info", "power"))
        val orchestrator = orchestratorWith(batteryTool, deviceTool)

        // "battery" appears in battery keywords twice (battery + charge match one each),
        // but "battery charge power" hits battery 3 times vs device 1 time
        val result = orchestrator.handle("check battery charge power level")

        assertThat(result.toolName).isEqualTo("battery")
    }

    @Test
    fun `handle returns failure when no keyword matches`() = runTest {
        val orchestrator = orchestratorWith(
            fakeTool("device_info", keywords = listOf("device")),
            fakeTool("clipboard",   keywords = listOf("clipboard"))
        )

        val result = orchestrator.handle("play some music")

        assertThat(result.success).isFalse()
        assertThat(result.toolName).isEqualTo("none")
        assertThat(result.errorMessage).isNotNull()
    }

    @Test
    fun `handle with empty registry always returns failure`() = runTest {
        val orchestrator = orchestratorWith()

        val result = orchestrator.handle("device info")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `handle propagates tool failure result transparently`() = runTest {
        val orchestrator = orchestratorWith(
            failingTool("broken_tool", keywords = listOf("broken"))
        )

        val result = orchestrator.handle("broken request")

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("broken_tool")
    }

    // ── execute() – direct dispatch by name ──────────────────────────────────────

    @Test
    fun `execute dispatches to correct tool by exact name`() = runTest {
        val orchestrator = orchestratorWith(
            fakeTool("get_device_info"),
            fakeTool("get_clipboard"),
            fakeTool("get_battery_status")
        )

        val result = orchestrator.execute("get_clipboard")

        assertThat(result.success).isTrue()
        assertThat(result.toolName).isEqualTo("get_clipboard")
    }

    @Test
    fun `execute returns failure for unknown tool name`() = runTest {
        val orchestrator = orchestratorWith(fakeTool("known_tool"))

        val result = orchestrator.execute("unknown_tool")

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("unknown_tool")
    }

    @Test
    fun `execute passes params through to tool`() = runTest {
        var capturedParams: Map<String, Any> = emptyMap()
        val capturingTool = object : Tool {
            override val name        = "capturing"
            override val description = ""
            override val keywords    = emptyList<String>()
            override suspend fun execute(params: Map<String, Any>): ToolResult {
                capturedParams = params
                return ToolResult(name, true, "ok")
            }
        }
        val orchestrator = orchestratorWith(capturingTool)
        val params = mapOf("key" to "value")

        orchestrator.execute("capturing", params)

        assertThat(capturedParams).isEqualTo(params)
    }
}
