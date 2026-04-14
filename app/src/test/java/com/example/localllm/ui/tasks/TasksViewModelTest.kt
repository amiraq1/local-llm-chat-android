package com.example.localllm.ui.tasks

import app.cash.turbine.test
import com.example.localllm.domain.tools.ActionOrchestrator
import com.example.localllm.domain.tools.ToolResult
import com.example.localllm.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TasksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val orchestrator = mockk<ActionOrchestrator>()

    private fun viewModel() = TasksViewModel(orchestrator)

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun successResult(name: String) =
        ToolResult(toolName = name, success = true, resultText = "result for $name")

    private fun failureResult(name: String) =
        ToolResult(toolName = name, success = false, resultText = "", errorMessage = "error from $name")

    // ── Initial state ─────────────────────────────────────────────────────────────

    @Test
    fun `initial state is clean`() {
        val vm = viewModel()
        val state = vm.uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isNull()
        assertThat(state.errorMessage).isNull()
    }

    // ── runDeviceInfo ─────────────────────────────────────────────────────────────

    @Test
    fun `runDeviceInfo sets loading then success state`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            successResult("get_device_info")
        val vm = viewModel()

        vm.uiState.test {
            assertThat(awaitItem().isLoading).isFalse()   // initial

            vm.runDeviceInfo()

            assertThat(awaitItem().isLoading).isTrue()    // loading

            val success = awaitItem()                     // done
            assertThat(success.isLoading).isFalse()
            assertThat(success.result).isEqualTo("result for get_device_info")
            assertThat(success.errorMessage).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `runDeviceInfo calls orchestrator with correct tool name`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            successResult("get_device_info")
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()

        coVerify(exactly = 1) { orchestrator.execute("get_device_info", any()) }
    }

    // ── runClipboard ──────────────────────────────────────────────────────────────

    @Test
    fun `runClipboard produces success state with result text`() = runTest {
        coEvery { orchestrator.execute("get_clipboard", any()) } returns
            successResult("get_clipboard")
        val vm = viewModel()

        vm.runClipboard()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isEqualTo("result for get_clipboard")
        assertThat(state.errorMessage).isNull()
    }

    // ── runBatteryStatus ──────────────────────────────────────────────────────────

    @Test
    fun `runBatteryStatus produces success state with result text`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            successResult("get_battery_status")
        val vm = viewModel()

        vm.runBatteryStatus()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isEqualTo("result for get_battery_status")
        assertThat(state.errorMessage).isNull()
    }

    // ── Tool failure (ToolResult.success = false) ─────────────────────────────────

    @Test
    fun `tool failure sets errorMessage and result stays null`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            failureResult("get_device_info")
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isNull()
        assertThat(state.errorMessage).isEqualTo("error from get_device_info")
    }

    @Test
    fun `tool failure with null errorMessage falls back to generic message`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            ToolResult("get_device_info", success = false, resultText = "", errorMessage = null)
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    // ── Uncaught exception ────────────────────────────────────────────────────────

    @Test
    fun `uncaught exception from orchestrator sets errorMessage and clears loading`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } throws
            RuntimeException("network failure")
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isNull()
        assertThat(state.errorMessage).isEqualTo("network failure")
    }

    @Test
    fun `exception with null message falls back to generic message`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } throws
            RuntimeException(null as String?)
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    // ── clearResult ───────────────────────────────────────────────────────────────

    @Test
    fun `clearResult resets state to clean`() = runTest {
        coEvery { orchestrator.execute("get_battery_status", any()) } returns
            successResult("get_battery_status")
        val vm = viewModel()

        vm.runBatteryStatus()
        advanceUntilIdle()
        assertThat(vm.uiState.value.result).isNotNull()

        vm.clearResult()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isNull()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `clearResult on error state resets errorMessage`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            failureResult("get_device_info")
        val vm = viewModel()

        vm.runDeviceInfo()
        advanceUntilIdle()
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.clearResult()

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `clearResult cancels in-flight coroutine and prevents stale state update`() = runTest {
        coEvery { orchestrator.execute("get_device_info", any()) } coAnswers {
            delay(1_000)
            successResult("get_device_info")
        }
        val vm = viewModel()

        vm.runDeviceInfo()     // starts coroutine, suspends at delay
        vm.clearResult()       // cancels the job before delay elapses

        advanceUntilIdle()     // advance past the 1s delay

        // The cancelled coroutine must not write its result to state
        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.result).isNull()
        assertThat(state.errorMessage).isNull()
    }

    // ── Duplicate call guard ──────────────────────────────────────────────────────

    @Test
    fun `second call while loading is ignored`() = runTest {
        coEvery { orchestrator.execute("get_clipboard", any()) } coAnswers {
            delay(1_000)
            successResult("get_clipboard")
        }
        coEvery { orchestrator.execute("get_device_info", any()) } returns
            successResult("get_device_info")
        val vm = viewModel()

        vm.runClipboard()      // starts, suspends at delay — isLoading becomes true
        vm.runDeviceInfo()     // guard fires: isLoading is true, call is dropped

        advanceUntilIdle()

        coVerify(exactly = 1) { orchestrator.execute("get_clipboard", any()) }
        coVerify(exactly = 0) { orchestrator.execute("get_device_info", any()) }
    }
}
