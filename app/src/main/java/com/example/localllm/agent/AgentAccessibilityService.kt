package com.example.localllm.agent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class AgentAccessibilityService : AccessibilityService(), ScreenStateProvider {

    private val screenParser = AccessibilityScreenParser()
    @Volatile
    private var lastPackageName: String? = null
    @Volatile
    private var lastWindowClassName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("AgentAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        lastPackageName = event.packageName?.toString()
        lastWindowClassName = event.className?.toString()

        Timber.v(
            "AgentAccessibilityService event type=%d package=%s class=%s",
            event.eventType,
            event.packageName,
            event.className
        )
    }

    override fun onInterrupt() {
        Timber.w("AgentAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override suspend fun getCurrentScreenState(): CurrentScreenState =
        withContext(Dispatchers.Main.immediate) {
            val root = rootInActiveWindow ?: return@withContext CurrentScreenState()

            try {
                val parsedNodes = screenParser.parseTree(root)
                val focusedNodeId = findFocusedParsedNodeId(root)

                CurrentScreenState(
                    packageName = root.packageName?.toString() ?: lastPackageName,
                    activityName = lastWindowClassName,
                    screenTitle = extractScreenTitle(root),
                    focusedNodeId = focusedNodeId,
                    nodes = parsedNodes.map { parsedNode ->
                        UiNodeSnapshot(
                            id = parsedNode.id,
                            text = parsedNode.text.ifBlank { null },
                            clickable = parsedNode.isClickable,
                            editable = parsedNode.isEditable,
                            scrollable = parsedNode.isScrollable,
                            boundsInScreen = parsedNode.boundsInScreen.flattenToString()
                        )
                    }
                )
            } finally {
                root.recycle()
            }
        }

    suspend fun dispatchClick(nodeId: Int): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val root = rootInActiveWindow ?: return@withContext false

            try {
                val targetNode = findNodeByParsedId(root, nodeId) ?: return@withContext false

                try {
                    if (!targetNode.isVisibleToUser || !targetNode.isClickable) {
                        Timber.w("AgentAccessibilityService: node %d is not clickable", nodeId)
                        return@withContext false
                    }

                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } finally {
                    targetNode.recycle()
                }
            } finally {
                root.recycle()
            }
        }

    fun captureScreenState(): String {
        val root = rootInActiveWindow ?: return "No active window found."

        return try {
            val parsedNodes = screenParser.parseTree(root)
            screenParser.formatForLlm(parsedNodes)
        } finally {
            root.recycle()
        }
    }

    fun clickNode(nodeInfo: AccessibilityNodeInfo): Boolean {
        return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun extractScreenTitle(root: AccessibilityNodeInfo): String? {
        val candidates = listOf(
            root.paneTitle,
            root.contentDescription,
            root.text
        )

        return candidates
            .firstOrNull { !it.isNullOrBlank() }
            ?.toString()
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
    }

    private fun findFocusedParsedNodeId(root: AccessibilityNodeInfo): Int? {
        val rootCopy = AccessibilityNodeInfo.obtain(root)
        val result = findFocusedParsedNodeIdRecursive(node = rootCopy, nextId = 1)
        return result.focusedId
    }

    private fun findFocusedParsedNodeIdRecursive(
        node: AccessibilityNodeInfo,
        nextId: Int
    ): FocusSearchResult {
        var runningId = nextId

        try {
            if (!node.isVisibleToUser) {
                return FocusSearchResult(runningId, null)
            }

            val text = extractNodeText(node)
            val actionable = isActionableNode(node, text)
            val currentId = if (actionable) runningId else null

            if (actionable) {
                runningId++
                if (node.isFocused || node.isAccessibilityFocused) {
                    return FocusSearchResult(runningId, currentId)
                }
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val childResult = findFocusedParsedNodeIdRecursive(child, runningId)
                runningId = childResult.nextId
                if (childResult.focusedId != null) {
                    return childResult
                }
            }

            return FocusSearchResult(runningId, null)
        } finally {
            node.recycle()
        }
    }

    private fun findNodeByParsedId(
        root: AccessibilityNodeInfo,
        targetId: Int
    ): AccessibilityNodeInfo? {
        val rootCopy = AccessibilityNodeInfo.obtain(root)
        val result = findNodeByParsedIdRecursive(
            node = rootCopy,
            targetId = targetId,
            nextId = 1
        )
        return result.node
    }

    private fun findNodeByParsedIdRecursive(
        node: AccessibilityNodeInfo,
        targetId: Int,
        nextId: Int
    ): NodeSearchResult {
        var runningId = nextId

        try {
            if (!node.isVisibleToUser) {
                return NodeSearchResult(runningId, null)
            }

            val text = extractNodeText(node)
            if (isActionableNode(node, text)) {
                if (runningId == targetId) {
                    return NodeSearchResult(
                        nextId = runningId + 1,
                        node = AccessibilityNodeInfo.obtain(node)
                    )
                }
                runningId++
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                val childResult = findNodeByParsedIdRecursive(
                    node = child,
                    targetId = targetId,
                    nextId = runningId
                )
                runningId = childResult.nextId
                if (childResult.node != null) {
                    return childResult
                }
            }

            return NodeSearchResult(runningId, null)
        } finally {
            node.recycle()
        }
    }

    private fun isActionableNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return text.isNotBlank() || node.isClickable || node.isEditable || node.isScrollable
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

    private data class NodeSearchResult(
        val nextId: Int,
        val node: AccessibilityNodeInfo?
    )

    private data class FocusSearchResult(
        val nextId: Int,
        val focusedId: Int?
    )

    companion object {
        @Volatile
        private var instance: AgentAccessibilityService? = null

        fun getInstance(): AgentAccessibilityService? = instance

        fun isConnected(): Boolean = instance != null

        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}

private fun Rect.flattenToString(): String = "$left,$top,$right,$bottom"
