package com.example.localllm.engine

import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponseChoice
import ai.mlc.mlcllm.OpenAIProtocol.CompletionUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlcResponseAssemblerTest {

    @Test
    fun `split finish and usage emit finished only after the usage chunk arrives`() {
        val assembler = MlcResponseAssembler()

        val firstEvents = assembler.onResponse(
            response(
                content = "Hello",
                finishReason = "stop"
            )
        )

        assertEquals(1, firstEvents.size)
        assertEquals(
            GenerationResponse.Token("Hello"),
            firstEvents.single()
        )

        val secondEvents = assembler.onResponse(
            response(
                usage = CompletionUsage(
                    prompt_tokens = 12,
                    completion_tokens = 4,
                    total_tokens = 16
                )
            )
        )

        val finished = secondEvents.single() as GenerationResponse.Finished
        assertEquals(FinishReason.STOP, finished.finishReason)
        assertEquals(12, finished.usage.promptTokens)
        assertEquals(4, finished.usage.completionTokens)
    }

    @Test
    fun `missing usage falls back to pending finish reason when the stream closes`() {
        val assembler = MlcResponseAssembler()

        val events = assembler.onResponse(response(finishReason = "length"))
        assertTrue(events.isEmpty())

        val finished = assembler.finishIfNeeded()
        assertEquals(FinishReason.MAX_TOKENS, finished?.finishReason)
        assertEquals(0, finished?.usage?.completionTokens)
    }

    private fun response(
        content: String? = null,
        finishReason: String? = null,
        usage: CompletionUsage? = null
    ) = ChatCompletionStreamResponse(
        id = "request-1",
        choices = if (content != null || finishReason != null) {
            listOf(
                ChatCompletionStreamResponseChoice(
                    finish_reason = finishReason,
                    index = 0,
                    delta = ChatCompletionMessage(
                        role = ChatCompletionRole.assistant,
                        content = content ?: ""
                    )
                )
            )
        } else {
            emptyList()
        },
        usage = usage
    )
}
