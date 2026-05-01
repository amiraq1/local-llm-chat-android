package com.example.localllm.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart inference engine that tries MLC native first, then falls back to
 * the fake engine if native libraries are missing, fail to initialise, or
 * crash at generation time (e.g. OpenCL errors on unsupported GPUs).
 *
 * This lets the full UI/chat flow work end-to-end even when the packed
 * MLC runtime is missing or reload/generate fails for the selected model.
 */
@Singleton
class FallbackInferenceEngine @Inject constructor(
    @MlcEngine private val mlcEngine: InferenceEngine,
    @FakeEngine private val fakeEngine: InferenceEngine
) : InferenceEngine {

    @Volatile
    private var activeEngine: InferenceEngine? = null

    /** Tracks whether we already fell back to fake at generation time. */
    @Volatile
    private var mlcGenerationFailed = false

    private var lastModelPath: String? = null
    private var lastConfig: ModelConfig? = null

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
        lastModelPath = modelPath
        lastConfig = config
        mlcGenerationFailed = false

        val mlcResult = mlcEngine.loadModel(modelPath, config)
        if (mlcResult.isSuccess) {
            activeEngine = mlcEngine
            Timber.i("FallbackEngine: loaded via MLC native engine")
            return Result.success(FallbackModelSession(mlcResult.getOrThrow()))
        }

        val mlcError = mlcResult.exceptionOrNull()

        // Never silently fall back on cancellation — surface it to the caller.
        if (mlcError is CancellationException) {
            throw mlcError
        }

        Timber.w(mlcError, "FallbackEngine: MLC failed, falling back to fake engine")

        return loadFakeEngine(modelPath, config, mlcError)
    }

    override fun isModelLoaded(): Boolean =
        activeEngine?.isModelLoaded() ?: false

    override suspend fun unloadModel() {
        activeEngine?.unloadModel()
        activeEngine = null
        mlcGenerationFailed = false
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

    private suspend fun loadFakeEngine(
        modelPath: String,
        config: ModelConfig,
        mlcError: Throwable?
    ): Result<ModelSession> {
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

    /**
     * A wrapper session that intercepts [generate] errors from the MLC session
     * and transparently falls back to the Fake engine when the native runtime
     * crashes at generation time (e.g. OpenCL CL_INVALID_COMMAND_QUEUE on Mali).
     */
    private inner class FallbackModelSession(
        private var delegate: ModelSession
    ) : ModelSession {

        override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
            if (mlcGenerationFailed) {
                // We already know MLC fails — go straight to fake
                emitAll(delegate.generate(request))
                return@flow
            }

            emitAll(
                delegate.generate(request)
                    .catch { e ->
                        // CRITICAL: Never swallow cancellation. Re-throw so coroutine machinery
                        // can propagate it correctly (downstream collector cancelled, scope cancelled, etc.).
                        if (e is CancellationException) throw e

                        // Do not silently fall back on programmer errors that indicate the caller
                        // misused the API — they should be visible during development.
                        if (e is IllegalStateException || e is IllegalArgumentException) {
                            emit(GenerationResponse.Error(e))
                            return@catch
                        }

                        Timber.e(
                            e,
                            "FallbackEngine: MLC generation crashed, " +
                                "falling back to fake engine mid-stream"
                        )
                        mlcGenerationFailed = true

                        // Try to unload MLC to free resources
                        runCatching { mlcEngine.unloadModel() }
                            .onFailure { Timber.w(it, "FallbackEngine: MLC unload after crash failed") }

                        // Load fake engine and get a new session
                        val path = lastModelPath ?: ""
                        val config = lastConfig ?: ModelConfig()
                        val fakeResult = loadFakeEngine(path, config, e)
                        if (fakeResult.isFailure) {
                            emit(GenerationResponse.Error(
                                fakeResult.exceptionOrNull()
                                    ?: RuntimeException("Fake engine also failed to load")
                            ))
                            return@catch
                        }

                        val fakeSession = fakeResult.getOrThrow()
                        delegate = fakeSession

                        // Emit a notice token so the user knows what happened
                        emit(GenerationResponse.Token(
                            "⚠ تعذّر استخدام المحرك الأصلي — تم التحويل للوضع التجريبي.\n\n"
                        ))

                        // Now replay the generation via fake
                        emitAll(fakeSession.generate(request))
                    }
            )
        }

        override fun resetContext() = delegate.resetContext()

        override fun getContextLength(): Int = delegate.getContextLength()

        override suspend fun close() = delegate.close()
    }
}
