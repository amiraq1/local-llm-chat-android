package com.example.localllm.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class GemmaInferenceEngine @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val initMutex = Mutex()
    private val generationMutex = Mutex()

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var activeConfig: GemmaModelConfig? = null

    suspend fun initialize(config: GemmaModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (llmInference != null && activeConfig == config) {
                return@withLock Result.success(Unit)
            }

            runCatching {
                validateConfig(config)
                releaseLocked()

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(config.modelPath)
                    .setMaxTokens(config.maxTokens)
                    .setMaxTopK(config.topK)
                    .build()

                llmInference = LlmInference.createFromOptions(appContext, options)
                activeConfig = config
                Timber.i("GemmaInferenceEngine initialized with model at %s", config.modelPath)
            }.recoverCatching { error ->
                throw mapThrowable(error, "load")
            }
        }
    }

    fun isInitialized(): Boolean = llmInference != null

    fun currentConfig(): GemmaModelConfig? = activeConfig

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val engine = llmInference
            ?: return@withContext Result.failure(
                GemmaNotInitializedException("Initialize GemmaInferenceEngine before inference.")
            )

        generationMutex.withLock {
            runCatching {
                createSession(engine).useSession { session ->
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                }
            }.recoverCatching { error ->
                throw mapThrowable(error, "inference")
            }
        }
    }

    fun streamGenerate(prompt: String): Flow<String> = callbackFlow {
        val engine = llmInference
        if (engine == null) {
            close(GemmaNotInitializedException("Initialize GemmaInferenceEngine before streaming."))
            return@callbackFlow
        }

        val activeStream = ActiveStream(
            emitChunk = { chunk ->
                if (chunk.isNotEmpty()) {
                    trySend(chunk)
                }
            },
            finish = { close() },
            signalFailure = { throwable: Throwable -> close(throwable) }
        )
        val activeSession = AtomicReference<LlmInferenceSession?>(null)

        val worker = launch(Dispatchers.IO) {
            generationMutex.withLock {
                var session: LlmInferenceSession? = null

                try {
                    session = createSession(engine)
                    activeSession.set(session)
                    session.addQueryChunk(prompt)
                    val future = session.generateResponseAsync(
                        ProgressListener<String> { partialResult: String?, done: Boolean ->
                            activeStream.onPartialResult(partialResult.orEmpty(), done)
                        }
                    )
                    future.get()
                    activeStream.awaitCompletion()
                } catch (error: Throwable) {
                    activeStream.fail(mapThrowable(unwrapAsyncError(error), "inference"))
                } finally {
                    activeSession.set(null)
                    runCatching { session?.close() }
                        .onFailure { error ->
                            Timber.w(error, "Failed to close Gemma MediaPipe session")
                        }
                }
            }
        }

        awaitClose {
            activeStream.markCollectorClosed()
            runCatching { activeSession.get()?.cancelGenerateResponseAsync() }
                .onFailure { error ->
                    Timber.w(error, "Failed to cancel active Gemma async generation")
                }
            if (!worker.isCompleted) {
                worker.invokeOnCompletion { cause ->
                    cause?.let { Timber.w(it, "Gemma streaming coroutine completed with an error") }
                }
            }
        }
    }

    suspend fun countTokens(text: String): Result<Int> = withContext(Dispatchers.IO) {
        val engine = llmInference
            ?: return@withContext Result.failure(
                GemmaNotInitializedException("Initialize GemmaInferenceEngine before counting tokens.")
            )

        generationMutex.withLock {
            runCatching {
                engine.sizeInTokens(text)
            }.recoverCatching { error ->
                throw mapThrowable(error, "inference")
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            releaseLocked()
        }
    }

    private fun validateConfig(config: GemmaModelConfig) {
        require(config.maxTokens > 0) { "maxTokens must be greater than 0." }
        require(config.topK > 0) { "topK must be greater than 0." }
        require(config.temperature >= 0f) { "temperature must be non-negative." }
        require(config.topP in 0f..1f) { "topP must be between 0 and 1." }

        val modelFile = File(config.modelPath)
        require(modelFile.exists()) { "Model file does not exist at ${config.modelPath}" }
        require(modelFile.canRead()) { "Model file is not readable at ${config.modelPath}" }
    }

    private fun releaseLocked() {
        runCatching {
            llmInference?.close()
        }.onFailure { error ->
            Timber.w(error, "Failed to close MediaPipe LlmInference cleanly")
        }

        llmInference = null
        activeConfig = null
    }

    private fun createSession(engine: LlmInference): LlmInferenceSession {
        val config = activeConfig
            ?: throw GemmaNotInitializedException("Gemma configuration is missing.")

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(config.topK)
            .setTemperature(config.temperature)
            .setTopP(config.topP)
            .setRandomSeed(config.randomSeed)
            .build()

        // Each request gets a fresh session to avoid stale context growth on edge devices.
        return LlmInferenceSession.createFromOptions(engine, sessionOptions)
    }

    private fun mapThrowable(
        error: Throwable,
        stage: String
    ): GemmaEngineException {
        if (error is GemmaEngineException) return error

        val message = error.message.orEmpty().lowercase()

        return when {
            error is OutOfMemoryError ||
                "outofmemory" in message ||
                "memory" in message ||
                "allocate" in message ||
                "arena" in message -> {
                GemmaMemoryException(
                    message = "Gemma $stage failed because the device could not allocate enough memory.",
                    cause = error
                )
            }

            stage == "load" ||
                "model" in message ||
                "mmap" in message ||
                "file" in message ||
                "open" in message ||
                "load" in message -> {
                GemmaModelLoadException(
                    message = "Gemma model loading failed. Verify the MediaPipe bundle path and model compatibility.",
                    cause = error
                )
            }

            else -> GemmaEngineException(
                message = "Gemma $stage failed: ${error.message ?: "unknown MediaPipe error"}",
                cause = error
            )
        }
    }
}

data class GemmaModelConfig(
    val modelPath: String,
    val maxTokens: Int = 512,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.8f,
    val randomSeed: Int = 0
)

open class GemmaEngineException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class GemmaModelLoadException(
    message: String,
    cause: Throwable? = null
) : GemmaEngineException(message, cause)

class GemmaMemoryException(
    message: String,
    cause: Throwable? = null
) : GemmaEngineException(message, cause)

class GemmaNotInitializedException(
    message: String
) : GemmaEngineException(message)

private class ActiveStream(
    private val emitChunk: (String) -> Unit,
    private val finish: () -> Unit,
    private val signalFailure: (Throwable) -> Unit
) {

    private val completion = CompletableDeferred<Unit>()
    private var lastPartialResponse = ""

    @Volatile
    private var collectorClosed = false

    fun onPartialResult(partialResult: String, done: Boolean) {
        val delta = if (partialResult.startsWith(lastPartialResponse)) {
            partialResult.substring(lastPartialResponse.length)
        } else {
            partialResult
        }

        lastPartialResponse = partialResult

        if (!collectorClosed && delta.isNotEmpty()) {
            emitChunk(delta)
        }

        if (done) {
            if (!collectorClosed) finish()
            completion.complete(Unit)
        }
    }

    fun fail(error: Throwable) {
        if (!collectorClosed) signalFailure.invoke(error)
        completion.complete(Unit)
    }

    fun markCollectorClosed() {
        collectorClosed = true
    }

    suspend fun awaitCompletion() {
        completion.await()
    }
}

private inline fun <T> LlmInferenceSession.useSession(
    block: (LlmInferenceSession) -> T
): T {
    return try {
        block(this)
    } finally {
        close()
    }
}

private fun unwrapAsyncError(error: Throwable): Throwable {
    return when (error) {
        is ExecutionException -> error.cause ?: error
        else -> error
    }
}
