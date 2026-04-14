package com.example.localllm.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ParsedNode(
    val id: Int,
    val text: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val boundsInScreen: Rect
)

class AccessibilityScreenParser {

    fun parseTree(root: AccessibilityNodeInfo): List<ParsedNode> {
        val results = mutableListOf<ParsedNode>()
        val rootCopy = AccessibilityNodeInfo.obtain(root)
        traverse(node = rootCopy, nextId = 1, output = results)
        return results
    }

    fun formatForLlm(nodes: List<ParsedNode>): String {
        if (nodes.isEmpty()) return "No actionable visible nodes."

        return nodes.joinToString(separator = "\n") { node ->
            val traits = buildList {
                if (node.isClickable) add("clickable")
                if (node.isEditable) add("editable")
                if (node.isScrollable) add("scrollable")
            }

            val label = if (node.text.isNotBlank()) {
                "'${node.text.replace("'", "\\'")}' "
            } else {
                ""
            }

            val suffix = if (traits.isEmpty()) {
                ""
            } else {
                "(${traits.joinToString(", ")})"
            }

            "[${node.id}] $label$suffix".trimEnd()
        }
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        nextId: Int,
        output: MutableList<ParsedNode>
    ): Int {
        var runningId = nextId

        try {
            if (!node.isVisibleToUser) {
                return runningId
            }

            val text = extractNodeText(node)
            val clickable = node.isClickable
            val editable = node.isEditable
            val scrollable = node.isScrollable
            val actionable = text.isNotBlank() || clickable || editable || scrollable

            if (actionable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                output += ParsedNode(
                    id = runningId,
                    text = text,
                    isClickable = clickable,
                    isEditable = editable,
                    isScrollable = scrollable,
                    boundsInScreen = bounds
                )
                runningId++
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                runningId = traverse(
                    node = child,
                    nextId = runningId,
                    output = output
                )
            }

            return runningId
        } finally {
            node.recycle()
        }
    }

    private fun extractNodeText(node: AccessibilityNodeInfo): String {
        val rawText = when {
            !node.text.isNullOrBlank() -> node.text
            !node.contentDescription.isNullOrBlank() -> node.contentDescription
            else -> null
        }

        return rawText
            ?.toString()
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            .orEmpty()
    }

    private companion object {
        val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
