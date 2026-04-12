package com.example.localllm.accessibility.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localllm.accessibility.AccessibilityAgentStateStore
import com.example.localllm.accessibility.AccessibilityNodeRegistry
import com.example.localllm.accessibility.AccessibilityScreenParser
import com.example.localllm.accessibility.ScreenParseConfig
import com.example.localllm.agent.AccessibilityAgentController
import com.example.localllm.di.ApplicationScope
import com.example.localllm.di.DefaultDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AccessibilityAgentService : AccessibilityService() {

    @Inject
    lateinit var screenParser: AccessibilityScreenParser

    @Inject
    lateinit var nodeRegistry: AccessibilityNodeRegistry

    @Inject
    lateinit var stateStore: AccessibilityAgentStateStore

    @Inject
    lateinit var controller: AccessibilityAgentController

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    private var snapshotJob: Job? = null

    private val parseConfig = ScreenParseConfig(
        maxInteractiveNodes = 48,
        maxTextSnippets = 32,
        maxDepth = 24,
        maxLabelLength = 72
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller.attachGlobalActionPerformer(::performGlobalAction)
        Timber.i("AccessibilityAgentService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!shouldCapture(event)) return

        val root = rootInActiveWindow ?: return
        val parserRoot = AccessibilityNodeInfo.obtain(root)
        val registryRoot = AccessibilityNodeInfo.obtain(root)
        root.recycle()

        snapshotJob?.cancel()
        snapshotJob = applicationScope.launch(defaultDispatcher) {
            try {
                // Coalesce rapid event bursts so we do not parse the entire tree for every small mutation.
                delay(120)

                val snapshot = screenParser.parse(parserRoot, parseConfig)
                nodeRegistry.replaceSnapshot(registryRoot, parseConfig)
                stateStore.update(snapshot)
            } catch (error: Throwable) {
                runCatching { parserRoot.recycle() }
                runCatching { registryRoot.recycle() }
                Timber.w(error, "Failed to refresh accessibility snapshot")
            }
        }
    }

    override fun onInterrupt() {
        snapshotJob?.cancel()
        Timber.i("AccessibilityAgentService interrupted")
    }

    override fun onDestroy() {
        snapshotJob?.cancel()
        controller.detachGlobalActionPerformer()

        applicationScope.launch(defaultDispatcher) {
            nodeRegistry.clear()
            stateStore.clear()
        }

        super.onDestroy()
    }

    private fun shouldCapture(event: AccessibilityEvent?): Boolean {
        val type = event?.eventType ?: return true
        return type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED
    }
}
