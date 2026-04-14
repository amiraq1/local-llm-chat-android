package ai.mlc.mlcllm

import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessageContent
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionStreamResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses logprobs and nullable fingerprint from compatible response`() {
        val payload = """
            {
              "id": "req-1",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": null,
                  "delta": { "role": "assistant", "content": "Hi" },
                  "logprobs": {
                    "content": [
                      { "token": "Hi", "logprob": -0.1 }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<ChatCompletionStreamResponse>(payload)

        assertEquals("req-1", response.id)
        assertNull(response.system_fingerprint)
        assertNotNull(response.choices.single().logprobs)
    }

    @Test
    fun `extracts text from structured message parts`() {
        val payload = """
            [
              { "type": "text", "text": "Hello " },
              { "type": "text", "text": "world" }
            ]
        """.trimIndent()

        val content = json.decodeFromString<ChatCompletionMessageContent>(payload)

        assertEquals("Hello world", content.asText())
    }
}
