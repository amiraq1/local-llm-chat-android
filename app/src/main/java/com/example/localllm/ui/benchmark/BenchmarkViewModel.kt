package com.example.localllm.ui.benchmark

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.data.db.dao.BenchmarkDao
import com.example.localllm.data.db.entity.BenchmarkResultEntity
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.domain.model.BenchmarkResult
import com.example.localllm.domain.model.MessageRole
import com.example.localllm.engine.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BenchmarkUiState(
    val isRunning: Boolean = false,
    val results: List<BenchmarkResult> = emptyList(),
    val currentTTFT: Long? = null,
    val currentTPS: Double? = null,
    val currentTokens: Int? = null,
    val activeModelId: String = "",
    val errorMessage: String? = null
)

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

    companion object {
        const val BENCHMARK_PROMPT = "اشرح لي مفهوم التعلم الآلي بشكل مبسط في ثلاث نقاط رئيسية."
    }

    init {
        viewModelScope.launch {
            combine(
                benchmarkDao.getAllResults(),
                settingsDataStore.settings
            ) { entities, settings ->
                Pair(entities.map { it.toDomain() }, settings.activeModelId)
            }.collect { (results, activeModelId) ->
                _state.update { it.copy(results = results, activeModelId = activeModelId) }
            }
        }
    }

    fun runBenchmark() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true, currentTTFT = null, currentTPS = null, currentTokens = null) }

        viewModelScope.launch {
            var session: ModelSession? = null
            try {
                val activeModel = modelRepository.getActiveModel()
                if (activeModel == null) {
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

                val request = GenerationRequest(
                    messages = listOf(ChatMessage(role = MessageRole.USER, content = BENCHMARK_PROMPT)),
                    maxTokens = 200
                )

                val startTime = System.currentTimeMillis()
                var firstTokenTime: Long? = null
                var tokenCount = 0

                session.generate(request).collect { response ->
                    when (response) {
                        is GenerationResponse.Token -> {
                            if (firstTokenTime == null) {
                                firstTokenTime = System.currentTimeMillis() - startTime
                                _state.update { it.copy(currentTTFT = firstTokenTime) }
                            }
                            tokenCount++
                            _state.update { it.copy(currentTokens = tokenCount) }
                        }
                        is GenerationResponse.Finished -> {
                            val totalMs = System.currentTimeMillis() - startTime
                            val tps = tokenCount / (totalMs / 1000.0)

                            _state.update { it.copy(currentTPS = tps, isRunning = false) }

                            val result = BenchmarkResultEntity(
                                modelId = activeModel.id,
                                ttftMs = firstTokenTime ?: 0L,
                                tokensPerSecond = tps,
                                totalTokens = tokenCount,
                                deviceRamMb = getAvailableRamMb(),
                                promptText = BENCHMARK_PROMPT
                            )
                            benchmarkDao.insert(result)
                            Timber.d("Benchmark complete: ${tps}tps, TTFT=${firstTokenTime}ms")
                        }
                        is GenerationResponse.Error -> {
                            _state.update {
                                it.copy(isRunning = false, errorMessage = "حدث خطأ في الاختبار")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Benchmark failed")
                _state.update { it.copy(isRunning = false, errorMessage = "فشل الاختبار: ${e.message}") }
            } finally {
                runCatching {
                    session?.close()
                    inferenceEngine.unloadModel()
                }.onFailure { cleanupError ->
                    Timber.w(cleanupError, "Benchmark cleanup failed")
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private fun getAvailableRamMb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return (memInfo.availMem / 1_000_000).toInt()
    }

    private fun BenchmarkResultEntity.toDomain() = BenchmarkResult(
        id = id, modelId = modelId, runAt = runAt, ttftMs = ttftMs,
        tokensPerSecond = tokensPerSecond, totalTokens = totalTokens,
        deviceRamMb = deviceRamMb, promptText = promptText
    )
}
