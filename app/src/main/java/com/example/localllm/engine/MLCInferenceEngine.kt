package com.example.localllm.engine

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.*
import android.content.Context
import com.example.localllm.domain.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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

                // جدار الحماية: التأكد أن المسار هو مجلد وليس ملف .bin
                require(modelDir.exists()) { "Model path not found: $modelPath" }
                require(modelDir.isDirectory) { "MLC requires a model directory, not a single file: $modelPath" }

                val modelId = modelDir.name

                Timber.i("MLCInferenceEngine: Loading model %s from %s", modelId, modelPath)

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
        version = "1.0.0",
        backend = "MLC"
    )

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
            generationJob.cancel()
        }
    }

    override fun resetContext() {
        engineInstance.reset()
        contextTokenCount = 0
    }

    override fun getContextLength(): Int = contextTokenCount

    override suspend fun close() {
        engineInstance.reset()
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

private fun String.toFinishReason(): FinishReason = when (this.lowercase()) {
    "length" -> FinishReason.MAX_TOKENS
    "error" -> FinishReason.ERROR
    else -> FinishReason.STOP
}