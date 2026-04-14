package ai.mlc.mlcllm

import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRequest
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface RequestController {
    fun chatCompletion(requestJson: String, requestId: String)
    fun abort(requestId: String)
}

internal data class RequestState(
    val request: ChatCompletionRequest,
    val continuation: Channel<ChatCompletionStreamResponse>,
    val sendMutex: Mutex = Mutex(),
    val completionHandled: AtomicBoolean = AtomicBoolean(false),
    val abortHandled: AtomicBoolean = AtomicBoolean(false)
)

internal class EngineState(
    private val requestController: RequestController,
    private val callbackScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val logger = Logger.getLogger(EngineState::class.java.name)
    private val requestStateMap = ConcurrentHashMap<String, RequestState>()
    private val requestJson = Json { encodeDefaults = true }
    private val responseJson = Json { ignoreUnknownKeys = true }

    suspend fun chatCompletion(
        request: ChatCompletionRequest
    ): ReceiveChannel<ChatCompletionStreamResponse> {
        val requestId = UUID.randomUUID().toString()
        val channel = Channel<ChatCompletionStreamResponse>(Channel.BUFFERED)
        val requestState = RequestState(request = request, continuation = channel)

        requestStateMap[requestId] = requestState

        channel.invokeOnClose { cause ->
            when {
                cause is CancellationException -> abortRequest(requestId, cause)
                cause != null -> finishRequest(requestId, cause)
                else -> requestStateMap.remove(requestId)
            }
        }

        return try {
            requestController.chatCompletion(
                requestJson.encodeToString(request),
                requestId
            )
            channel
        } catch (t: Throwable) {
            finishRequest(requestId, t)
            throw t
        }
    }

    fun streamCallback(result: String?) {
        try {
            val responses: List<ChatCompletionStreamResponse> =
                responseJson.decodeFromString(result ?: return)

            responses.forEach { response ->
                val requestState = requestStateMap[response.id] ?: return@forEach
                callbackScope.launch {
                    deliverResponse(response.id, requestState, response)
                }
            }
        } catch (e: Exception) {
            logger.severe("Kotlin JSON parsing error: $e, jsonsrc=$result")
        }
    }

    fun abortAll(reason: String) {
        val cancellation = CancellationException(reason)
        requestStateMap.keys.toList().forEach { requestId ->
            abortRequest(requestId, cancellation)
        }
    }

    internal fun activeRequestCount(): Int = requestStateMap.size

    private suspend fun deliverResponse(
        requestId: String,
        requestState: RequestState,
        response: ChatCompletionStreamResponse
    ) {
        requestState.sendMutex.withLock {
            if (requestState.completionHandled.get()) {
                return
            }

            val includeUsage = requestState.request.stream_options?.include_usage == true
            val shouldForward = response.usage == null || includeUsage

            if (shouldForward) {
                requestState.continuation.send(response)
            }

            val shouldClose = response.usage != null ||
                (!includeUsage && response.choices.any { it.finish_reason != null })

            if (shouldClose) {
                finishRequest(requestId, null)
            }
        }
    }

    private fun abortRequest(requestId: String, cause: CancellationException) {
        val requestState = requestStateMap[requestId] ?: return
        if (requestState.abortHandled.compareAndSet(false, true)) {
            runCatching { requestController.abort(requestId) }
                .onFailure { abortError ->
                    logger.warning("Failed to abort request $requestId: $abortError")
                }
        }
        finishRequest(requestId, cause)
    }

    private fun finishRequest(requestId: String, cause: Throwable?) {
        val requestState = requestStateMap.remove(requestId) ?: return
        if (!requestState.completionHandled.compareAndSet(false, true)) {
            return
        }
        if (cause == null) {
            requestState.continuation.close()
        } else {
            requestState.continuation.close(cause)
        }
    }
}
