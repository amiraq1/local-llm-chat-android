package com.example.localllm.data.tools

import android.os.Build
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import javax.inject.Inject

/**
 * Returns basic device information using [android.os.Build].
 * No permissions required.
 */
class GetDeviceInfoTool @Inject constructor() : Tool {

    override val name = "get_device_info"
    override val description = "Returns manufacturer, model, Android version, and SDK level"
    override val keywords = listOf("device", "info", "model", "manufacturer", "android", "sdk", "معلومات", "جهاز")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val payload = mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model"        to Build.MODEL,
                "brand"        to Build.BRAND,
                "androidVersion" to Build.VERSION.RELEASE,
                "sdkLevel"     to Build.VERSION.SDK_INT.toString(),
                "device"       to Build.DEVICE,
                "hardware"     to Build.HARDWARE,
                "supportedAbis" to Build.SUPPORTED_ABIS.joinToString(", ")
            )

            val text = buildString {
                appendLine("Manufacturer: ${payload["manufacturer"]}")
                appendLine("Model: ${payload["model"]}")
                appendLine("Android: ${payload["androidVersion"]} (SDK ${payload["sdkLevel"]})")
                appendLine("ABI: ${payload["supportedAbis"]}")
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
                errorMessage = "فشل في قراءة معلومات الجهاز: ${e.message}"
            )
        }
    }
}
