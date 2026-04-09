package ai.mlc.mlcllm

import ai.mlc.mlcllm.OpenAIProtocol.*
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel

class BackgroundWorker(private val task: () -> Unit) {

    fun start() {
        thread(start = true) {
            task()
        }
    }
}

class MLCEngine {

    private val jsonFFIEngine: JSONFFIEngine
    private val state: EngineState
    internal val chat: Chat
    private val threads = mutableListOf<BackgroundWorker>()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        jsonFFIEngine = JSONFFIEngine()
        state = EngineState(
            requestController = object : RequestController {
                override fun chatCompletion(requestJson: String, requestId: String) {
                    jsonFFIEngine.chatCompletion(requestJson, requestId)
                }

                override fun abort(requestId: String) {
                    jsonFFIEngine.abort(requestId)
                }
            },
            callbackScope = callbackScope
        )
        chat = Chat(jsonFFIEngine, state)

        jsonFFIEngine.initBackgroundEngine { result ->
            state.streamCallback(result)
        }

        val backgroundWorker = BackgroundWorker {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            jsonFFIEngine.runBackgroundLoop()
        }

        val backgroundStreamBackWorker = BackgroundWorker {
            jsonFFIEngine.runBackgroundStreamBackLoop()
        }

        threads.add(backgroundWorker)
        threads.add(backgroundStreamBackWorker)

        backgroundWorker.start()
        backgroundStreamBackWorker.start()
    }

    fun reload(modelPath: String, modelLib: String) {
        val engineConfig = """
            {
                "device": "vulkan",
                "model": "$modelPath",
                "model_lib": "system://$modelLib",
                "mode": "interactive"
            }
        """
        jsonFFIEngine.reload(engineConfig)
    }

    fun reset() {
        jsonFFIEngine.reset()
    }

    fun unload() {
        state.abortAll("Engine unloaded")
        jsonFFIEngine.unload()
    }
}

internal class Chat(
    private val jsonFFIEngine: JSONFFIEngine,
    private val state: EngineState
) {
    internal val completions = Completions(jsonFFIEngine, state)
}

internal class Completions(
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
