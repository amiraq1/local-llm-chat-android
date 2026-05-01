package com.example.localllm.domain.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolCallClassifierTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private fun fakeTool(
        name: String,
        keywords: List<String>,
        sensitivity: ToolSensitivity = ToolSensitivity.PUBLIC
    ) = object : Tool {
        override val name        = name
        override val description = "description for $name"
        override val keywords    = keywords
        override val sensitivity = sensitivity
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

    // ── SENSITIVE-tool gating threshold ───────────────────────────────────────

    @Test
    fun `sensitive tool is NOT triggered by a single non-canonical keyword hit`() {
        // "copy" alone (not canonical: not the tool name, not the first keyword)
        // must not be enough to invoke a SENSITIVE tool — protects privacy.
        val classifier = classifierWith(
            fakeTool(
                name = "get_clipboard",
                keywords = listOf("clipboard", "copy", "paste"),
                sensitivity = ToolSensitivity.SENSITIVE
            )
        )

        val result = classifier.classify("هل تستطيع copy هذا الملف؟")

        assertThat(result).isInstanceOf(ClassificationResult.LlmChat::class.java)
    }

    @Test
    fun `sensitive tool IS triggered by canonical keyword (first keyword)`() {
        // First keyword is treated as canonical — single hit is enough.
        val classifier = classifierWith(
            fakeTool(
                name = "get_clipboard",
                keywords = listOf("clipboard", "copy", "paste"),
                sensitivity = ToolSensitivity.SENSITIVE
            )
        )

        val result = classifier.classify("show me the clipboard")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    @Test
    fun `sensitive tool IS triggered by exact tool name in message`() {
        val classifier = classifierWith(
            fakeTool(
                name = "read_screen",
                keywords = listOf("screen", "ui", "view"),
                sensitivity = ToolSensitivity.SENSITIVE
            )
        )

        // Exact-name match is the very first rule in the classifier.
        val result = classifier.classify("read_screen")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("read_screen")
    }

    @Test
    fun `sensitive tool IS triggered when at least two non-canonical keywords match`() {
        val classifier = classifierWith(
            fakeTool(
                name = "get_clipboard",
                keywords = listOf("clipboard", "copy", "paste"),
                sensitivity = ToolSensitivity.SENSITIVE
            )
        )

        // Two non-canonical hits ("copy" + "paste") clear SENSITIVE_MIN_HITS=2.
        val result = classifier.classify("can you copy and paste it?")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
        assertThat((result as ClassificationResult.ToolCall).toolName).isEqualTo("get_clipboard")
    }

    @Test
    fun `public tool with same single-keyword hit IS still triggered`() {
        // Sanity check: the threshold raise applies ONLY to SENSITIVE tools.
        val classifier = classifierWith(
            fakeTool(
                name = "get_battery_status",
                keywords = listOf("battery", "charge"),
                sensitivity = ToolSensitivity.PUBLIC
            )
        )

        val result = classifier.classify("how is the charge?")

        assertThat(result).isInstanceOf(ClassificationResult.ToolCall::class.java)
    }

    @Test
    fun `tie-break is deterministic and alphabetical by tool name`() {
        // Both tools have identical keyword sets and same score; alphabetical
        // tie-break must consistently pick the same one regardless of insertion order.
        val a = fakeTool("aaa_tool", listOf("device"))
        val b = fakeTool("bbb_tool", listOf("device"))

        val classifierAB = classifierWith(a, b)
        val classifierBA = classifierWith(b, a)

        val resA = classifierAB.classify("device")
        val resB = classifierBA.classify("device")

        assertThat((resA as ClassificationResult.ToolCall).toolName).isEqualTo("aaa_tool")
        assertThat((resB as ClassificationResult.ToolCall).toolName).isEqualTo("aaa_tool")
    }
}
