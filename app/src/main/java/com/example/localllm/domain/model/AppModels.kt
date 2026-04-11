package com.example.localllm.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

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
    SYSTEM("system");

    companion object {
        fun fromStorageValue(value: String): MessageRole =
            entries.firstOrNull { it.storageValue == value.lowercase() } ?: SYSTEM
    }
}

// ─── Model Domain ─────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class LLMModel(
    val id: String,
    val name: String,
    val family: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val checksumSha256: String,
    val minRamMb: Int,
    val recommendedRamMb: Int,
    val contextLength: Int,
    val quantization: String,
    val tags: List<String> = emptyList(),
    val minAndroidApi: Int = 28,
    val provider: String = "",
    val description: String = ""
)

@Immutable
data class InstalledModel(
    val id: String,
    val name: String,
    val family: String,
    val sizeBytes: Long,
    val filePath: String,
    val installedAt: Long,
    val checksumVerified: Boolean,
    val isActive: Boolean,
    val quantization: String,
    val contextLength: Int
)

enum class ModelDownloadState {
    NOT_DOWNLOADED, DOWNLOADING, PAUSED, VERIFYING, INSTALLED, ERROR
}

@Immutable
data class ModelUiState(
    val model: LLMModel,
    val downloadState: ModelDownloadState = ModelDownloadState.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val isInstalled: Boolean = false,
    val isActive: Boolean = false,
    val isCompatible: Boolean = true,
    val incompatibilityReason: String? = null
)

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
