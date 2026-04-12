package com.example.localllm.data.tools

import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import javax.inject.Inject

class GetDeviceInfoTool @Inject constructor(
    private val provider: DeviceInfoProvider
) : Tool {

    override val name = "get_device_info"
    override val description = "Returns manufacturer, model, Android version, and SDK level"
    override val keywords = listOf("device", "info", "model", "manufacturer", "android", "sdk", "معلومات", "جهاز")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val info = provider.get()
            val payload = mapOf(
                "manufacturer"   to info.manufacturer,
                "model"          to info.model,
                "brand"          to info.brand,
                "androidVersion" to info.androidVersion,
                "sdkLevel"       to info.sdkLevel.toString(),
                "device"         to info.device,
                "hardware"       to info.hardware,
                "supportedAbis"  to info.supportedAbis.joinToString(", ")
            )
            val text = buildString {
                appendLine("Manufacturer: ${info.manufacturer}")
                appendLine("Model: ${info.model}")
                appendLine("Android: ${info.androidVersion} (SDK ${info.sdkLevel})")
                appendLine("ABI: ${info.supportedAbis.joinToString(", ")}")
            }.trimEnd()

            ToolResult(toolName = name, success = true, resultText = text, payload = payload)
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
