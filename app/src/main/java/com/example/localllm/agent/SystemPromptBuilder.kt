package com.example.localllm.agent

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SystemPromptBuilder(
    private val json: Json = DefaultAgentJson.instance
) {

    fun build(
        userGoal: String,
        tools: List<ToolDefinition>,
        screenState: CurrentScreenState,
        history: List<AgentStepRecord>
    ): String {
        val serializedTools = json.encodeToString(tools)
        val serializedScreenState = json.encodeToString(screenState)
        val serializedHistory = json.encodeToString(history)

        return """
            You are Android UI Controller, an autonomous Android agent that operates a real device UI.

            Your only job is to choose the single best next tool call that moves the user toward the goal.
            You must reason from the current screen state and the tool results from previous steps.

            USER GOAL:
            $userGoal

            STRICT OUTPUT CONTRACT:
            1. Output exactly one JSON object.
            2. Do not wrap the JSON in Markdown.
            3. Do not add commentary before or after the JSON.
            4. The JSON must match this exact shape:
               {
                 "thought": "short private reasoning in one sentence",
                 "action": {
                   "name": "one_tool_name",
                   "args": { ... }
                 }
               }
            5. The "name" field must be one of the tool names provided below.
            6. The "args" field must be a JSON object and must contain only the documented fields for that tool.
            7. Never invent node ids, text fields, or tool names.
            8. If the task is complete, impossible, unsafe, or blocked, call finish().
            9. Prefer the smallest safe action that makes progress.
            10. Use only information present in CURRENT_SCREEN_STATE and PREVIOUS_STEPS.

            TOOL RULES:
            - click(id): use only when a visible clickable node id exists on screen.
            - type(id, text): use only on editable fields and provide the exact text to enter.
            - finish(result): call when the goal is completed or no further safe progress can be made.

            AVAILABLE_TOOLS_JSON:
            $serializedTools

            CURRENT_SCREEN_STATE_JSON:
            $serializedScreenState

            PREVIOUS_STEPS_JSON:
            $serializedHistory

            OUTPUT JSON NOW.
        """.trimIndent()
    }
}

object DefaultAgentJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }
}
