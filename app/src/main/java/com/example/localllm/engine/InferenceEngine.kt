package com.example.localllm.engine

import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow

// ─── Core Abstraction Interfaces ──────────────────────────────────────────────

/**
 * Top-level abstraction over any on-device inference engine.
 * Concrete implementations: MLCInferenceEngine, LiteRTInferenceEngine, etc.
 */
interface InferenceEngine {
    /** Load a model from [modelPath]. Returns a [ModelSession] ready for inference. */
    suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession>

    /** Returns true if a model is currently loaded and ready. */
    fun isModelLoaded(): Boolean

    /** Unload the current model and release all GPU/RAM resources. */
    suspend fun unloadModel()

    /** Engine metadata for display. */
    fun getEngineInfo(): EngineInfo
}

/**
 * A live session tied to a loaded model.
 * Create via [InferenceEngine.loadModel]. Dispose via [close].
 */
interface ModelSession {
    /** Stream tokens for the given [request]. Emits [GenerationResponse] until finished or error. */
    fun generate(request: GenerationRequest): Flow<GenerationResponse>

    /** Clear the KV-cache / conversation memory without reloading the model. */
    fun resetContext()

    /** Current number of tokens in the context window. */
    fun getContextLength(): Int

    /** Release the session resources. Call when switching models or closing the app. */
    suspend fun close()
}

// ─── Request / Response ───────────────────────────────────────────────────────

data class ChatMessage(
    val role: MessageRole,
    val content: String
)

data class GenerationRequest(
    val messages: List<ChatMessage>,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stopSequences: List<String> = emptyList()
)

sealed class GenerationResponse {
    data class Token(val text: String) : GenerationResponse()
    data class Finished(
        val finishReason: FinishReason,
        val usage: TokenUsage
    ) : GenerationResponse()
    data class Error(val throwable: Throwable) : GenerationResponse()
}

enum class FinishReason { STOP, MAX_TOKENS, ERROR }

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int
)

// ─── Config / Info ────────────────────────────────────────────────────────────

data class ModelConfig(
    val contextLength: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f
)

data class EngineInfo(
    val name: String,
    val version: String,
    val backend: String   // "MLC", "LiteRT", "Fake"
)

fun Message.toChatMessage(): ChatMessage = ChatMessage(
    role = role,
    content = content
)

fun MessageRole.asWireValue(): String = storageValue
