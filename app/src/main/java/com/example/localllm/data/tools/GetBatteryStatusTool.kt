package com.example.localllm.data.tools

import android.os.BatteryManager
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolRefusalReason
import com.example.localllm.domain.tools.ToolResult
import com.example.localllm.domain.tools.ToolSensitivity
import javax.inject.Inject

class GetBatteryStatusTool @Inject constructor(
    private val provider: BatteryInfoProvider
) : Tool {

    override val name = "get_battery_status"
    override val description = "Returns battery level, charging state, and health"
    override val keywords = listOf("battery", "charge", "power", "بطارية", "شحن", "طاقة")
    override val sensitivity = ToolSensitivity.PUBLIC

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val info = provider.get()
                ?: return ToolResult(
                    toolName      = name,
                    success       = false,
                    resultText    = "",
                    errorMessage  = "تعذّر الحصول على حالة البطارية.",
                    refusalReason = ToolRefusalReason.INTERNAL_ERROR
                )

            val levelPct = if (info.level >= 0 && info.scale > 0) (info.level * 100 / info.scale) else -1

            val statusLabel = when (info.status) {
                BatteryManager.BATTERY_STATUS_CHARGING     -> "جارٍ الشحن"
                BatteryManager.BATTERY_STATUS_DISCHARGING  -> "يعمل على البطارية"
                BatteryManager.BATTERY_STATUS_FULL         -> "مكتمل الشحن"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "غير متصل بالشاحن"
                else                                        -> "غير معروف"
            }
            val healthLabel = when (info.health) {
                BatteryManager.BATTERY_HEALTH_GOOD          -> "جيدة"
                BatteryManager.BATTERY_HEALTH_OVERHEAT      -> "سخونة زائدة"
                BatteryManager.BATTERY_HEALTH_DEAD          -> "تالفة"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE  -> "جهد زائد"
                else                                         -> "غير معروف"
            }
            val pluggedLabel = when (info.plugged) {
                BatteryManager.BATTERY_PLUGGED_AC       -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB      -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "لاسلكي"
                else                                    -> "لا يوجد"
            }

            val payload = mapOf(
                "levelPercent" to if (levelPct >= 0) "$levelPct%" else "غير متاح",
                "status"       to statusLabel,
                "health"       to healthLabel,
                "plugged"      to pluggedLabel
            )
            val text = buildString {
                appendLine("Level: ${payload["levelPercent"]}")
                appendLine("Status: ${payload["status"]}")
                appendLine("Health: ${payload["health"]}")
                appendLine("Power source: ${payload["plugged"]}")
            }.trimEnd()

            ToolResult(toolName = name, success = true, resultText = text, payload = payload)
        } catch (e: Exception) {
            ToolResult(
                toolName      = name,
                success       = false,
                resultText    = "",
                errorMessage  = "فشل في قراءة حالة البطارية: ${e.message}",
                refusalReason = ToolRefusalReason.INTERNAL_ERROR
            )
        }
    }
}
