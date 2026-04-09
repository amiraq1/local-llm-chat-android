package com.example.localllm.engine

import ai.mlc.mlcllm.MLCEngine
 HEAD
import ai.mlc.mlcllm.OpenAIProtocol.*
import android.content.Context
 codex/fix-audit-findings
import com.example.localllm.domain.model.MessageRole

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
main
import dagger.hilt.android.qualifiers.ApplicationContext

import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
import android.content.Context
 (Fix merge artifacts and normalize MLC engine wiring)
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.mlc.findBundledMlcModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import timber.log.Timber
<<<<<<< HEAD
import java.io.File
import javax.inject.Inject

 codex/fix-audit-findings

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
main
=======

>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

<<<<<<< HEAD
 codex/fix-audit-findings
    private var engine: MLCEngine? = null

codex/fix-audit-findings
    private var nativeEngine: MLCEngine? = null
 main
    private var activeSession: MLCModelSession? = null
    private var activeModelId: String? = null

=======
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
    private var engine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var activeModelId: String? = null

    override suspend fun loadModel(
        modelPath: String,
        config: ModelConfig
    ): Result<ModelSession> {
        return withContext(Dispatchers.IO) {
            try {
<<<<<<< HEAD
codex/fix-audit-findings
                val modelDir = File(modelPath)

 codex/fix-audit-findings
                Timber.i("MLCInferenceEngine: Initializing native engine and loading model from $modelPath")
 main

                // جدار الحماية: التأكد أن المسار هو مجلد وليس ملف .bin
                require(modelDir.exists()) { "Model path not found: $modelPath" }
                require(modelDir.isDirectory) { "MLC requires a model directory, not a single file: $modelPath" }

                val modelId = modelDir.name

                Timber.i("MLCInferenceEngine: Loading model %s from %s", modelId, modelPath)

 codex/fix-audit-findings
                val mlcEngine = engine ?: MLCEngine().also { engine = it }

                // التأكد من إغلاق أي جلسة سابقة
                activeSession?.close()
                runCatching { mlcEngine.unload() }

                val modelLib = extractModelLib(modelId)
                mlcEngine.reload(modelDir.absolutePath, modelLib)

                val session = MLCModelSession(mlcEngine)
                activeSession = session
                activeModelId = modelId

                Result.success(session)

                activeSession = MLCModelSession(engine, config)
                Timber.i("MLCInferenceEngine: Model loaded successfully (lib=$modelLib)")

=======
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
                val modelDir = File(modelPath)
                require(modelDir.isDirectory) {
                    "Model directory not found: $modelPath"
                }

                val modelId = modelDir.name
                val manifestRecord = findBundledMlcModel(context, modelId)
                    ?: error("Model \"$modelId\" is missing from mlc-app-config.json")

                Timber.i(
                    "MLCInferenceEngine: Loading model %s from %s",
                    modelId,
                    modelPath
                )

                val mlcEngine = engine ?: MLCEngine().also { engine = it }
                runCatching { mlcEngine.unload() }

                mlcEngine.reload(modelDir.absolutePath, manifestRecord.modelLib)

                activeSession = MLCModelSession(mlcEngine, config)
                activeModelId = modelId

                Result.success(activeSession!!)
 main
            } catch (e: Exception) {
                Timber.e(e, "MLCInferenceEngine: Failed to load model")
<<<<<<< HEAD
=======
                activeSession = null
                activeModelId = null
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
                Result.failure(e)
            }
        }
    }

    override fun isModelLoaded(): Boolean {
        return activeSession != null && activeModelId != null
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
<<<<<<< HEAD
 codex/fix-audit-findings

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

 main
=======
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
            Timber.i("MLCInferenceEngine: Unloading model and releasing VRAM")

            runCatching { activeSession?.close() }
                .onFailure { Timber.e(it, "MLCInferenceEngine: Error while closing active session") }

            activeSession = null
            activeModelId = null
<<<<<<< HEAD
            engine?.unload()
 codex/fix-audit-findings

 main
main
=======

            runCatching { engine?.unload() }
                .onFailure { Timber.e(it, "MLCInferenceEngine: Error during model unload") }
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = "mlc4j-local",
        backend = "MLC"
    )
<<<<<<< HEAD

    private fun extractModelLib(modelId: String): String {
        return when {
            modelId.contains("Qwen3", ignoreCase = true) ->
                "qwen3_q0f16_e709b04052d95e24b38d40e4259e1f14"
            else -> throw IllegalArgumentException(
                "Unsupported MLC model: $modelId. This build only supports Qwen3."
            )
        }
    }
}

 codex/fix-audit-findings
private class MLCModelSession(
    private val engineInstance: MLCEngine
) : ModelSession {

    private var contextTokenCount = 0

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        val generationJob = launch(Dispatchers.IO) {
            var finishReason = FinishReason.STOP
            try {
                val responses = engineInstance.chat.completions.create(
                    messages = request.messages.map(ChatMessage::toMlcMessage),
                    max_tokens = request.maxTokens,
                    temperature = request.temperature,
                    top_p = request.topP,
                    stop = request.stopSequences.takeIf { it.isNotEmpty() },
                    stream = true,
                    stream_options = StreamOptions(include_usage = true)

class MLCModelSession(
codex/fix-audit-findings
    private val engine: MLCEngine,
=======
}

private class MLCModelSession(
    private val engineInstance: MLCEngine,
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
    private val config: ModelConfig
) : ModelSession {

    @Volatile
    private var isGenerating = false
    @Volatile
    private var activeResponses: ReceiveChannel<ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse>? = null

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        if (isGenerating) {
            close(IllegalStateException("Already generating"))
            return@callbackFlow
        }

        isGenerating = true

<<<<<<< HEAD
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
main
                )

                for (response in responses) {
                    response.usage?.let { usage ->
                        contextTokenCount = usage.total_tokens
                        trySend(
                            GenerationResponse.Finished(
                                finishReason = finishReason,
                                usage = TokenUsage(usage.prompt_tokens, usage.completion_tokens)
                            )
                        )
                        close()
                        return@launch
                    }

                    response.choices.forEach { choice ->
                        finishReason = choice.finish_reason?.toFinishReason() ?: finishReason
                        val text = choice.delta.content?.asText().orEmpty()
                        if (text.isNotEmpty()) {
                            trySend(GenerationResponse.Token(text))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "MLCModelSession: Generation failed")
                trySend(GenerationResponse.Error(e))
                close(e)
            }
        }

        awaitClose {
 codex/fix-audit-findings
            generationJob.cancel()

            if (isGenerating) {
                Timber.w("MLCModelSession: Generation cancelled by downstream collector")

        val generationJob = launch(Dispatchers.IO) {
            var finishReason = FinishReason.STOP
=======
        val generationJob = launch(Dispatchers.IO) {
            var promptTokens = 0
            var completionTokens = 0
            var sentFinished = false

>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
            try {
                val responses = engineInstance.chat.completions.create(
                    messages = request.messages.map { msg ->
                        ChatCompletionMessage(
                            role = when (msg.role) {
                                MessageRole.USER -> ChatCompletionRole.user
                                MessageRole.ASSISTANT -> ChatCompletionRole.assistant
                                MessageRole.SYSTEM -> ChatCompletionRole.system
                            },
                            content = msg.content
                        )
                    },
                    max_tokens = request.maxTokens,
                    temperature = request.temperature,
                    top_p = request.topP,
                    stop = request.stopSequences.ifEmpty { null },
                    stream = true,
                    stream_options = StreamOptions(include_usage = true)
                )
                activeResponses = responses

                for (response in responses) {
                    if (!isGenerating || !isActive) break

                    response.usage?.let { usage ->
                        promptTokens = usage.prompt_tokens
                        completionTokens = usage.completion_tokens
                    }

                    for (choice in response.choices) {
                        val deltaText = choice.delta.content?.asText()
                        if (!deltaText.isNullOrEmpty()) {
                            val sendResult = trySend(GenerationResponse.Token(deltaText))
                            if (sendResult.isFailure) {
                                break
                            }
                        }

                        choice.finish_reason?.let { reason ->
                            val finishReason = when (reason) {
                                "length" -> FinishReason.MAX_TOKENS
                                else -> FinishReason.STOP
                            }

                            if (!sentFinished) {
                                trySend(
                                    GenerationResponse.Finished(
                                        finishReason = finishReason,
                                        usage = TokenUsage(
                                            promptTokens = promptTokens,
                                            completionTokens = completionTokens
                                        )
                                    )
                                )
                                sentFinished = true
                            }
                        }
                    }
                }

                if (!sentFinished && isActive && isGenerating) {
                    trySend(
                        GenerationResponse.Finished(
                            finishReason = FinishReason.STOP,
                            usage = TokenUsage(
                                promptTokens = promptTokens,
                                completionTokens = completionTokens
                            )
                        )
                    )
                }

                close()
            } catch (_: CancellationException) {
                responsesCancel("Generation cancelled")
            } catch (e: Exception) {
                Timber.e(e, "MLCModelSession: Generation error")
                if (channel.isOpenForSend) {
                    trySend(GenerationResponse.Error(e))
                }
                close(e)
            } finally {
                activeResponses = null
                isGenerating = false
            }
        }

        awaitClose {
            responsesCancel("Flow collector closed")
            generationJob.cancel()
<<<<<<< HEAD
            if (isGenerating) {
                Timber.w("MLCModelSession: Generation cancelled by UI")
main
                isGenerating = false
                // MLC engine will finish current batch; reset clears state
            }
 main
        }
    }

    override fun resetContext() {
 codex/fix-audit-findings
        engineInstance.reset()
        contextTokenCount = 0

        Timber.i("MLCModelSession: Resetting KV Cache")
codex/fix-audit-findings
        engine.reset()
 main
    }

    override fun getContextLength(): Int = contextTokenCount

    override suspend fun close() {
codex/fix-audit-findings
        engineInstance.reset()

        Timber.i("MLCModelSession: Closing session")
        isGenerating = false

        engineInstance.reset()
        contextTokenCount = 0
 main
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

private fun ChatMessage.toMlcMessage(): ChatCompletionMessage = ChatCompletionMessage(
    role = when (role) {
        MessageRole.SYSTEM -> ChatCompletionRole.system
        MessageRole.USER -> ChatCompletionRole.user
        MessageRole.ASSISTANT -> ChatCompletionRole.assistant
    },
    content = content
)

private fun String.toFinishReason(): FinishReason = when (this.lowercase()) {
    "length" -> FinishReason.MAX_TOKENS
    "error" -> FinishReason.ERROR
    else -> FinishReason.STOP
}
=======
            isGenerating = false
            Timber.w("MLCModelSession: Generation cancelled by downstream collector")
        }
    }

    override fun resetContext() {
        responsesCancel("Context reset")
        runCatching { engineInstance.reset() }
            .onFailure { Timber.e(it, "MLCModelSession: Failed to reset engine session") }
    }

    override fun getContextLength(): Int = config.contextLength

    override suspend fun close() {
        responsesCancel("Session closed")
        resetContext()
    }

    private fun responsesCancel(reason: String) {
        runCatching {
            activeResponses?.cancel(CancellationException(reason))
        }.onFailure { cancelError ->
            Timber.w(cancelError, "MLCModelSession: Failed to cancel active response stream")
        }
    }
}
>>>>>>> a19f194 (Fix merge artifacts and normalize MLC engine wiring)
