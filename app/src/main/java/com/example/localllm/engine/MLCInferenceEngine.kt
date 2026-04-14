package com.example.localllm.engine

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
import android.content.Context
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private var engine: MLCEngine? = null
    private var activeSession: MLCModelSession? = null
    private var activeModelId: String? = null

    override suspend fun loadModel(
        modelPath: String,
        config: ModelConfig
    ): Result<ModelSession> {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(modelPath)
                require(modelDir.exists()) { "Model path not found: $modelPath" }
                require(modelDir.isDirectory) {
                    "MLC requires a model directory, not a single file: $modelPath"
                }

                val modelId = modelDir.name
                val manifestRecord = findBundledMlcModel(context, modelId)
                    ?: error("Model \"$modelId\" is missing from mlc-app-config.json")

                Timber.i("MLCInferenceEngine: Loading model %s from %s", modelId, modelPath)

                val mlcEngine = engine ?: MLCEngine().also { engine = it }

                runCatching { activeSession?.close() }
                    .onFailure {
                        Timber.e(it, "MLCInferenceEngine: Failed to close active session before reload")
                    }
                activeSession = null

                runCatching { mlcEngine.unload() }
                    .onFailure { Timber.w(it, "MLCInferenceEngine: Engine unload before reload failed") }

                mlcEngine.reload(modelDir.absolutePath, manifestRecord.modelLib)

                val session = MLCModelSession(mlcEngine, config)
                activeSession = session
                activeModelId = modelId

                Result.success(session)
            } catch (e: Throwable) {
                Timber.e(e, "MLCInferenceEngine: Failed to load model")
                activeSession = null
                activeModelId = null
                Result.failure(if (e is Exception) e else RuntimeException("MLC load failed", e))
            }
        }
    }

    override fun isModelLoaded(): Boolean = activeSession != null && activeModelId != null

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            Timber.i("MLCInferenceEngine: Unloading model and releasing VRAM")

            runCatching { activeSession?.close() }
                .onFailure { Timber.e(it, "MLCInferenceEngine: Error while closing active session") }

            activeSession = null
            activeModelId = null

            runCatching { engine?.unload() }
                .onFailure { Timber.e(it, "MLCInferenceEngine: Error during model unload") }
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = "mlc4j-local",
        backend = "MLC"
    )
}

private class MLCModelSession(
    private val engineInstance: MLCEngine,
    private val config: ModelConfig
) : ModelSession {

    @Volatile
    private var isGenerating = false

    @Volatile
    private var activeResponses: ReceiveChannel<ChatCompletionStreamResponse>? = null

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = callbackFlow {
        if (isGenerating) {
            close(IllegalStateException("Already generating"))
            return@callbackFlow
        }

        isGenerating = true

        val generationJob = launch(Dispatchers.IO) {
            val responseAssembler = MlcResponseAssembler()
            var collectorOpen = true

            try {
                val responses = engineInstance.chat.completions.create(
                    messages = request.messages.map(ChatMessage::toMlcMessage),
                    max_tokens = request.maxTokens,
                    temperature = request.temperature,
                    top_p = request.topP,
                    stop = request.stopSequences.ifEmpty { null },
                    stream = true,
                    stream_options = StreamOptions(include_usage = true)
                )
                activeResponses = responses

                for (response in responses) {
                    if (!isGenerating || !isActive || !collectorOpen) break

                    for (event in responseAssembler.onResponse(response)) {
                        if (trySend(event).isFailure) {
                            collectorOpen = false
                            break
                        }
                    }
                }

                if (collectorOpen && isActive && isGenerating) {
                    responseAssembler.finishIfNeeded()?.let { trySend(it) }
                }

                close()
            } catch (_: CancellationException) {
                responsesCancel("Generation cancelled")
            } catch (e: Exception) {
                Timber.e(e, "MLCModelSession: Generation error")
                trySend(GenerationResponse.Error(e))
                close(e)
            } finally {
                activeResponses = null
                isGenerating = false
            }
        }

        awaitClose {
            responsesCancel("Flow collector closed")
            generationJob.cancel()
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

internal class MlcResponseAssembler {

    private var promptTokens = 0
    private var completionTokens = 0
    private var pendingFinishReason: FinishReason? = null
    private var sentFinished = false

    fun onResponse(response: ChatCompletionStreamResponse): List<GenerationResponse> {
        if (sentFinished) return emptyList()

        response.usage?.let { usage ->
            promptTokens = usage.prompt_tokens
            completionTokens = usage.completion_tokens
        }

        val events = buildList {
            response.choices.forEach { choice ->
                choice.delta.content?.asText()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { add(GenerationResponse.Token(it)) }

                choice.finish_reason?.let { pendingFinishReason = it.toFinishReason() }
            }

            if (response.usage != null) {
                add(buildFinished(pendingFinishReason ?: FinishReason.STOP))
            }
        }

        return events
    }

    fun finishIfNeeded(defaultReason: FinishReason = FinishReason.STOP): GenerationResponse.Finished? {
        if (sentFinished) return null
        return buildFinished(pendingFinishReason ?: defaultReason)
    }

    private fun buildFinished(reason: FinishReason): GenerationResponse.Finished {
        sentFinished = true
        return GenerationResponse.Finished(
            finishReason = reason,
            usage = TokenUsage(
                promptTokens = promptTokens,
                completionTokens = completionTokens
            )
        )
    }
}

private fun ChatMessage.toMlcMessage(): ChatCompletionMessage = ChatCompletionMessage(
    role = when (role) {
        MessageRole.SYSTEM -> ChatCompletionRole.system
        MessageRole.USER -> ChatCompletionRole.user
        MessageRole.ASSISTANT -> ChatCompletionRole.assistant
        MessageRole.TOOL -> throw IllegalArgumentException(
            "TOOL-role messages must not be forwarded to the LLM"
        )
    },
    content = content
)

private fun String.toFinishReason(): FinishReason = when (lowercase()) {
    "length" -> FinishReason.MAX_TOKENS
    "error" -> FinishReason.ERROR
    else -> FinishReason.STOP
}
