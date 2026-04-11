package ai.mlc.mlcllm

import ai.mlc.mlcllm.OpenAIProtocol.*
import org.apache.tvm.Device
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.logging.Logger

class BackgroundWorker(
    private val task: () -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {

    fun start() {
        thread(start = true) {
            runCatching { task() }
                .onFailure(onError)
        }
    }
}

class MLCEngine {
    private val logger = Logger.getLogger(MLCEngine::class.java.name)

    private val jsonFFIEngine: JSONFFIEngine
    private val state: EngineState
    val chat: Chat
    private val threads = mutableListOf<BackgroundWorker>()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = detectBackend()

    @Volatile
    private var backgroundFailure: Throwable? = null

    init {
        jsonFFIEngine = JSONFFIEngine()
        state = EngineState(
            requestController = object : RequestController {
                override fun chatCompletion(requestJson: String, requestId: String) {
                    ensureHealthy()
                    jsonFFIEngine.chatCompletion(requestJson, requestId)
                }

                override fun abort(requestId: String) {
                    jsonFFIEngine.abort(requestId)
                }
            },
            callbackScope = callbackScope
        )
        chat = Chat(jsonFFIEngine, state)

        jsonFFIEngine.initBackgroundEngine(backend.device.deviceType, backend.device.deviceId) { result ->
            state.streamCallback(result)
        }

        val backgroundWorker = BackgroundWorker(
            task = {
                Thread.currentThread().priority = Thread.MAX_PRIORITY
                jsonFFIEngine.runBackgroundLoop()
            },
            onError = { error ->
                handleBackgroundFailure("runBackgroundLoop", error)
            }
        )

        val backgroundStreamBackWorker = BackgroundWorker(
            task = {
                jsonFFIEngine.runBackgroundStreamBackLoop()
            },
            onError = { error ->
                handleBackgroundFailure("runBackgroundStreamBackLoop", error)
            }
        )

        threads.add(backgroundWorker)
        threads.add(backgroundStreamBackWorker)

        backgroundWorker.start()
        backgroundStreamBackWorker.start()
    }

    fun reload(modelPath: String, modelLib: String) {
        ensureHealthy()
        val engineConfig = """
            {
                "device": "${backend.configName}",
                "model": "$modelPath",
                "model_lib": "system://$modelLib",
                "mode": "interactive"
            }
        """
        jsonFFIEngine.reload(engineConfig)
    }

    fun reset() {
        ensureHealthy()
        jsonFFIEngine.reset()
    }

    fun unload() {
        state.abortAll("Engine unloaded")
        jsonFFIEngine.unload()
    }

    private fun handleBackgroundFailure(workerName: String, error: Throwable) {
        backgroundFailure = error
        logger.severe("MLCEngine background worker $workerName failed: $error")
        state.abortAll("MLC background worker failed: ${error.message}")
    }

    private fun ensureHealthy() {
        backgroundFailure?.let { error ->
            throw IllegalStateException("MLC backend failed on ${backend.configName}", error)
        }
    }

    private data class EngineBackend(
        val configName: String,
        val device: Device
    )

    private companion object {
        fun detectBackend(): EngineBackend {
            val candidates = listOf(
                EngineBackend("vulkan", Device.vulkan()),
                EngineBackend("opencl", Device.opencl()),
                EngineBackend("cpu", Device.cpu())
            )

            return candidates.firstOrNull { backend ->
                runCatching { backend.device.exist() }.getOrDefault(false)
            } ?: candidates.last()
        }
    }
}

class Chat internal constructor(
    private val jsonFFIEngine: JSONFFIEngine,
    private val state: EngineState
) {
    val completions = Completions(jsonFFIEngine, state)
}

class Completions internal constructor(
    private val jsonFFIEngine: JSONFFIEngine,
    private val state: EngineState
) {

    suspend fun create(request: ChatCompletionRequest): ReceiveChannel<ChatCompletionStreamResponse> {
        return state.chatCompletion(request)
    }

    suspend fun create(
        messages: List<ChatCompletionMessage>,
        model: String? = null,
        frequency_penalty: Float? = null,
        presence_penalty: Float? = null,
        logprobs: Boolean = false,
        top_logprobs: Int = 0,
        logit_bias: Map<Int, Float>? = null,
        max_tokens: Int? = null,
        n: Int = 1,
        seed: Int? = null,
        stop: List<String>? = null,
        stream: Boolean = true,
        stream_options: StreamOptions? = null,
        temperature: Float? = null,
        top_p: Float? = null,
        tools: List<ChatTool>? = null,
        user: String? = null,
        response_format: ResponseFormat? = null
    ): ReceiveChannel<ChatCompletionStreamResponse> {
        if (!stream) {
            throw IllegalArgumentException("Only stream=true is supported in MLCKotlin")
        }

        val request = ChatCompletionRequest(
            messages = messages,
            model = model,
            frequency_penalty = frequency_penalty,
            presence_penalty = presence_penalty,
            logprobs = logprobs,
            top_logprobs = top_logprobs,
            logit_bias = logit_bias,
            max_tokens = max_tokens,
            n = n,
            seed = seed,
            stop = stop,
            stream = stream,
            stream_options = stream_options,
            temperature = temperature,
            top_p = top_p,
            tools = tools,
            user = user,
            response_format = response_format
        )
        return create(request)
    }
}
