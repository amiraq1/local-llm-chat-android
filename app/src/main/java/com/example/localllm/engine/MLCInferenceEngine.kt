package com.example.localllm.engine

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.*
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Production MLC LLM inference engine.
 * Uses the MLC4J native library to run Qwen3-0.6B (or any compiled model) on-device.
 *
 * API flow:
 *   1. MLCEngine() → starts background threads for the native runtime
 *   2. engine.reload(modelPath, modelLib) → loads model weights into GPU/NPU
 *   3. engine.chat.completions.create(messages, ...) → streams ChatCompletionStreamResponse
 *   4. engine.reset() → clears KV-cache between conversations
 *   5. engine.unload() → releases model from memory
 */
class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private var nativeEngine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var currentModelLib: String? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.i("MLCInferenceEngine: Initializing native engine and loading model from $modelPath")

                // Create the native engine (starts background inference + stream-back threads)
                val engine = MLCEngine()

                // The modelLib identifier must match what was compiled by mlc_llm package.
                // Convention: the model directory name under assets or the lib suffix.
                // We extract it from the modelPath or use a known default.
                val modelLib = extractModelLib(modelPath)
                currentModelLib = modelLib

                engine.reload(modelPath, modelLib)
                nativeEngine = engine

                activeSession = MLCModelSession(engine, config)
                Timber.i("MLCInferenceEngine: Model loaded successfully (lib=$modelLib)")
                Result.success(activeSession!!)
            } catch (e: Exception) {
                Timber.e(e, "MLCInferenceEngine: Failed to load model")
                nativeEngine = null
                activeSession = null
                Result.failure(e)
            }
        }
    }

    override fun isModelLoaded(): Boolean = nativeEngine != null

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            try {
                Timber.i("MLCInferenceEngine: Unloading model and releasing VRAM")
                activeSession?.close()
                activeSession = null
                nativeEngine?.unload()
                nativeEngine = null
                currentModelLib = null
            } catch (e: Exception) {
                Timber.e(e, "MLCInferenceEngine: Error during model unload")
                // Force cleanup even on error
                activeSession = null
                nativeEngine = null
                currentModelLib = null
            }
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = "1.0.0",
        backend = "MLC"
    )

    /**
     * Extracts the model library identifier from the model path.
     * The modelLib must match the compiled library name registered via `system://` prefix.
     * Falls back to the Qwen3 lib compiled in our mlc4j module.
     */
    private fun extractModelLib(modelPath: String): String {
        // If the path contains a known model pattern, map to the correct lib
        return when {
            modelPath.contains("Qwen3-0.6B", ignoreCase = true) ->
                "qwen3_q0f16_e709b04052d95e24b38d40e4259e1f14"
            else -> {
                // Default: assume the last path segment is the model ID,
                // and the lib shares a known naming convention
                Timber.w("MLCInferenceEngine: Unknown model path '$modelPath', using default Qwen3 lib")
                "qwen3_q0f16_e709b04052d95e24b38d40e4259e1f14"
            }
        }
    }
}

class MLCModelSession(
    private val engine: MLCEngine,
    private val config: ModelConfig
) : ModelSession {

    @Volatile
    private var isGenerating = false

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        if (isGenerating) {
            close(IllegalStateException("Already generating — wait for current generation to finish"))
            return@callbackFlow
        }
        isGenerating = true

        try {
            // Map our domain messages to MLC OpenAI-compatible messages
            val mlcMessages = request.messages.map { msg ->
                ChatCompletionMessage(
                    role = when (msg.role) {
                        com.example.localllm.domain.model.MessageRole.USER -> ChatCompletionRole.user
                        com.example.localllm.domain.model.MessageRole.ASSISTANT -> ChatCompletionRole.assistant
                        com.example.localllm.domain.model.MessageRole.SYSTEM -> ChatCompletionRole.system
                    },
                    content = msg.content
                )
            }

            // Call MLC engine streaming API
            val channel = engine.chat.completions.create(
                messages = mlcMessages,
                max_tokens = request.maxTokens,
                temperature = request.temperature,
                top_p = request.topP,
                stop = request.stopSequences.ifEmpty { null },
                stream = true,
                stream_options = StreamOptions(include_usage = true)
            )

            var promptTokens = 0
            var completionTokens = 0

            // Consume the MLC response channel
            for (response in channel) {
                if (!isGenerating) break // cancelled

                // Extract streamed token delta
                for (choice in response.choices) {
                    val deltaContent = choice.delta.content?.asText()
                    if (!deltaContent.isNullOrEmpty()) {
                        trySend(GenerationResponse.Token(deltaContent))
                    }

                    // Check for finish
                    choice.finish_reason?.let { reason ->
                        val finishReason = when (reason) {
                            "stop" -> FinishReason.STOP
                            "length" -> FinishReason.MAX_TOKENS
                            else -> FinishReason.STOP
                        }
                        trySend(
                            GenerationResponse.Finished(
                                finishReason,
                                TokenUsage(promptTokens, completionTokens)
                            )
                        )
                    }
                }

                // Capture usage stats if present (sent in final chunk)
                response.usage?.let { usage ->
                    promptTokens = usage.prompt_tokens
                    completionTokens = usage.completion_tokens
                }
            }

            close()
        } catch (e: Exception) {
            Timber.e(e, "MLCModelSession: Generation error")
            trySend(GenerationResponse.Error(e))
            close(e)
        } finally {
            isGenerating = false
        }

        awaitClose {
            if (isGenerating) {
                Timber.w("MLCModelSession: Generation cancelled by downstream collector")
                isGenerating = false
                // MLC engine will finish current batch; reset clears state
            }
        }
    }

    override fun resetContext() {
        Timber.i("MLCModelSession: Resetting KV Cache")
        engine.reset()
    }

    override fun getContextLength(): Int {
        // MLC doesn't expose a direct getter; return config value
        return config.contextLength
    }

    override suspend fun close() {
        Timber.i("MLCModelSession: Closing session")
        isGenerating = false
    }
}
