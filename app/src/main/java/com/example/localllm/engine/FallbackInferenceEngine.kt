package com.example.localllm.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart inference engine that tries MLC native first, then falls back to
 * the fake engine if native libraries are missing or fail to initialise.
 *
 * This lets the full UI/chat flow work end-to-end even when the packed
 * MLC runtime is missing or reload fails for the selected model.
 */
@Singleton
class FallbackInferenceEngine @Inject constructor(
    private val mlcEngine: MLCInferenceEngine,
    private val fakeEngine: FakeInferenceEngine
) : InferenceEngine {

    @Volatile
    private var activeEngine: InferenceEngine? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        val mlcResult = mlcEngine.loadModel(modelPath, config)
        if (mlcResult.isSuccess) {
            activeEngine = mlcEngine
            Timber.i("FallbackEngine: loaded via MLC native engine")
            return mlcResult
        }

        val mlcError = mlcResult.exceptionOrNull()
        Timber.w(mlcError, "FallbackEngine: MLC failed, falling back to fake engine")

        val fakeResult = fakeEngine.loadModel(modelPath, config)
        if (fakeResult.isSuccess) {
            activeEngine = fakeEngine
            Timber.i(
                "FallbackEngine: using fake engine (MLC error: %s)",
                mlcError?.message?.take(240)
            )
        } else {
            activeEngine = null
        }
        return fakeResult
    }

    override fun isModelLoaded(): Boolean =
        activeEngine?.isModelLoaded() ?: false

    override suspend fun unloadModel() {
        activeEngine?.unloadModel()
        activeEngine = null
    }

    override fun getEngineInfo(): EngineInfo {
        val delegate = activeEngine ?: return EngineInfo(
            name = "No engine loaded",
            version = "none",
            backend = "None"
        )

        val info = delegate.getEngineInfo()
        return if (delegate === fakeEngine) {
            info.copy(name = "Fake (MLC fallback)", version = "fallback")
        } else {
            info
        }
    }
}
