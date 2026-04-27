package com.example.localllm.data.tools

import com.example.localllm.agent.AgentAccessibilityService
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import javax.inject.Inject

class ReadScreenTool @Inject constructor() : Tool {

    override val name = "read_screen"
    override val description = "Captures and parses the current screen content using accessibility services"
    override val keywords = listOf("screen", "ui", "view", "read", "شاشة", "قراءة", "واجهة")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.getInstance()
            ?: return ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "خدمة الوصول غير مفعلة. يرجى تفعيل خدمة المساعد في الإعدادات."
            )

        return try {
            val screenState = service.captureScreenState()
            ToolResult(
                toolName = name,
                success = true,
                resultText = screenState,
                payload = mapOf("raw_content" to screenState)
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "فشل في قراءة محتوى الشاشة: ${e.message}"
            )
        }
    }
}
