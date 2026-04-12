package com.example.localllm.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolCallClassifierTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private fun fakeTool(name: String, keywords: List<String>) = object : Tool {
        override val name        = name
        override val description = "description for $name"
        override val keywords    = keywords
        override suspend fun execute(params: Map<String, Any>) =
            ToolResult(name, true, "ok")
    }

    private fun classifierWith(vararg tools: Tool): ToolCallClassifier {
        val registry = ToolRegistry(tools.toSet())
        return ToolCallClassifier(registry)
    }

    // ── LlmChat — no tool matches ──────────────────────────────────────────────

    @Test
    fun `returns LlmChat when no keyword matches`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info")),
            fakeTool("get_clipboard",      listOf("clipboard", "paste")),
            fakeTool("get_battery_status", listOf("battery", "charge"))
        )

        val result = classifier.classify("اشرح لي الذكاء الاصطناعي")

        assertThat(result).isInstanceOf(ClassificationResult.LlmChat::class.java)
    }

    @Test
    fun `returns LlmChat for empty message`() {
        val classifier = classifierWith(
            fakeTool("get_device_info", listOf("device"))
        )

        assertThat(classifier.classify("")).isInstanceOf(ClassificationResult.LlmChat::class.java)
    }

    @Test
    fun `returns LlmChat when registry is empty`() {
        val classifier = classifierWith()

        assertThat(classifier.classify("battery level")).isInstanceOf(ClassificationResult.LlmChat::class.java)
    }

    // ── ToolCall — keyword matching ────────────────────────────────────────────

    @Test
    fun `returns ToolCall when single keyword matches`() {
        val classifier = classifierWith(
            fakeTool("get_clipboard", listOf("clipboard", "paste"))
        )

        val result = classifier.classify("ما محتوى الـ clipboard؟")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    @Test
    fun `returns ToolCall with highest score when multiple tools partially match`() {
        val classifier = classifierWith(
            fakeTool("get_battery_status", listOf("battery", "charge", "power")),
            fakeTool("get_device_info",    listOf("device", "info", "power"))
        )

        // "battery charge power" hits battery×3, device×1 → battery wins
        val result = classifier.classify("كم battery charge power متبقي؟")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_battery_status")
    }

    @Test
    fun `keyword matching is case-insensitive`() {
        val classifier = classifierWith(
            fakeTool("get_clipboard", listOf("clipboard"))
        )

        val result = classifier.classify("CLIPBOARD content please")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
    }

    @Test
    fun `keyword matching trims leading and trailing whitespace`() {
        val classifier = classifierWith(
            fakeTool("get_device_info", listOf("device"))
        )

        val result = classifier.classify("   device info   ")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
    }

    @Test
    fun `Arabic keywords are matched correctly`() {
        val classifier = classifierWith(
            fakeTool("get_clipboard", listOf("حافظة", "clipboard"))
        )

        val result = classifier.classify("ما محتوى الحافظة؟")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    // ── ToolCall — exact name match ────────────────────────────────────────────

    @Test
    fun `exact tool name match takes priority over keyword scoring`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info")),
            fakeTool("get_battery_status", listOf("battery", "charge", "device", "info"))
        )

        // Exact name hit for get_device_info should win even though
        // get_battery_status has more keyword hits for "device info".
        val result = classifier.classify("get_device_info")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_device_info")
    }

    @Test
    fun `exact name match is case-insensitive`() {
        val classifier = classifierWith(
            fakeTool("get_clipboard", listOf("clipboard"))
        )

        val result = classifier.classify("GET_CLIPBOARD")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    // ── Real keyword set regression ────────────────────────────────────────────

    @Test
    fun `device info keywords classify correctly`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info", "model", "android", "hardware", "manufacturer", "build")),
            fakeTool("get_clipboard",      listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")),
            fakeTool("get_battery_status", listOf("battery", "charge", "charging", "power", "level", "بطارية", "شحن"))
        )

        val result = classifier.classify("ما معلومات الـ device الخاص بي؟")

        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_device_info")
    }

    @Test
    fun `battery keywords classify correctly`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info", "model", "android", "hardware", "manufacturer", "build")),
            fakeTool("get_clipboard",      listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")),
            fakeTool("get_battery_status", listOf("battery", "charge", "charging", "power", "level", "بطارية", "شحن"))
        )

        val result = classifier.classify("كم نسبة شحن البطارية؟")

        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_battery_status")
    }

    @Test
    fun `clipboard keywords classify correctly`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info", "model", "android", "hardware", "manufacturer", "build")),
            fakeTool("get_clipboard",      listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")),
            fakeTool("get_battery_status", listOf("battery", "charge", "charging", "power", "level", "بطارية", "شحن"))
        )

        val result = classifier.classify("اعرض محتوى الحافظة")

        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    @Test
    fun `general LLM question is not misclassified`() {
        val classifier = classifierWith(
            fakeTool("get_device_info",    listOf("device", "info", "model", "android", "hardware", "manufacturer", "build")),
            fakeTool("get_clipboard",      listOf("clipboard", "copy", "paste", "clip", "حافظة", "نسخ", "لصق")),
            fakeTool("get_battery_status", listOf("battery", "charge", "charging", "power", "level", "بطارية", "شحن"))
        )

        val result = classifier.classify("اكتب لي قصيدة عن البحر")

        assertThat(result).isInstanceOf(ClassificationResult.LlmChat::class.java)
    }
}
