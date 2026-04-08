package com.example.localllm.engine

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

class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private var engine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var activeModelId: String? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        return withContext(Dispatchers.IO) {
            try {
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
                Result.success(activeSession!!)
            } catch (e: Exception) {
                Timber.e(e, "MLCInferenceEngine: Failed to load model")
                Result.failure(e)
            }
        }
    }

    override fun isModelLoaded(): Boolean = activeSession != null && activeModelId != null

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            Timber.i("MLCInferenceEngine: Unloading model and releasing VRAM")
            activeSession?.close()
            activeSession = null
            activeModelId = null
            engine?.unload()
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = "mlc4j-local",
        backend = "MLC"
    )
}

class MLCModelSession(
    private val engineInstance: MLCEngine
) : ModelSession {

    private var isGenerating = false
    private var contextTokenCount = 0

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        if (isGenerating) {
            close(IllegalStateException("Already generating"))
            return@callbackFlow
        }
        isGenerating = true

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
                isGenerating = false
            }
        }
    }

    override fun resetContext() {
        Timber.i("MLCModelSession: Resetting KV Cache")
        engineInstance.reset()
        contextTokenCount = 0
    }

    override fun getContextLength(): Int = contextTokenCount

    override suspend fun close() {
        Timber.i("MLCModelSession: Closing session")
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
