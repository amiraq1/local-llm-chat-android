package ai.mlc.mlcllm

import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRequest
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponseChoice
import ai.mlc.mlcllm.OpenAIProtocol.CompletionUsage
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineStateTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `cancelled request aborts once and cleans up`() = runTest {
        val controller = FakeRequestController()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val state = EngineState(controller, scope)

        val channel = state.chatCompletion(request())
        val requestId = controller.requestIds.single()

        assertEquals(1, state.activeRequestCount())

        channel.cancel(CancellationException("test cancellation"))
        advanceUntilIdle()

        assertEquals(listOf(requestId), controller.abortedIds)
        assertEquals(0, state.activeRequestCount())
    }

    @Test
    fun `final usage chunk is forwarded and closes stream`() = runTest {
        val controller = FakeRequestController()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val state = EngineState(controller, scope)

        val channel = state.chatCompletion(
            request(streamOptions = StreamOptions(include_usage = true))
        )
        val requestId = controller.requestIds.single()

        state.streamCallback(
            json.encodeToString(
                listOf(
                    response(
                        requestId = requestId,
                        finishReason = "stop",
                        usage = CompletionUsage(
                            prompt_tokens = 12,
                            completion_tokens = 4,
                            total_tokens = 16
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        val first = channel.receiveCatching().getOrThrow()
        assertEquals(4, first.usage?.completion_tokens)
        assertTrue(channel.receiveCatching().isClosed)
        assertEquals(0, state.activeRequestCount())
    }

    @Test
    fun `include usage stream stays open until the trailing usage chunk arrives`() = runTest {
        val controller = FakeRequestController()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val state = EngineState(controller, scope)

        val channel = state.chatCompletion(
            request(streamOptions = StreamOptions(include_usage = true))
        )
        val requestId = controller.requestIds.single()

        state.streamCallback(
            json.encodeToString(
                listOf(
                    response(
                        requestId = requestId,
                        finishReason = "stop"
                    )
                )
            )
        )
        advanceUntilIdle()

        val first = channel.receiveCatching().getOrThrow()
        assertEquals("stop", first.choices.single().finish_reason)
        assertEquals(1, state.activeRequestCount())

        state.streamCallback(
            json.encodeToString(
                listOf(
                    response(
                        requestId = requestId,
                        usage = CompletionUsage(
                            prompt_tokens = 12,
                            completion_tokens = 4,
                            total_tokens = 16
                        ),
                        includeChoice = false
                    )
                )
            )
        )
        advanceUntilIdle()

        val second = channel.receiveCatching().getOrThrow()
        assertEquals(4, second.usage?.completion_tokens)
        assertTrue(channel.receiveCatching().isClosed)
        assertEquals(0, state.activeRequestCount())
    }

    @Test
    fun `finish reason without usage closes non usage streams`() = runTest {
        val controller = FakeRequestController()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val state = EngineState(controller, scope)

        val channel = state.chatCompletion(request())
        val requestId = controller.requestIds.single()

        state.streamCallback(
            json.encodeToString(
                listOf(
                    response(
                        requestId = requestId,
                        finishReason = "stop"
                    )
                )
            )
        )
        advanceUntilIdle()

        val first = channel.receiveCatching().getOrThrow()
        assertEquals(requestId, first.id)
        assertTrue(channel.receiveCatching().isClosed)
        assertEquals(0, state.activeRequestCount())
    }

    private fun request(
        streamOptions: StreamOptions? = null
    ) = ChatCompletionRequest(
        messages = listOf(
            ChatCompletionMessage(
                role = ChatCompletionRole.user,
                content = "hello"
            )
        ),
        stream = true,
        stream_options = streamOptions
    )

    private fun response(
        requestId: String,
        finishReason: String? = null,
        usage: CompletionUsage? = null,
        includeChoice: Boolean = true
    ) = ChatCompletionStreamResponse(
        id = requestId,
        choices = if (includeChoice) {
            listOf(
                ChatCompletionStreamResponseChoice(
                    finish_reason = finishReason,
                    index = 0,
                    delta = ChatCompletionMessage(
                        role = ChatCompletionRole.assistant,
                        content = "hi"
                    )
                )
            )
        } else {
            emptyList()
        },
        usage = usage
    )

    private class FakeRequestController : RequestController {
        val requestIds = mutableListOf<String>()
        val abortedIds = mutableListOf<String>()

        override fun chatCompletion(requestJson: String, requestId: String) {
            requestIds += requestId
        }

        override fun abort(requestId: String) {
            abortedIds += requestId
        }
    }
}
