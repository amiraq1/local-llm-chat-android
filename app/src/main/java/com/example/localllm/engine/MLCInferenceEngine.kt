package com.example.localllm.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Compile-safe scaffold for the future native MLC integration.
 *
 * The app currently binds [FakeInferenceEngine] by default, so this class stays
 * lightweight and safe until the native runtime and model manifests are fully
 * wired in.
 */
class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private var activeSession: MLCModelSession? = null
    private var activeModelPath: String? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val modelDir = File(modelPath)
                require(modelDir.exists()) { "Model path not found: $modelPath" }
                require(modelDir.isDirectory) { "MLC requires a model directory: $modelPath" }

                Timber.w(
                    "MLCInferenceEngine is scaffold-only in this build; loading placeholder session for %s",
                    modelDir.name
                )

                MLCModelSession(config).also {
                    activeSession = it
                    activeModelPath = modelDir.absolutePath
                }
            }
        }

    override fun isModelLoaded(): Boolean =
        activeSession != null && activeModelPath != null

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            activeSession?.close()
            activeSession = null
            activeModelPath = null
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = "scaffold",
        backend = "MLC"
    )
}

private class MLCModelSession(
    private val config: ModelConfig
) : ModelSession {

    private var contextTokenCount = 0

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
        val placeholder =
            "تكامل MLC موجود حاليًا كهيكل جاهز للبناء فقط. عند تفعيل الربط " +
                "الفعلي سيتم استبدال هذه الاستجابة بالتوليد المحلي الحقيقي."

        delay(200)

        placeholder.split(" ").forEach { word ->
            emit(GenerationResponse.Token("$word "))
            contextTokenCount++
            delay(35)
        }

        emit(
            GenerationResponse.Finished(
                finishReason = FinishReason.STOP,
                usage = TokenUsage(
                    promptTokens = request.messages.sumOf { it.content.length / 4 },
                    completionTokens = placeholder.split(" ").size
                )
            )
        )
    }

    override fun resetContext() {
        contextTokenCount = 0
        Timber.d("MLCModelSession: context reset")
    }

    override fun getContextLength(): Int = contextTokenCount.coerceAtMost(config.contextLength)

    override suspend fun close() {
        contextTokenCount = 0
        Timber.d("MLCModelSession: closing scaffold session")
    }
}
