package com.example.localllm.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Verifies that [FallbackInferenceEngine] does **not** swallow
 * [CancellationException] in either:
 *  - the `loadModel` path (MLC throws cancellation while loading), or
 *  - the streaming `generate` path (MLC throws cancellation mid-stream).
 *
 * Cancellation must always propagate to the caller so coroutine machinery
 * can unwind correctly. The fake engine must NOT be activated as a fallback.
 */
class FallbackEngineCancellationTest {

    // ── loadModel path ────────────────────────────────────────────────────────

    @Test(expected = CancellationException::class)
    fun `loadModel re-throws CancellationException without falling back to fake`() = runTest {
        val mlc = StubInferenceEngine.failingLoadWith(CancellationException("user cancelled"))
        val fake = StubInferenceEngine.alwaysSucceeds()
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        engine.loadModel("/dev/null", ModelConfig())

        // Should not reach here — exception expected.
    }

    @Test
    fun `loadModel falls back to fake on a non-cancellation error`() = runTest {
        val mlc = StubInferenceEngine.failingLoadWith(RuntimeException("native crash"))
        val fake = StubInferenceEngine.alwaysSucceeds()
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        val result = engine.loadModel("/dev/null", ModelConfig())

        assertThat(result.isSuccess).isTrue()
        assertThat(fake.loadCalls).isEqualTo(1)
    }

    // ── generate() streaming path ─────────────────────────────────────────────

    @Test(expected = CancellationException::class)
    fun `generate re-throws CancellationException mid-stream without falling back`() = runTest {
        val cancelling = object : ModelSession {
            override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
                emit(GenerationResponse.Token("partial "))
                throw CancellationException("scope cancelled")
            }
            override fun resetContext() = Unit
            override fun getContextLength(): Int = 0
            override suspend fun close() = Unit
        }

        val mlc = StubInferenceEngine.successfulLoadWith(cancelling)
        val fake = StubInferenceEngine.alwaysSucceeds()
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        val session = engine.loadModel("/dev/null", ModelConfig()).getOrThrow()
        session.generate(emptyRequest()).toList()
    }

    @Test
    fun `generate falls back to fake on a non-cancellation error`() = runTest {
        val crashing = object : ModelSession {
            override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
                emit(GenerationResponse.Token("partial "))
                throw RuntimeException("OpenCL CL_INVALID_COMMAND_QUEUE")
            }
            override fun resetContext() = Unit
            override fun getContextLength(): Int = 0
            override suspend fun close() = Unit
        }

        val mlc = StubInferenceEngine.successfulLoadWith(crashing)
        val fake = StubInferenceEngine.alwaysSucceeds(text = "hello from fake")
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        val session = engine.loadModel("/dev/null", ModelConfig()).getOrThrow()
        val emissions = session.generate(emptyRequest()).toList()

        // We get the partial MLC token, the warning notice, then the fake stream.
        assertThat(emissions).isNotEmpty()
        val joinedText = emissions.filterIsInstance<GenerationResponse.Token>()
            .joinToString("") { it.text }
        assertThat(joinedText).contains("partial ")
        assertThat(joinedText).contains("hello from fake")
        assertThat(fake.loadCalls).isAtLeast(1)
    }

    @Test
    fun `generate emits Error and does not fall back on IllegalStateException`() = runTest {
        val crashing = object : ModelSession {
            override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
                throw IllegalStateException("session not initialised")
            }
            override fun resetContext() = Unit
            override fun getContextLength(): Int = 0
            override suspend fun close() = Unit
        }

        val mlc = StubInferenceEngine.successfulLoadWith(crashing)
        val fake = StubInferenceEngine.alwaysSucceeds()
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        val session = engine.loadModel("/dev/null", ModelConfig()).getOrThrow()
        val emissions = session.generate(emptyRequest()).toList()

        val errors = emissions.filterIsInstance<GenerationResponse.Error>()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].throwable).isInstanceOf(IllegalStateException::class.java)
        // Fake engine must NOT have been recruited for a programmer error.
        assertThat(fake.loadCalls).isEqualTo(0)
    }

    @Test
    fun `generate emits Error and does not fall back on IllegalArgumentException`() = runTest {
        // Parity with IllegalStateException: caller-misuse exceptions surface
        // as a real error so they're visible during development, instead of
        // silently routing the user to the fake engine.
        val crashing = object : ModelSession {
            override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
                throw IllegalArgumentException("invalid temperature value")
            }
            override fun resetContext() = Unit
            override fun getContextLength(): Int = 0
            override suspend fun close() = Unit
        }

        val mlc = StubInferenceEngine.successfulLoadWith(crashing)
        val fake = StubInferenceEngine.alwaysSucceeds()
        val engine = FallbackInferenceEngine(mlcEngine = mlc, fakeEngine = fake)

        val session = engine.loadModel("/dev/null", ModelConfig()).getOrThrow()
        val emissions = session.generate(emptyRequest()).toList()

        val errors = emissions.filterIsInstance<GenerationResponse.Error>()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].throwable).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(fake.loadCalls).isEqualTo(0)
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    private fun emptyRequest() = GenerationRequest(messages = emptyList())

    /**
     * A minimal stub that satisfies [InferenceEngine]. The fallback engine accepts
     * its delegates by interface, so no unsafe casting is needed here.
     */
    private class StubInferenceEngine private constructor(
        private val behaviour: Behaviour,
        private val text: String = "stub output"
    ) : InferenceEngine {

        var loadCalls: Int = 0

        sealed class Behaviour {
            object AlwaysSucceeds : Behaviour()
            data class FailingLoad(val error: Throwable) : Behaviour()
            data class SuccessfulWith(val session: ModelSession) : Behaviour()
        }

        override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
            loadCalls++
            return when (val b = behaviour) {
                Behaviour.AlwaysSucceeds -> Result.success(simpleSession(text))
                is Behaviour.FailingLoad -> Result.failure(b.error)
                is Behaviour.SuccessfulWith -> Result.success(b.session)
            }
        }

        override fun isModelLoaded() = true
        override suspend fun unloadModel() = Unit
        override fun getEngineInfo() = EngineInfo("stub", "0", "Stub")

        companion object {
            fun alwaysSucceeds(text: String = "stub output") =
                StubInferenceEngine(Behaviour.AlwaysSucceeds, text)
            fun failingLoadWith(error: Throwable) =
                StubInferenceEngine(Behaviour.FailingLoad(error))
            fun successfulLoadWith(session: ModelSession) =
                StubInferenceEngine(Behaviour.SuccessfulWith(session))

            private fun simpleSession(text: String) = object : ModelSession {
                override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
                    emit(GenerationResponse.Token(text))
                    emit(
                        GenerationResponse.Finished(
                            finishReason = FinishReason.STOP,
                            usage = TokenUsage(promptTokens = 0, completionTokens = 1)
                        )
                    )
                }
                override fun resetContext() = Unit
                override fun getContextLength(): Int = 0
                override suspend fun close() = Unit
            }
        }
    }
}
