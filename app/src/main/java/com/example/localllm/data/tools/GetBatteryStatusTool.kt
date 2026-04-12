package com.example.localllm.data.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reports current battery level, charging state, and health.
 *
 * Uses the sticky [Intent.ACTION_BATTERY_CHANGED] broadcast, which is always
 * available without registering a receiver and requires no permissions.
 */
class GetBatteryStatusTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {

    override val name = "get_battery_status"
    override val description = "Returns battery level, charging state, and health"
    override val keywords = listOf("battery", "charge", "power", "بطارية", "شحن", "طاقة")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "تعذّر الحصول على حالة البطارية."
            )

            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val levelPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val statusLabel = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING    -> "جارٍ الشحن"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "يعمل على البطارية"
                BatteryManager.BATTERY_STATUS_FULL        -> "مكتمل الشحن"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "غير متصل بالشاحن"
                else                                       -> "غير معروف"
            }
            val healthLabel = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD        -> "جيدة"
                BatteryManager.BATTERY_HEALTH_OVERHEAT    -> "سخونة زائدة"
                BatteryManager.BATTERY_HEALTH_DEAD        -> "تالفة"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "جهد زائد"
                else                                       -> "غير معروف"
            }
            val pluggedLabel = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC   -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB  -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "لاسلكي"
                else                                -> "لا يوجد"
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

            ToolResult(
                toolName = name,
                success = true,
                resultText = text,
                payload = payload
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "فشل في قراءة حالة البطارية: ${e.message}"
            )
        }
    }
}
