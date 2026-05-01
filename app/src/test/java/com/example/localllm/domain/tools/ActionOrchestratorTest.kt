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

    /** Always-allow gate — these tests cover non-sensitive dispatch only. */
    private val openGate = object : ToolConsentGate {
        override suspend fun isPersistentlyEnabledNow(toolName: String) = true
        override fun isSessionApproved(toolName: String) = true
    }

    private fun orchestratorWith(vararg tools: Tool): ActionOrchestrator {
        val registry = ToolRegistry(tools.toSet())
        return ActionOrchestrator(registry, openGate)
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
