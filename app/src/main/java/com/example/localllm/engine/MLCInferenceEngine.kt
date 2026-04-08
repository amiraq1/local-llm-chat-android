package com.example.localllm.engine

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.*
import android.content.Context
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.mlc.findBundledMlcModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

codex/fix-audit-findings
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

main
class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

codex/fix-audit-findings
    private var nativeEngine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var currentModelLib: String? = null

    private var engine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var activeModelId: String? = null
main

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        return withContext(Dispatchers.IO) {
            try {
 codex/fix-audit-findings
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

                val modelDir = File(modelPath)
                require(modelDir.isDirectory) { "Model directory not found: $modelPath" }

                val modelId = modelDir.name
                val manifestRecord = findBundledMlcModel(context, modelId)
                    ?: error("Model \"$modelId\" is missing from mlc-app-config.json")

                Timber.i("MLCInferenceEngine: Loading model %s from %s", modelId, modelPath)

                val mlcEngine = engine ?: MLCEngine().also { engine = it }
                runCatching { mlcEngine.unload() }

                mlcEngine.reload(modelDir.absolutePath, manifestRecord.modelLib)

                activeSession = MLCModelSession(mlcEngine)
                activeModelId = modelId
main
                Result.success(activeSession!!)
            } catch (e: Exception) {
                Timber.e(e, "MLCInferenceEngine: Failed to load model")
                nativeEngine = null
                activeSession = null
                Result.failure(e)
            }
        }
    }

    override fun isModelLoaded(): Boolean = activeSession != null && activeModelId != null

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
codex/fix-audit-findings
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

            Timber.i("MLCInferenceEngine: Unloading model and releasing VRAM")
            activeSession?.close()
            activeSession = null
            activeModelId = null
            engine?.unload()
 main
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
 codex/fix-audit-findings
        version = "1.0.0",

        version = "mlc4j-local",
 main
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
codex/fix-audit-findings
    private val engine: MLCEngine,
    private val config: ModelConfig

    private val engineInstance: MLCEngine
 main
) : ModelSession {

    @Volatile
    private var isGenerating = false
    private var contextTokenCount = 0

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        if (isGenerating) {
            close(IllegalStateException("Already generating — wait for current generation to finish"))
            return@callbackFlow
        }
        isGenerating = true

codex/fix-audit-findings
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

        val generationJob = launch(Dispatchers.IO) {
            var finishReason = FinishReason.STOP
            try {
                val responses = engineInstance.chat.completions.create(
                    messages = request.messages.map(ChatMessage::toMlcMessage),
                    max_tokens = request.maxTokens,
                    temperature = request.temperature,
                    top_p = request.topP,
                    stop = request.stopSequences.takeIf { it.isNotEmpty() },
                    stream_options = StreamOptions(include_usage = true)
                )

                for (response in responses) {
                    response.usage?.let { usage ->
                        contextTokenCount = usage.total_tokens
                        trySend(
                            GenerationResponse.Finished(
                                finishReason = finishReason,
                                usage = TokenUsage(
                                    promptTokens = usage.prompt_tokens,
                                    completionTokens = usage.completion_tokens
                                )
                            )
                        )
                        close()
                        return@launch
                    }

                    response.choices.forEach { choice ->
                        finishReason = choice.finish_reason?.toFinishReason() ?: finishReason
                        val text = choice.delta.content?.asText().orEmpty()
                        if (text.isNotEmpty()) {
                            val sendResult: ChannelResult<Unit> =
                                trySend(GenerationResponse.Token(text))
                            if (sendResult.isFailure) {
                                Timber.w("MLCModelSession: Dropped token because the collector is closed")
                            }
                        }
                    }
                }

                trySend(
                    GenerationResponse.Finished(
                        finishReason = finishReason,
                        usage = TokenUsage(promptTokens = 0, completionTokens = 0)
                    )
                )
                close()
            } catch (e: Exception) {
                Timber.e(e, "MLCModelSession: Generation failed")
                trySend(GenerationResponse.Error(e))
                close(e)
            } finally {
                isGenerating = false
            }
        }

        awaitClose {
            generationJob.cancel()
            if (isGenerating) {
                Timber.w("MLCModelSession: Generation cancelled by UI")
main
                isGenerating = false
                // MLC engine will finish current batch; reset clears state
            }
        }
    }

    override fun resetContext() {
        Timber.i("MLCModelSession: Resetting KV Cache")
codex/fix-audit-findings
        engine.reset()
    }

    override fun getContextLength(): Int {
        // MLC doesn't expose a direct getter; return config value
        return config.contextLength
    }

    override suspend fun close() {
        Timber.i("MLCModelSession: Closing session")
        isGenerating = false

        engineInstance.reset()
        contextTokenCount = 0
    }

    override fun getContextLength(): Int = contextTokenCount

    override suspend fun close() {
        Timber.i("MLCModelSession: Closing session")
main
    }
}

private fun ChatMessage.toMlcMessage(): ChatCompletionMessage = ChatCompletionMessage(
    role = when (role) {
        MessageRole.SYSTEM -> ChatCompletionRole.system
        MessageRole.USER -> ChatCompletionRole.user
        MessageRole.ASSISTANT -> ChatCompletionRole.assistant
    },
    content = content
)

private fun String.toFinishReason(): FinishReason = when (lowercase()) {
    "length" -> FinishReason.MAX_TOKENS
    "error" -> FinishReason.ERROR
    else -> FinishReason.STOP
}
