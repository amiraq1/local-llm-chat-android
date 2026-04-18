package com.example.localllm.ui.benchmark

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.db.dao.BenchmarkDao
import com.example.localllm.data.db.entity.BenchmarkResultEntity
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.domain.model.BenchmarkResult
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.engine.ChatMessage
import com.example.localllm.engine.GenerationRequest
import com.example.localllm.engine.GenerationResponse
import com.example.localllm.engine.InferenceEngine
import com.example.localllm.engine.ModelConfig
import com.example.localllm.engine.ModelSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BenchmarkUiState(
    val isRunning: Boolean = false,
    val isCancelling: Boolean = false,
    val results: List<BenchmarkResult> = emptyList(),
    val currentTTFT: Long? = null,
    val currentTPS: Double? = null,
    val currentTokens: Int? = null,
    val currentRunIndex: Int = 0,
    val totalRuns: Int = 0,
    val completedRuns: Int = 0,
    val warmupRuns: Int = 0,
    val averageTTFT: Double? = null,
    val averageTPS: Double? = null,
    val bestTPS: Double? = null,
    val worstTPS: Double? = null,
    val activeModelId: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class BenchmarkConfig(
    val prompt: String = BENCHMARK_PROMPT,
    val maxTokens: Int = 200,
    val warmupRuns: Int = 1,
    val measuredRuns: Int = 3
) {
    init {
        require(maxTokens > 0) { "maxTokens must be > 0" }
        require(warmupRuns >= 0) { "warmupRuns must be >= 0" }
        require(measuredRuns > 0) { "measuredRuns must be > 0" }
    }

    val totalRuns: Int get() = warmupRuns + measuredRuns

    companion object {
        const val BENCHMARK_PROMPT =
            "اشرح لي مفهوم التعلم الآلي بشكل مبسط في ثلاث نقاط رئيسية."
    }
}

@HiltViewModel
class BenchmarkViewModel @Inject constructor(
    private val inferenceEngine: InferenceEngine,
    private val modelRepository: ModelRepository,
    private val benchmarkDao: BenchmarkDao,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(BenchmarkUiState())
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

    private var benchmarkJob: Job? = null

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                benchmarkDao.getAllResults(),
                settingsDataStore.settings
            ) { entities, settings ->
                Pair(
                    entities.map { it.toDomain() },
                    settings.activeModelId
                )
            }.collect { (results, activeModelId) ->
                _state.update {
                    it.copy(
                        results = results,
                        activeModelId = activeModelId
                    )
                }
            }
        }
    }

    fun runBenchmark(
        config: BenchmarkConfig = BenchmarkConfig()
    ) {
        if (benchmarkJob?.isActive == true || _state.value.isRunning) return

        benchmarkJob = viewModelScope.launch {
            var session: ModelSession? = null

            _state.update {
                it.copy(
                    isRunning = true,
                    isCancelling = false,
                    currentTTFT = null,
                    currentTPS = null,
                    currentTokens = 0,
                    currentRunIndex = 0,
                    completedRuns = 0,
                    totalRuns = config.totalRuns,
                    warmupRuns = config.warmupRuns,
                    averageTTFT = null,
                    averageTPS = null,
                    bestTPS = null,
                    worstTPS = null,
                    statusMessage = "جاري تهيئة الاختبار...",
                    errorMessage = null
                )
            }

            try {
                val activeModel = modelRepository.getActiveModel()
                    ?: run {
                        _state.update {
                            it.copy(
                                isRunning = false,
                                errorMessage = "لا يوجد نموذج نشط لتشغيل الاختبار"
                            )
                        }
                        return@launch
                    }

                session = inferenceEngine.loadModel(
                    modelPath = activeModel.filePath,
                    config = ModelConfig(contextLength = activeModel.contextLength)
                ).getOrThrow()

                val measuredRuns = mutableListOf<SingleRunMetrics>()

                repeat(config.totalRuns) { runIndex ->
                    ensureActive()

                    val isWarmup = runIndex < config.warmupRuns
                    val displayIndex = runIndex + 1

                    _state.update {
                        it.copy(
                            currentRunIndex = displayIndex,
                            statusMessage = if (isWarmup) {
                                "تشغيل تمهيدي $displayIndex/${config.totalRuns}"
                            } else {
                                val measuredIndex = displayIndex - config.warmupRuns
                                "تشغيل قياس $measuredIndex/${config.measuredRuns}"
                            },
                            currentTTFT = null,
                            currentTPS = null,
                            currentTokens = 0
                        )
                    }

                    session.resetContext()

                    val runMetrics = runSingleBenchmarkRun(
                        session = session,
                        prompt = config.prompt,
                        maxTokens = config.maxTokens
                    )

                    _state.update {
                        it.copy(
                            currentTTFT = runMetrics.ttftMs,
                            currentTPS = runMetrics.tokensPerSecond,
                            currentTokens = runMetrics.totalTokens,
                            completedRuns = displayIndex
                        )
                    }

                    if (!isWarmup) {
                        measuredRuns += runMetrics

                        val averageTTFT = measuredRuns.map { it.ttftMs }.average()
                        val averageTPS = measuredRuns.map { it.tokensPerSecond }.average()
                        val bestTPS = measuredRuns.maxOf { it.tokensPerSecond }
                        val worstTPS = measuredRuns.minOf { it.tokensPerSecond }

                        _state.update {
                            it.copy(
                                averageTTFT = averageTTFT,
                                averageTPS = averageTPS,
                                bestTPS = bestTPS,
                                worstTPS = worstTPS
                            )
                        }

                        benchmarkDao.insert(
                            BenchmarkResultEntity(
                                modelId = activeModel.id,
                                ttftMs = runMetrics.ttftMs,
                                tokensPerSecond = runMetrics.tokensPerSecond,
                                totalTokens = runMetrics.totalTokens,
                                deviceRamMb = getAvailableRamMb(),
                                promptText = config.prompt
                            )
                        )
                    }
                }

                val finalAverageTTFT = measuredRuns.map { it.ttftMs }.average()
                val finalAverageTPS = measuredRuns.map { it.tokensPerSecond }.average()
                val finalBestTPS = measuredRuns.maxOfOrNull { it.tokensPerSecond }
                val finalWorstTPS = measuredRuns.minOfOrNull { it.tokensPerSecond }

                _state.update {
                    it.copy(
                        isRunning = false,
                        isCancelling = false,
                        averageTTFT = finalAverageTTFT.takeIf { !it.isNaN() },
                        averageTPS = finalAverageTPS.takeIf { !it.isNaN() },
                        bestTPS = finalBestTPS,
                        worstTPS = finalWorstTPS,
                        statusMessage = "اكتمل الاختبار"
                    )
                }

                Timber.d(
                    "Benchmark complete: runs=%s avgTTFT=%.2fms avgTPS=%.2f bestTPS=%.2f worstTPS=%.2f",
                    measuredRuns.size,
                    finalAverageTTFT,
                    finalAverageTPS,
                    finalBestTPS ?: 0.0,
                    finalWorstTPS ?: 0.0
                )
            } catch (cancelled: CancellationException) {
                Timber.i("Benchmark cancelled")
                _state.update {
                    it.copy(
                        isRunning = false,
                        isCancelling = false,
                        statusMessage = "تم إلغاء الاختبار"
                    )
                }
                throw cancelled
            } catch (e: Exception) {
                Timber.e(e, "Benchmark failed")
                _state.update {
                    it.copy(
                        isRunning = false,
                        isCancelling = false,
                        errorMessage = "فشل الاختبار: ${e.message ?: "خطأ غير معروف"}",
                        statusMessage = null
                    )
                }
            } finally {
                runCatching { session?.close() }
                    .onFailure { Timber.w(it, "Benchmark session close failed") }

                runCatching { inferenceEngine.unloadModel() }
                    .onFailure { Timber.w(it, "Benchmark engine unload failed") }
            }
        }
    }

    fun cancelBenchmark() {
        val job = benchmarkJob ?: return
        if (!job.isActive) return

        _state.update {
            it.copy(
                isCancelling = true,
                statusMessage = "جاري إلغاء الاختبار..."
            )
        }
        job.cancel()
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun runSingleBenchmarkRun(
        session: ModelSession,
        prompt: String,
        maxTokens: Int
    ): SingleRunMetrics {
        val request = GenerationRequest(
            messages = listOf(
                ChatMessage(
                    role = MessageRole.USER,
                    content = prompt
                )
            ),
            maxTokens = maxTokens
        )

        val benchmarkStartNs = System.nanoTime()
        var firstTokenLatencyMs: Long? = null
        var tokenCount = 0
        var finished = false
        var generationError: Throwable? = null

        session.generate(request).collect { response ->
            when (response) {
                is GenerationResponse.Token -> {
                    if (firstTokenLatencyMs == null) {
                        firstTokenLatencyMs = nanosToMillis(System.nanoTime() - benchmarkStartNs)
                    }
                    tokenCount += 1
                    _state.update { it.copy(currentTokens = tokenCount) }
                }

                is GenerationResponse.Finished -> {
                    finished = true
                }

                is GenerationResponse.Error -> {
                    generationError = response.throwable
                }
            }
        }

        generationError?.let { throw it }

        if (!finished) {
            throw IllegalStateException("انتهى التوليد بدون إشارة Finished")
        }

        val totalDurationMs = nanosToMillis(System.nanoTime() - benchmarkStartNs).coerceAtLeast(1L)
        val ttftMs = (firstTokenLatencyMs ?: totalDurationMs).coerceAtLeast(0L)
        val generationDurationMs = (totalDurationMs - ttftMs).coerceAtLeast(1L)

        val tokensPerSecond = tokenCount / (generationDurationMs / 1000.0)

        return SingleRunMetrics(
            ttftMs = ttftMs,
            tokensPerSecond = tokensPerSecond,
            totalTokens = tokenCount,
            totalDurationMs = totalDurationMs
        )
    }

    private fun getAvailableRamMb(): Int {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.availMem / 1_000_000).toInt()
    }

    private fun BenchmarkResultEntity.toDomain(): BenchmarkResult =
        BenchmarkResult(
            id = id,
            modelId = modelId,
            runAt = runAt,
            ttftMs = ttftMs,
            tokensPerSecond = tokensPerSecond,
            totalTokens = totalTokens,
            deviceRamMb = deviceRamMb,
            promptText = promptText
        )

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L

    private data class SingleRunMetrics(
        val ttftMs: Long,
        val tokensPerSecond: Double,
        val totalTokens: Int,
        val totalDurationMs: Long
    )
}
