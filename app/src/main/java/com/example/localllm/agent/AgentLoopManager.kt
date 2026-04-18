package com.example.localllm.agent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class AgentLoopManager(
    private val llmEngine: AgentLlmEngine,
    private val screenStateProvider: ScreenStateProvider,
    private val actionExecutor: ActionExecutor,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val json: Json = DefaultAgentJson.instance,
    private val logicDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    sealed class LoopUpdate {
        data class AnalyzingScreen(val step: Int) : LoopUpdate()
        data class Thinking(val step: Int) : LoopUpdate()
        data class ExecutingAction(
            val step: Int,
            val action: ToolCall,
            val description: String
        ) : LoopUpdate()
        data class Finished(val result: AgentRunResult) : LoopUpdate()
    }

    suspend fun run(
        userGoal: String,
        tools: List<ToolDefinition> = defaultTools(),
        maxSteps: Int = 10,
        settleDelayMs: Long = 750L,
        onUpdate: suspend (LoopUpdate) -> Unit = {}
    ): AgentRunResult = withContext(logicDispatcher) {
        require(userGoal.isNotBlank()) { "userGoal must not be blank" }
        require(maxSteps > 0) { "maxSteps must be greater than 0" }

        val toolRegistry = ToolRegistry(tools)
        val history = mutableListOf<AgentStepRecord>()

        repeat(maxSteps) { index ->
            val stepNumber = index + 1
            onUpdate(LoopUpdate.AnalyzingScreen(stepNumber))
            val screenState = screenStateProvider.getCurrentScreenState()
            val prompt = systemPromptBuilder.build(
                userGoal = userGoal,
                tools = tools,
                screenState = screenState,
                history = history
            )

            Timber.d("AgentLoopManager: Step %d prompt built", stepNumber)

            onUpdate(LoopUpdate.Thinking(stepNumber))
            val rawModelOutput = llmEngine.generate(prompt)
            val decision = parseDecision(rawModelOutput)
            toolRegistry.validate(decision.action)

            Timber.d(
                "AgentLoopManager: Step %d selected tool=%s args=%s",
                stepNumber,
                decision.action.name,
                decision.action.args
            )

            if (decision.action.name == FINISH_TOOL) {
                val finalMessage = decision.action.args.stringValue("result")
                    ?: decision.thought
                history += AgentStepRecord(
                    step = stepNumber,
                    thought = decision.thought,
                    actionName = decision.action.name,
                    actionArgs = decision.action.args,
                    execution = ActionExecutionResult(
                        success = true,
                        message = finalMessage
                    )
                )
                val result = AgentRunResult(
                    status = AgentRunStatus.FINISHED,
                    totalSteps = history.size,
                    finalMessage = finalMessage,
                    history = history.toList()
                )
                onUpdate(LoopUpdate.Finished(result))
                return@withContext result
            }

            onUpdate(
                LoopUpdate.ExecutingAction(
                    step = stepNumber,
                    action = decision.action,
                    description = decision.action.describe()
                )
            )
            val executionResult = withContext(uiDispatcher) {
                actionExecutor.execute(decision.action)
            }

            history += AgentStepRecord(
                step = stepNumber,
                thought = decision.thought,
                actionName = decision.action.name,
                actionArgs = decision.action.args,
                execution = executionResult
            )

            if (!executionResult.success) {
                val result = AgentRunResult(
                    status = AgentRunStatus.FAILED,
                    totalSteps = history.size,
                    finalMessage = executionResult.message,
                    history = history.toList()
                )
                onUpdate(LoopUpdate.Finished(result))
                return@withContext result
            }

            delay(settleDelayMs)
        }

        val result = AgentRunResult(
            status = AgentRunStatus.MAX_STEPS_REACHED,
            totalSteps = history.size,
            finalMessage = "Stopped after reaching the maximum of $maxSteps steps.",
            history = history.toList()
        )
        onUpdate(LoopUpdate.Finished(result))
        result
    }

    private fun parseDecision(rawModelOutput: String): AgentDecision {
        val normalized = extractJsonObject(rawModelOutput)
        return try {
            json.decodeFromString<AgentDecision>(normalized)
        } catch (error: SerializationException) {
            Timber.e(error, "AgentLoopManager: Invalid model JSON: %s", rawModelOutput)
            throw IllegalArgumentException("Model returned invalid action JSON: $rawModelOutput", error)
        }
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        require(start >= 0) {
            "Model response did not contain a JSON object: $raw"
        }

        var depth = 0
        for (index in start until raw.length) {
            when (raw[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(start, index + 1)
                    }
                }
            }
        }

        error("Model response contained an unterminated JSON object: $raw")
    }

    private class ToolRegistry(
        private val tools: List<ToolDefinition>
    ) {
        private val definitionsByName = tools.associateBy { it.name }

        fun validate(call: ToolCall) {
            val definition = definitionsByName[call.name]
                ?: error("Unknown tool returned by model: ${call.name}")

            val allowedNames = definition.parameters.mapTo(mutableSetOf()) { it.name }
            val requiredNames = definition.parameters
                .filter { it.required }
                .mapTo(mutableSetOf()) { it.name }

            val providedNames = call.args.keys
            val missing = requiredNames - providedNames
            require(missing.isEmpty()) {
                "Tool ${call.name} is missing required args: ${missing.joinToString()}"
            }

            val unknown = providedNames - allowedNames
            require(unknown.isEmpty()) {
                "Tool ${call.name} contains unknown args: ${unknown.joinToString()}"
            }
        }
    }

    companion object {
        const val FINISH_TOOL = "finish"

        fun defaultTools(): List<ToolDefinition> = listOf(
            ToolDefinition(
                name = "click",
                description = "Tap a visible clickable node by id.",
                parameters = listOf(
                    ToolParameterDefinition(
                        name = "id",
                        type = "integer",
                        required = true,
                        description = "The exact node id from CurrentScreenState.nodes[].id."
                    )
                ),
                example = buildJsonObject {
                    put("name", "click")
                    put(
                        "args",
                        buildJsonObject {
                            put("id", 1)
                        }
                    )
                }
            ),
            ToolDefinition(
                name = "type",
                description = "Enter text into an editable node by id.",
                parameters = listOf(
                    ToolParameterDefinition(
                        name = "id",
                        type = "integer",
                        required = true,
                        description = "The exact editable node id from CurrentScreenState.nodes[].id."
                    ),
                    ToolParameterDefinition(
                        name = "text",
                        type = "string",
                        required = true,
                        description = "The exact text to type."
                    )
                ),
                example = buildJsonObject {
                    put("name", "type")
                    put(
                        "args",
                        buildJsonObject {
                            put("id", 2)
                            put("text", "Hello John")
                        }
                    )
                }
            ),
            ToolDefinition(
                name = FINISH_TOOL,
                description = "Terminate the loop when the task is complete, blocked, or unsafe.",
                parameters = listOf(
                    ToolParameterDefinition(
                        name = "result",
                        type = "string",
                        required = true,
                        description = "A short human-readable completion or failure summary."
                    )
                ),
                example = buildJsonObject {
                    put("name", FINISH_TOOL)
                    put(
                        "args",
                        buildJsonObject {
                            put("result", "Message sent to John.")
                        }
                    )
                }
            )
        )
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

private fun ToolCall.describe(): String = when (name) {
    "click" -> "Clicking node ${args.intValue("id") ?: "?"}"
    "type" -> {
        val id = args.intValue("id") ?: "?"
        val text = args.stringValue("text").orEmpty().ifBlank { "text" }
        "Typing \"$text\" into node $id"
    }
    AgentLoopManager.FINISH_TOOL -> "Finishing task"
    else -> buildString {
        append("Executing ")
        append(name)
        if (args.isNotEmpty()) {
            append(" ")
            append(args)
        }
    }
}

private fun JsonObject.intValue(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
