package com.example.localllm.engine

import android.content.Context
import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionRole
import ai.mlc.mlcllm.OpenAIProtocol.CompletionUsage
import ai.mlc.mlcllm.OpenAIProtocol.StreamOptions
import com.example.localllm.domain.model.CatalogModel
import com.example.localllm.domain.model.CuratedModelCatalog
import com.example.localllm.mlc.MlcChatConfig
import com.example.localllm.mlc.MlcModelRecord
import com.example.localllm.mlc.findBundledMlcModel
import com.example.localllm.mlc.readInstalledMlcChatConfig
import com.example.localllm.mlc.readInstalledMlcTensorCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLCInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : InferenceEngine {

    private val catalogModels: List<CatalogModel> by lazy {
        CuratedModelCatalog.load(context)
    }

    private val stateMutex = Mutex()

    @Volatile
    private var runtime: MLCEngine? = null

    @Volatile
    private var activeSession: MLCModelSession? = null

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var activeModelLib: String? = null

    override suspend fun loadModel(
        modelPath: String,
        config: ModelConfig
    ): Result<ModelSession> = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            runCatching {
                val modelDir = File(modelPath)
                require(modelDir.exists()) { "Model path not found: $modelPath" }
                require(modelDir.isDirectory) { "MLC requires a model directory: $modelPath" }

                val modelRecord = resolveModelRecord(modelDir)
                val installedConfig = validateInstalledAssets(modelDir)
                validateBundledModelLibrary(modelRecord)

                val currentPath = activeModelPath
                val currentLib = activeModelLib
                val shouldReload = currentPath != modelDir.absolutePath || currentLib != modelRecord.modelLib

                val engine = ensureRuntime()

                if (shouldReload) {
                    // أغلق الجلسة السابقة منطقيًا قبل إعادة التحميل
                    activeSession?.close()
                    activeSession = null

                    Timber.i(
                        "Loading MLC model %s from %s with model_lib=%s",
                        modelRecord.modelId,
                        modelDir.absolutePath,
                        modelRecord.modelLib
                    )
                    engine.reload(modelDir.absolutePath, modelRecord.modelLib)
                } else {
                    Timber.d("Reusing loaded MLC model %s", modelRecord.modelId)
                }

                MLCModelSession(
                    engine = engine,
                    config = config,
                    modelId = modelRecord.modelId,
                    modelLabel = modelDir.name,
                    installedConfig = installedConfig
                ).also { session ->
                    activeSession = session
                    activeModelPath = modelDir.absolutePath
                    activeModelLib = modelRecord.modelLib
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to load MLC model from %s", modelPath)
                clearActiveState()
            }
        }
    }

    override fun isModelLoaded(): Boolean {
        return runtime != null &&
            activeSession != null &&
            activeModelPath != null &&
            activeModelLib != null
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            stateMutex.withLock {
                val currentSession = activeSession
                val currentRuntime = runtime

                runCatching {
                    currentSession?.close()
                }.onFailure { error ->
                    Timber.w(error, "MLC session close encountered an error")
                }

                runCatching {
                    currentRuntime?.unload()
                }.onFailure { error ->
                    Timber.w(error, "MLC unload encountered an error")
                }

                runtime = null
                clearActiveState()
            }
        }
    }

    override fun getEngineInfo(): EngineInfo = EngineInfo(
        name = "MLC LLM Engine",
        version = if (isBundledTvmAvailable()) "native" else "missing-tvm4j",
        backend = "MLC"
    )

    private fun ensureRuntime(): MLCEngine {
        val existing = runtime
        if (existing != null) return existing

        return createRuntime().also { created ->
            runtime = created
        }
    }

    private fun createRuntime(): MLCEngine {
        return try {
            MLCEngine()
        } catch (error: Throwable) {
            throw IllegalStateException(
                buildString {
                    append("تعذر تهيئة محرك MLC الحقيقي. ")
                    append("تأكد من تضمين tvm4j ومن وجود المكتبات الأصلية المناسبة لهذا الجهاز.")
                },
                error
            )
        }
    }

    private fun clearActiveState() {
        activeSession = null
        activeModelPath = null
        activeModelLib = null
    }

    private fun resolveModelRecord(modelDir: File): MlcModelRecord {
        catalogModels.firstOrNull { it.slug == modelDir.name }
            ?.toMlcModelRecord()
            ?.let { return it }

        findBundledMlcModel(context, modelDir.name)?.let { return it }

        error(
            "لا توجد بيانات MLC مرتبطة بالنموذج ${modelDir.name}. " +
                "النماذج المحلية المخصصة تحتاج model_id و model_lib معروفين."
        )
    }

    private fun validateInstalledAssets(modelDir: File): MlcChatConfig {
        val chatConfig = readInstalledMlcChatConfig(modelDir)
        val tensorCache = readInstalledMlcTensorCache(modelDir)

        val missingFiles = buildList {
            chatConfig.tokenizerFiles.forEach { relativePath ->
                if (!File(modelDir, relativePath).exists()) add(relativePath)
            }
            tensorCache.records.forEach { record ->
                if (!File(modelDir, record.dataPath).exists()) add(record.dataPath)
            }
        }

        require(missingFiles.isEmpty()) {
            val preview = missingFiles.take(5).joinToString()
            val suffix = if (missingFiles.size > 5) " ..." else ""
            "ملفات النموذج ناقصة داخل ${modelDir.name}: $preview$suffix"
        }

        return chatConfig
    }

    private fun validateBundledModelLibrary(modelRecord: MlcModelRecord) {
        val expectedLibrary = File(
            context.applicationInfo.nativeLibraryDir,
            "lib${modelRecord.modelLib}.so"
        )

        require(expectedLibrary.exists()) {
            "مكتبة النموذج الأصلية ${expectedLibrary.name} غير مضمنة في هذا البناء. " +
                "الـ runtime موجود، لكن يلزم إضافة مكتبات MLC المولدة لكل نموذج قبل التوليد الحقيقي."
        }
    }

    private fun isBundledTvmAvailable(): Boolean =
        runCatching {
            Class.forName("org.apache.tvm.Function")
        }.isSuccess
}

private class MLCModelSession(
    private val engine: MLCEngine,
    private val config: ModelConfig,
    private val modelId: String,
    private val modelLabel: String,
    private val installedConfig: MlcChatConfig
) : ModelSession {

    @Volatile
    private var contextTokenCount = 0

    override fun generate(request: GenerationRequest): Flow<GenerationResponse> = flow {
        try {
            val promptTokensEstimate = request.messages.sumOf { approximateTokenCount(it.content) }

            val stream = engine.chat.completions.create(
                messages = request.messages.map(ChatMessage::toMlcMessage),
                model = modelId,
                max_tokens = request.maxTokens,
                temperature = request.temperature,
                top_p = request.topP,
                stop = request.stopSequences.ifEmpty { null },
                stream = true,
                stream_options = StreamOptions(include_usage = true)
            )

            var emittedCompletionChunks = 0
            var finishReason = FinishReason.STOP
            var finishedEmitted = false

            for (response in stream) {
                response.choices.forEach { choice ->
                    val tokenText = choice.delta.content?.asText().orEmpty()
                    if (tokenText.isNotEmpty()) {
                        emit(GenerationResponse.Token(tokenText))
                        emittedCompletionChunks++
                    }

                    finishReason = choice.finish_reason.toFinishReason(finishReason)
                }

                response.usage?.let { usage ->
                    contextTokenCount = usage.total_tokens
                        .coerceAtMost(installedConfig.contextWindowSize)
                        .coerceAtMost(config.contextLength)

                    emit(
                        GenerationResponse.Finished(
                            finishReason = finishReason,
                            usage = usage.toTokenUsage(emittedCompletionChunks)
                        )
                    )
                    finishedEmitted = true
                    return@flow
                }
            }

            if (!finishedEmitted) {
                contextTokenCount = (promptTokensEstimate + emittedCompletionChunks)
                    .coerceAtMost(installedConfig.contextWindowSize)
                    .coerceAtMost(config.contextLength)

                emit(
                    GenerationResponse.Finished(
                        finishReason = finishReason,
                        usage = TokenUsage(
                            promptTokens = promptTokensEstimate,
                            completionTokens = emittedCompletionChunks
                        )
                    )
                )
            }
        } catch (error: Throwable) {
            Timber.e(error, "MLC generation failed for %s", modelLabel)
            emit(GenerationResponse.Error(error))
        }
    }

    override fun resetContext() {
        runCatching {
            engine.reset()
            contextTokenCount = 0
        }.onFailure { error ->
            Timber.w(error, "MLC context reset failed for %s", modelLabel)
        }
    }

    override fun getContextLength(): Int =
        contextTokenCount.coerceAtMost(config.contextLength)

    override suspend fun close() {
        contextTokenCount = 0
        Timber.d("MLCModelSession: closing session for %s", modelLabel)
    }
}

private fun ChatMessage.toMlcMessage(): ChatCompletionMessage =
    ChatCompletionMessage(
        role = when (role) {
            com.example.localllm.domain.model.MessageRole.SYSTEM -> ChatCompletionRole.system
            com.example.localllm.domain.model.MessageRole.USER -> ChatCompletionRole.user
            com.example.localllm.domain.model.MessageRole.ASSISTANT -> ChatCompletionRole.assistant
        },
        content = content
    )

private fun CompletionUsage.toTokenUsage(fallbackCompletionTokens: Int): TokenUsage =
    TokenUsage(
        promptTokens = prompt_tokens,
        completionTokens = completion_tokens.takeIf { it > 0 } ?: fallbackCompletionTokens
    )

private fun String?.toFinishReason(defaultReason: FinishReason): FinishReason =
    when (this?.lowercase()) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.MAX_TOKENS
        "error" -> FinishReason.ERROR
        null, "" -> defaultReason
        else -> defaultReason
    }

private fun approximateTokenCount(text: String): Int =
    (text.length / 4).coerceAtLeast(1)
