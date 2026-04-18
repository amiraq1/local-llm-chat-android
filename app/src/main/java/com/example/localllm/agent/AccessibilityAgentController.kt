package com.example.localllm.agent

import com.example.localllm.accessibility.AccessibilityAgentStateStore
import com.example.localllm.accessibility.AccessibilityNodeRegistry
import com.example.localllm.accessibility.AccessibilityScreenParser
import com.example.localllm.accessibility.ParsedScreenState
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class AccessibilityAgentController @Inject constructor(
    private val stateStore: AccessibilityAgentStateStore,
    private val nodeRegistry: AccessibilityNodeRegistry,
    private val screenParser: AccessibilityScreenParser
) {

    private val globalActionPerformer = AtomicReference<((Int) -> Boolean)?>(null)

    val screenState: StateFlow<ParsedScreenState> = stateStore.screenState

    fun attachGlobalActionPerformer(performer: (Int) -> Boolean) {
        globalActionPerformer.set(performer)
    }

    fun detachGlobalActionPerformer() {
        globalActionPerformer.set(null)
    }

    fun currentPrompt(includeBounds: Boolean = false): String {
        return screenParser.toCompactText(
            snapshot = stateStore.screenState.value,
            includeBounds = includeBounds
        )
    }

    suspend fun execute(command: AgentToolCommand): AgentActionResult {
        val performer = globalActionPerformer.get()
            ?: return AgentActionResult(
                success = false,
                message = "Accessibility service is not connected."
            )

        return nodeRegistry.execute(command, performer)
    }
}
