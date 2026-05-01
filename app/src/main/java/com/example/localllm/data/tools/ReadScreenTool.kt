package com.example.localllm.data.tools

import com.example.localllm.agent.AgentAccessibilityService
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolRefusalReason
import com.example.localllm.domain.tools.ToolResult
import com.example.localllm.domain.tools.ToolSensitivity
import javax.inject.Inject
import timber.log.Timber

class ReadScreenTool @Inject constructor() : Tool {

    override val name = "read_screen"
    override val description = "Captures and parses the current screen content using accessibility services"
    override val keywords = listOf("screen", "ui", "view", "read", "شاشة", "قراءة", "واجهة")
    override val sensitivity = ToolSensitivity.SENSITIVE

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val service = AgentAccessibilityService.getInstance()
            ?: return ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "خدمة الوصول غير مفعلة. يرجى تفعيل خدمة المساعد في الإعدادات.",
                refusalReason = ToolRefusalReason.INTERNAL_ERROR
            )

        return try {
            val screenState = service.captureScreenState()
            // Do not log screen contents — may contain personal messages, OTPs, etc.
            Timber.d("ReadScreenTool: captured screen len=%d", screenState.length)
            ToolResult(
                toolName = name,
                success = true,
                resultText = screenState,
                payload = mapOf("raw_content" to screenState)
            )
        } catch (e: Exception) {
            Timber.w(e, "ReadScreenTool: failed")
            ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "فشل في قراءة محتوى الشاشة: ${e.message}",
                refusalReason = ToolRefusalReason.INTERNAL_ERROR
            )
        }
    }
}
