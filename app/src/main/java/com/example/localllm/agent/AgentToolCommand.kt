package com.example.localllm.agent

sealed interface AgentToolCommand {
    data class Tap(val nodeId: Int) : AgentToolCommand
    data class LongPress(val nodeId: Int) : AgentToolCommand
    data class SetText(val nodeId: Int, val text: String) : AgentToolCommand
    data class Scroll(val nodeId: Int, val direction: ScrollDirection) : AgentToolCommand
    data object Back : AgentToolCommand
    data object Home : AgentToolCommand
}

enum class ScrollDirection {
    FORWARD,
    BACKWARD,
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class AgentActionResult(
    val success: Boolean,
    val message: String
)
