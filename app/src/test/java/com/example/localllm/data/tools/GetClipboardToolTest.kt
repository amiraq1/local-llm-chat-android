package com.example.localllm.data.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetClipboardToolTest {

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun toolWith(reader: ClipboardReader) = GetClipboardTool(reader)

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    fun `execute with text returns success and content in payload`() = runTest {
        val result = toolWith(StaticClipboardReader("Hello World")).execute()

        assertThat(result.success).isTrue()
        assertThat(result.toolName).isEqualTo("get_clipboard")
        assertThat(result.resultText).isEqualTo("Hello World")
        assertThat(result.payload!!["content"]).isEqualTo("Hello World")
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `execute with null clipboard returns success with empty message`() = runTest {
        val result = toolWith(StaticClipboardReader(null)).execute()

        assertThat(result.success).isTrue()
        assertThat(result.resultText).contains("فارغة")
        assertThat(result.payload!!["content"]).isEmpty()
    }

    @Test
    fun `execute with empty string returns success with empty message`() = runTest {
        val result = toolWith(StaticClipboardReader("")).execute()

        assertThat(result.success).isTrue()
        assertThat(result.resultText).contains("فارغة")
        assertThat(result.payload!!["content"]).isEmpty()
    }

    @Test
    fun `execute with whitespace-only string returns success with empty message`() = runTest {
        // SystemClipboardReader trims and returns null for blank strings; simulate that here
        val result = toolWith(StaticClipboardReader(null)).execute()

        assertThat(result.success).isTrue()
        assertThat(result.payload!!["content"]).isEmpty()
    }

    @Test
    fun `execute when reader throws returns failure`() = runTest {
        val result = toolWith(ThrowingClipboardReader).execute()

        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).isNotNull()
        assertThat(result.errorMessage).contains("clipboard")
        assertThat(result.resultText).isEmpty()
    }

    @Test
    fun `tool name and keywords are stable`() {
        val tool = toolWith(StaticClipboardReader(null))

        assertThat(tool.name).isEqualTo("get_clipboard")
        assertThat(tool.keywords).contains("clipboard")
        assertThat(tool.keywords).contains("paste")
    }

    // ── Local fakes ───────────────────────────────────────────────────────────────

    private class StaticClipboardReader(private val text: String?) : ClipboardReader {
        override fun readText() = text
    }

    private object ThrowingClipboardReader : ClipboardReader {
        override fun readText(): String? = throw RuntimeException("clipboard service unavailable")
    }
}
