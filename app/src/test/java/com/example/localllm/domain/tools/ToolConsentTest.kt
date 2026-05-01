package com.example.localllm.domain.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verifies that [ActionOrchestrator] correctly gates SENSITIVE tools through the
 * two-layer consent flow (persistent flag + session approval) and that PUBLIC
 * tools bypass the gate.
 */
class ToolConsentTest {

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private fun fakeTool(
        name: String,
        sensitivity: ToolSensitivity,
        executed: () -> Unit = {}
    ) = object : Tool {
        override val name        = name
        override val description = "fake $name"
        override val keywords    = emptyList<String>()
        override val sensitivity = sensitivity
        override suspend fun execute(params: Map<String, Any>): ToolResult {
            executed()
            return ToolResult(name, true, "ran")
        }
    }

    /** Configurable in-memory gate for testing. */
    private class FakeGate(
        var persistent: Boolean = false,
        var session: Boolean = false
    ) : ToolConsentGate {
        override suspend fun isPersistentlyEnabledNow(toolName: String) = persistent
        override fun isSessionApproved(toolName: String) = session
    }

    private fun orchestrator(tool: Tool, gate: ToolConsentGate): ActionOrchestrator =
        ActionOrchestrator(ToolRegistry(setOf(tool)), gate)

    // ── PUBLIC tools bypass the gate ──────────────────────────────────────────

    @Test
    fun `public tool runs even when persistent flag is off`() = runTest {
        var ran = false
        val tool = fakeTool("get_battery_status", ToolSensitivity.PUBLIC) { ran = true }
        val orch = orchestrator(tool, FakeGate(persistent = false, session = false))

        val result = orch.execute("get_battery_status")

        assertThat(result.success).isTrue()
        assertThat(result.refusalReason).isNull()
        assertThat(ran).isTrue()
    }

    // ── SENSITIVE tools — persistent flag gate ────────────────────────────────

    @Test
    fun `sensitive tool refused with DISABLED_BY_USER when persistent flag is off`() = runTest {
        var ran = false
        val tool = fakeTool("get_clipboard", ToolSensitivity.SENSITIVE) { ran = true }
        val orch = orchestrator(tool, FakeGate(persistent = false, session = true))

        val result = orch.execute("get_clipboard")

        assertThat(result.success).isFalse()
        assertThat(result.refusalReason).isEqualTo(ToolRefusalReason.DISABLED_BY_USER)
        assertThat(ran).isFalse()
    }

    // ── SENSITIVE tools — session approval gate ───────────────────────────────

    @Test
    fun `sensitive tool refused with NEEDS_USER_APPROVAL when session not approved`() = runTest {
        var ran = false
        val tool = fakeTool("read_screen", ToolSensitivity.SENSITIVE) { ran = true }
        val orch = orchestrator(tool, FakeGate(persistent = true, session = false))

        val result = orch.execute("read_screen")

        assertThat(result.success).isFalse()
        assertThat(result.refusalReason).isEqualTo(ToolRefusalReason.NEEDS_USER_APPROVAL)
        assertThat(ran).isFalse()
    }

    // ── SENSITIVE tools — both gates pass ─────────────────────────────────────

    @Test
    fun `sensitive tool runs when both persistent and session approval are granted`() = runTest {
        var ran = false
        val tool = fakeTool("get_clipboard", ToolSensitivity.SENSITIVE) { ran = true }
        val orch = orchestrator(tool, FakeGate(persistent = true, session = true))

        val result = orch.execute("get_clipboard")

        assertThat(result.success).isTrue()
        assertThat(result.refusalReason).isNull()
        assertThat(ran).isTrue()
    }

    // ── Unknown tool ──────────────────────────────────────────────────────────

    @Test
    fun `unknown tool returns UNKNOWN_TOOL refusal`() = runTest {
        val tool = fakeTool("get_battery_status", ToolSensitivity.PUBLIC)
        val orch = orchestrator(tool, FakeGate(persistent = true, session = true))

        val result = orch.execute("does_not_exist")

        assertThat(result.success).isFalse()
        assertThat(result.refusalReason).isEqualTo(ToolRefusalReason.UNKNOWN_TOOL)
    }

    // ── Refusal precedence: persistent flag is checked before session ─────────

    @Test
    fun `persistent flag is checked before session approval`() = runTest {
        // Even with session approved, missing persistent flag must dominate.
        val tool = fakeTool("get_clipboard", ToolSensitivity.SENSITIVE)
        val orch = orchestrator(tool, FakeGate(persistent = false, session = true))

        val result = orch.execute("get_clipboard")

        assertThat(result.refusalReason).isEqualTo(ToolRefusalReason.DISABLED_BY_USER)
    }
}
