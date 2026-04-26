package com.example.localllm.domain.model

import kotlinx.serialization.Serializable

@Serializable
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
    val minAndroidApi: Int = 28
)

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

data class ModelUiState(
    val model: LLMModel,
    val downloadState: ModelDownloadState = ModelDownloadState.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val isInstalled: Boolean = false,
    val isActive: Boolean = false,
    val isCompatible: Boolean = true,
    val incompatibilityReason: String? = null
)
