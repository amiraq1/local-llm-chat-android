package com.example.localllm.data.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetDeviceInfoToolTest {

    // ── Fakes ───────────────────────────────────────────────────────────────────

    private fun fakeTool(provider: DeviceInfoProvider) = GetDeviceInfoTool(provider)

    private val defaultInfo = DeviceInfo(
        manufacturer  = "Google",
        model         = "Pixel 8",
        brand         = "google",
        androidVersion = "14",
        sdkLevel      = 34,
        device        = "shiba",
        hardware      = "qcom",
        supportedAbis = listOf("arm64-v8a", "x86_64")
    )

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    fun `execute returns success with correct tool name`() = runTest {
        val result = fakeTool(StaticDeviceInfoProvider(defaultInfo)).execute()

        assertThat(result.success).isTrue()
        assertThat(result.toolName).isEqualTo("get_device_info")
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `execute payload contains all expected keys`() = runTest {
        val result = fakeTool(StaticDeviceInfoProvider(defaultInfo)).execute()

        val payload = result.payload!!
        assertThat(payload).containsKey("manufacturer")
        assertThat(payload).containsKey("model")
        assertThat(payload).containsKey("brand")
        assertThat(payload).containsKey("androidVersion")
        assertThat(payload).containsKey("sdkLevel")
        assertThat(payload).containsKey("supportedAbis")
    }

    @Test
    fun `execute payload values match provider output`() = runTest {
        val result = fakeTool(StaticDeviceInfoProvider(defaultInfo)).execute()

        val payload = result.payload!!
        assertThat(payload["manufacturer"]).isEqualTo("Google")
        assertThat(payload["model"]).isEqualTo("Pixel 8")
        assertThat(payload["androidVersion"]).isEqualTo("14")
        assertThat(payload["sdkLevel"]).isEqualTo("34")
        assertThat(payload["supportedAbis"]).contains("arm64-v8a")
    }

    @Test
    fun `execute resultText contains manufacturer and model`() = runTest {
        val result = fakeTool(StaticDeviceInfoProvider(defaultInfo)).execute()

        assertThat(result.resultText).contains("Google")
        assertThat(result.resultText).contains("Pixel 8")
        assertThat(result.resultText).contains("14")
    }

    @Test
    fun `execute returns failure when provider throws`() = runTest {
        val result = fakeTool(ThrowingDeviceInfoProvider).execute()

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).isNotNull()
        assertThat(result.resultText).isEmpty()
    }

    @Test
    fun `tool name and keywords are stable`() {
        val tool = fakeTool(StaticDeviceInfoProvider(defaultInfo))

        assertThat(tool.name).isEqualTo("get_device_info")
        assertThat(tool.keywords).contains("device")
        assertThat(tool.keywords).contains("info")
    }

    // ── Local fakes ─────────────────────────────────────────────────────────────

    private class StaticDeviceInfoProvider(private val info: DeviceInfo) : DeviceInfoProvider {
        override fun get() = info
    }

    private object ThrowingDeviceInfoProvider : DeviceInfoProvider {
        override fun get(): DeviceInfo = throw RuntimeException("provider failure")
    }
}
