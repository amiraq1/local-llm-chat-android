package com.example.localllm.data.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetBatteryStatusToolTest {

    // BatteryManager integer constants (avoid Android framework dependency in unit tests)
    private object Battery {
        const val STATUS_CHARGING    = 2
        const val STATUS_DISCHARGING = 3
        const val STATUS_FULL        = 5
        const val HEALTH_GOOD        = 2
        const val PLUGGED_AC         = 1
        const val PLUGGED_USB        = 2
        const val PLUGGED_WIRELESS   = 4
        const val STATUS_UNKNOWN     = 1
        const val HEALTH_UNKNOWN     = 1
    }

    private fun toolWith(provider: BatteryInfoProvider) = GetBatteryStatusTool(provider)

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    fun `execute returns success when provider has data`() = runTest {
        val info = BatteryInfo(
            level   = 75,
            scale   = 100,
            status  = Battery.STATUS_CHARGING,
            health  = Battery.HEALTH_GOOD,
            plugged = Battery.PLUGGED_AC
        )
        val result = toolWith(StaticBatteryInfoProvider(info)).execute()

        assertThat(result.success).isTrue()
        assertThat(result.toolName).isEqualTo("get_battery_status")
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `execute calculates level percentage correctly`() = runTest {
        val info = BatteryInfo(level = 3, scale = 4,
            status = Battery.STATUS_DISCHARGING, health = Battery.HEALTH_GOOD, plugged = -1)
        val result = toolWith(StaticBatteryInfoProvider(info)).execute()

        assertThat(result.payload!!["levelPercent"]).isEqualTo("75%")
    }

    @Test
    fun `execute payload contains all expected keys`() = runTest {
        val info = BatteryInfo(level = 50, scale = 100,
            status = Battery.STATUS_FULL, health = Battery.HEALTH_GOOD, plugged = Battery.PLUGGED_USB)
        val result = toolWith(StaticBatteryInfoProvider(info)).execute()

        val payload = result.payload!!
        assertThat(payload).containsKey("levelPercent")
        assertThat(payload).containsKey("status")
        assertThat(payload).containsKey("health")
        assertThat(payload).containsKey("plugged")
    }

    @Test
    fun `execute returns failure when provider returns null`() = runTest {
        val result = toolWith(NullBatteryInfoProvider).execute()

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).isNotNull()
        assertThat(result.resultText).isEmpty()
    }

    @Test
    fun `execute returns failure when provider throws`() = runTest {
        val result = toolWith(ThrowingBatteryInfoProvider).execute()

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).isNotNull()
    }

    @Test
    fun `execute shows غير متاح when level is unavailable`() = runTest {
        val info = BatteryInfo(level = -1, scale = -1,
            status = Battery.STATUS_UNKNOWN, health = Battery.HEALTH_UNKNOWN, plugged = -1)
        val result = toolWith(StaticBatteryInfoProvider(info)).execute()

        assertThat(result.success).isTrue()
        assertThat(result.payload!!["levelPercent"]).isEqualTo("غير متاح")
    }

    @Test
    fun `tool name and keywords are stable`() {
        val tool = toolWith(NullBatteryInfoProvider)

        assertThat(tool.name).isEqualTo("get_battery_status")
        assertThat(tool.keywords).contains("battery")
        assertThat(tool.keywords).contains("charge")
    }

    // ── Local fakes ───────────────────────────────────────────────────────────────

    private class StaticBatteryInfoProvider(private val info: BatteryInfo) : BatteryInfoProvider {
        override fun get() = info
    }

    private object NullBatteryInfoProvider : BatteryInfoProvider {
        override fun get(): BatteryInfo? = null
    }

    private object ThrowingBatteryInfoProvider : BatteryInfoProvider {
        override fun get(): BatteryInfo = throw RuntimeException("battery service failure")
    }
}
