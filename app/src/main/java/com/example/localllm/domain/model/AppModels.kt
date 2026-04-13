package com.example.localllm.domain.model

import androidx.compose.runtime.Immutable

// ─── Chat Domain Models ────────────────────────────────────────────────────────

@Immutable
data class Conversation(
    val id: Long = 0,
    val title: String,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isArchived: Boolean = false
)

@Immutable
data class Message(
    val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tokensUsed: Int? = null,
    val generationTimeMs: Long? = null
)

enum class MessageRole(val storageValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    /** Result of a device-tool execution persisted verbatim in the conversation. */
    TOOL("tool");

    companion object {
        fun fromStorageValue(value: String): MessageRole =
            entries.firstOrNull { it.storageValue == value.lowercase() } ?: SYSTEM
    }
}

// ─── Benchmark Domain ─────────────────────────────────────────────────────────

@Immutable
data class BenchmarkResult(
    val id: Long = 0,
    val modelId: String,
    val runAt: Long,
    val ttftMs: Long,
    val tokensPerSecond: Double,
    val totalTokens: Int,
    val deviceRamMb: Int,
    val promptText: String
)

// ─── Settings Domain ──────────────────────────────────────────────────────────

@Immutable
data class AppSettings(
    val activeModelId: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512,
    val contextLength: Int = 2048,
    val wifiOnlyDownload: Boolean = true,
    val darkMode: Boolean = false
)
