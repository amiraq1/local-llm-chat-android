package com.example.localllm.data.tools

import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import javax.inject.Inject

class GetClipboardTool @Inject constructor(
    private val reader: ClipboardReader
) : Tool {

    override val name = "get_clipboard"
    override val description = "Reads the current text content from the system clipboard"
    override val keywords = listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val text = reader.readText()
            if (text.isNullOrEmpty()) {
                ToolResult(
                    toolName    = name,
                    success     = true,
                    resultText  = "الحافظة فارغة أو لا تحتوي على نص.",
                    payload     = mapOf("content" to "")
                )
            } else {
                ToolResult(
                    toolName   = name,
                    success    = true,
                    resultText = text,
                    payload    = mapOf("content" to text)
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolName     = name,
                success      = false,
                resultText   = "",
                errorMessage = "فشل في قراءة الحافظة: ${e.message}"
            )
        }
    }
}
