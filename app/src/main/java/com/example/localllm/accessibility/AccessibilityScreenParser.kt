package com.example.localllm.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class AccessibilityScreenParser @Inject constructor() {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun parse(
        root: AccessibilityNodeInfo?,
        config: ScreenParseConfig = ScreenParseConfig()
    ): ParsedScreenState = withContext(Dispatchers.Default) {
        parseInternal(root, config)
    }

    suspend fun parseAsJson(
        root: AccessibilityNodeInfo?,
        config: ScreenParseConfig = ScreenParseConfig()
    ): String = json.encodeToString(parse(root, config))

    suspend fun parseAsPrompt(
        root: AccessibilityNodeInfo?,
        config: ScreenParseConfig = ScreenParseConfig(),
        includeBounds: Boolean = false
    ): String = toCompactText(parse(root, config), includeBounds)

    fun toCompactText(
        snapshot: ParsedScreenState,
        includeBounds: Boolean = false
    ): String = buildString {
        snapshot.packageName?.let { append("app=").append(it).append('\n') }

        if (snapshot.visibleText.isNotEmpty()) {
            append("text: ")
            append(snapshot.visibleText.joinToString(" | "))
            append('\n')
        }

        snapshot.nodes.forEach { node ->
            append('[').append(node.id).append("] ")
            append(node.role)

            node.label?.takeIf { it.isNotBlank() }?.let {
                append(" '").append(it).append('\'')
            }

            node.hint?.takeIf { it.isNotBlank() && it != node.label }?.let {
                append(" hint='").append(it).append('\'')
            }

            append(" (").append(node.actions.joinToString(",")).append(')')

            if (includeBounds) {
                node.bounds?.let { bounds ->
                    append(" @")
                    append(bounds.left).append(',')
                    append(bounds.top).append(',')
                    append(bounds.right).append(',')
                    append(bounds.bottom)
                }
            }

            append('\n')
        }
    }.trim()

    private fun parseInternal(
        root: AccessibilityNodeInfo?,
        config: ScreenParseConfig
    ): ParsedScreenState {
        if (root == null) return ParsedScreenState()

        val queue = ArrayDeque<ParserNodeFrame>()
        queue.addLast(ParserNodeFrame(root, 0))

        val actionableNodes = ArrayList<ParsedAccessibilityNode>(config.maxInteractiveNodes)
        val visibleText = ArrayList<String>(config.maxTextSnippets)
        val seenText = LinkedHashSet<String>(config.maxTextSnippets)

        var packageName: String? = root.packageName?.toString()
        var className: String? = root.className?.toString()
        var nextId = 1

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()

            try {
                if (!node.isVisibleToUser) continue

                if (packageName == null) packageName = node.packageName?.toString()
                if (className == null) className = node.className?.toString()

                collectVisibleText(node, seenText, visibleText, config)

                if (
                    actionableNodes.size < config.maxInteractiveNodes &&
                    isActionable(node)
                ) {
                    actionableNodes += node.toParsedNode(nextId++, config.maxLabelLength)
                }

                if (depth >= config.maxDepth) continue

                val childCount = node.childCount
                for (index in 0 until childCount) {
                    val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                    queue.addLast(ParserNodeFrame(child, depth + 1))
                }
            } finally {
                node.recycle()
            }
        }

        return ParsedScreenState(
            packageName = packageName,
            rootClassName = className,
            nodes = actionableNodes,
            visibleText = visibleText
        )
    }

    private fun collectVisibleText(
        node: AccessibilityNodeInfo,
        seenText: MutableSet<String>,
        visibleText: MutableList<String>,
        config: ScreenParseConfig
    ) {
        if (visibleText.size >= config.maxTextSnippets) return

        val snippets = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString()
        )

        snippets.forEach { raw ->
            val normalized = raw.normalizeForPrompt(config.maxLabelLength)
            if (normalized.isBlank()) return@forEach
            if (seenText.add(normalized)) {
                visibleText += normalized
                if (visibleText.size >= config.maxTextSnippets) return
            }
        }
    }

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
            node.isLongClickable ||
            node.isScrollable ||
            node.isEditable
    }

    private fun AccessibilityNodeInfo.toParsedNode(
        id: Int,
        maxLabelLength: Int
    ): ParsedAccessibilityNode {
        val rect = Rect().also(::getBoundsInScreen)
        val label = bestLabel()?.normalizeForPrompt(maxLabelLength)
        val hint = hintText?.toString()?.normalizeForPrompt(maxLabelLength)

        return ParsedAccessibilityNode(
            id = id,
            role = inferRole(),
            label = label,
            hint = hint,
            actions = inferActions(),
            bounds = rect.toCompactBounds()
        )
    }

    private fun AccessibilityNodeInfo.bestLabel(): String? {
        return listOfNotNull(
            text?.toString(),
            contentDescription?.toString(),
            hintText?.toString()
        ).firstOrNull { it.isNotBlank() }
    }

    private fun AccessibilityNodeInfo.inferRole(): String {
        val classNameValue = className?.toString().orEmpty().substringAfterLast('.').lowercase()

        return when {
            "imagebutton" in classNameValue -> "IconButton"
            "button" in classNameValue -> "Button"
            "edittext" in classNameValue || "textfield" in classNameValue -> "Field"
            "switch" in classNameValue -> "Switch"
            "checkbox" in classNameValue -> "Checkbox"
            "radiobutton" in classNameValue -> "Radio"
            "recyclerview" in classNameValue || "listview" in classNameValue -> "List"
            "scrollview" in classNameValue || isScrollable -> "Scroll"
            isEditable -> "Field"
            isClickable -> "Control"
            else -> "Element"
        }
    }

    private fun AccessibilityNodeInfo.inferActions(): List<String> = buildList {
        if (isClickable) add("tap")
        if (isLongClickable) add("hold")
        if (isEditable) add("type")
        if (isScrollable) add("scroll")
    }
}

@Serializable
data class ParsedScreenState(
    val packageName: String? = null,
    val rootClassName: String? = null,
    val nodes: List<ParsedAccessibilityNode> = emptyList(),
    val visibleText: List<String> = emptyList()
)

@Serializable
data class ParsedAccessibilityNode(
    val id: Int,
    val role: String,
    val label: String? = null,
    val hint: String? = null,
    val actions: List<String> = emptyList(),
    val bounds: CompactBounds? = null
)

@Serializable
data class CompactBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class ScreenParseConfig(
    val maxInteractiveNodes: Int = 48,
    val maxTextSnippets: Int = 32,
    val maxDepth: Int = 24,
    val maxLabelLength: Int = 72
)

private data class ParserNodeFrame(
    val node: AccessibilityNodeInfo,
    val depth: Int
)

private fun String.normalizeForPrompt(maxLength: Int): String {
    return trim()
        .replace(Regex("\\s+"), " ")
        .let { normalized ->
            if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 1) + "…"
        }
}

private fun Rect.toCompactBounds(): CompactBounds? {
    if (isEmpty) return null
    return CompactBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}
