package com.example.localllm.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * FakeInferenceEngine — Development / testing mock.
 *
 * Streams realistic token-by-token responses without any real LLM.
 * Replace with MLCInferenceEngine when the native library is ready.
 */
class FakeInferenceEngine @Inject constructor() : InferenceEngine {

    private var session: FakeModelSession? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        Timber.d("FakeEngine: loading model from $modelPath")
        delay(1200)
        session = FakeModelSession(config)
        Timber.d("FakeEngine: model loaded successfully")
        return Result.success(session!!)
    }

    override fun isModelLoaded(): Boolean = session != null

    override suspend fun unloadModel() {
        Timber.d("FakeEngine: unloading model")
        session?.close()
        session = null
        delay(300)
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "FakeInferenceEngine",
        version = "1.0.0-dev",
        backend = "Fake"
    )
}

class FakeModelSession(private val config: ModelConfig) : ModelSession {

    private var contextTokenCount = 0

    private val responses = listOf(
        "مرحبًا! أنا نموذج لغوي يعمل بالكامل على جهازك بدون إنترنت. " +
            "يمكنني مساعدتك في الإجابة على أسئلتك والتحدث معك بخصوصية تامة.",
        "هذا التطبيق يستخدم محرك MLC LLM للاستدلال المحلي على الجهاز. " +
            "جميع المعالجة تتم على جهازك مباشرةً دون إرسال أي بيانات لخوادم خارجية.",
        "يمكنني مساعدتك في تحليل النصوص، الإجابة على الأسئلة، كتابة المحتوى، " +
            "والتحدث بشكل طبيعي في أي موضوع تريده.",
        "الخصوصية التامة هي ميزتنا الأساسية. كل ما تكتبه يبقى على جهازك فقط. " +
            "لا يوجد اتصال بالإنترنت أثناء المحادثة.",
        "أداء النموذج يعتمد على مواصفات جهازك. الأجهزة ذات 8GB RAM وما فوق " +
            "تُشغّل النماذج الأكبر بأداء أفضل."
    )

    private val responseIndex = AtomicInteger(0)

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
        val idx = responseIndex.getAndIncrement()
        val responseText = responses[idx % responses.size]

        val words = responseText.split(" ")
        var totalTokens = 0

        delay(350)

        for ((index, word) in words.withIndex()) {
            val token = if (index < words.size - 1) "$word " else word
            emit(GenerationResponse.Token(token))
            totalTokens++
            contextTokenCount++
            delay((60..100L).random())
        }

        emit(
            GenerationResponse.Finished(
                finishReason = FinishReason.STOP,
                usage = TokenUsage(
                    promptTokens = request.messages.sumOf { it.content.length / 4 },
                    completionTokens = totalTokens
                )
            )
        )
    }

    override fun resetContext() {
        contextTokenCount = 0
        Timber.d("FakeModelSession: context reset")
    }

    override fun getContextLength(): Int = contextTokenCount

    override suspend fun close() {
        Timber.d("FakeModelSession: closing session")
    }
}
