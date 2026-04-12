package com.example.localllm.data.tools

import android.content.ClipboardManager
import android.content.Context
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads the current primary clip from the system clipboard.
 *
 * Android 10+ (API 29) restricts clipboard access to the foreground app or the
 * default IME. Since this app calls the tool while it is in the foreground, the
 * read succeeds. If the app is backgrounded the platform returns an empty clip
 * rather than throwing, so the empty-clipboard branch handles that gracefully.
 *
 * No special permissions are required.
 */
class GetClipboardTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {

    override val name = "get_clipboard"
    override val description = "Reads the current text content from the system clipboard"
    override val keywords = listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val clipText = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()

            if (clipText.isNullOrEmpty()) {
                ToolResult(
                    toolName = name,
                    success = true,
                    resultText = "الحافظة فارغة أو لا تحتوي على نص.",
                    payload = mapOf("content" to "")
                )
            } else {
                ToolResult(
                    toolName = name,
                    success = true,
                    resultText = clipText,
                    payload = mapOf("content" to clipText)
                )
            }
        } catch (e: Exception) {
            ToolResult(
                toolName = name,
                success = false,
                resultText = "",
                errorMessage = "فشل في قراءة الحافظة: ${e.message}"
            )
        }
    }
}
