package com.example.localllm.accessibility

import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localllm.agent.AgentActionResult
import com.example.localllm.agent.AgentToolCommand
import com.example.localllm.agent.ScrollDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AccessibilityNodeRegistry @Inject constructor(
    @com.example.localllm.di.DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher
) {

    private val mutex = Mutex()
    private val nodesById = LinkedHashMap<Int, AccessibilityNodeInfo>()

    suspend fun replaceSnapshot(
        root: AccessibilityNodeInfo?,
        config: ScreenParseConfig = ScreenParseConfig()
    ) = withContext(defaultDispatcher) {
        mutex.withLock {
            recycleAllLocked()

            if (root == null) return@withLock

            val queue = ArrayDeque<RegistryNodeFrame>()
            queue.addLast(RegistryNodeFrame(root, 0))
            var nextId = 1

            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()

                try {
                    if (!node.isVisibleToUser) continue

                    if (
                        nextId <= config.maxInteractiveNodes &&
                        isActionable(node)
                    ) {
                        nodesById[nextId] = AccessibilityNodeInfo.obtain(node)
                        nextId++
                    }

                    if (depth >= config.maxDepth) continue

                    for (index in 0 until node.childCount) {
                        val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                        queue.addLast(RegistryNodeFrame(child, depth + 1))
                    }
                } finally {
                    node.recycle()
                }
            }
        }
    }

    suspend fun execute(
        command: AgentToolCommand,
        performGlobalAction: (Int) -> Boolean
    ): AgentActionResult = withContext(defaultDispatcher) {
        mutex.withLock {
            when (command) {
                is AgentToolCommand.Tap -> executeNodeCommand(
                    command.nodeId,
                    "tap"
                ) { performAction(AccessibilityNodeInfo.ACTION_CLICK) }

                is AgentToolCommand.LongPress -> executeNodeCommand(
                    command.nodeId,
                    "long press"
                ) { performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }

                is AgentToolCommand.SetText -> executeNodeCommand(
                    command.nodeId,
                    "set text"
                ) {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            command.text
                        )
                    }
                    performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }

                is AgentToolCommand.Scroll -> executeNodeCommand(
                    command.nodeId,
                    "scroll ${command.direction.name.lowercase()}"
                ) {
                    performAction(command.direction.toAccessibilityAction())
                }

                AgentToolCommand.Back -> {
                    val success = performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    )
                    AgentActionResult(success, if (success) "Performed BACK" else "Failed to perform BACK")
                }

                AgentToolCommand.Home -> {
                    val success = performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    )
                    AgentActionResult(success, if (success) "Performed HOME" else "Failed to perform HOME")
                }
            }
        }
    }

    suspend fun clear() = withContext(defaultDispatcher) {
        mutex.withLock {
            recycleAllLocked()
        }
    }

    private fun executeNodeCommand(
        nodeId: Int,
        description: String,
        action: AccessibilityNodeInfo.() -> Boolean
    ): AgentActionResult {
        val node = nodesById[nodeId]
            ?: return AgentActionResult(false, "Node [$nodeId] is no longer available.")

        val success = runCatching { node.action() }.getOrDefault(false)
        return AgentActionResult(
            success = success,
            message = if (success) "Performed $description on [$nodeId]" else "Failed to $description on [$nodeId]"
        )
    }

    private fun recycleAllLocked() {
        nodesById.values.forEach { node ->
            runCatching { node.recycle() }
        }
        nodesById.clear()
    }

    private fun isActionable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable ||
            node.isLongClickable ||
            node.isScrollable ||
            node.isEditable
    }
}

private fun ScrollDirection.toAccessibilityAction(): Int {
    return when (this) {
        ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        ScrollDirection.UP -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        }

        ScrollDirection.DOWN -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
        }

        ScrollDirection.LEFT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        }

        ScrollDirection.RIGHT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
        }
    }
}

private data class RegistryNodeFrame(
    val node: AccessibilityNodeInfo,
    val depth: Int
)
