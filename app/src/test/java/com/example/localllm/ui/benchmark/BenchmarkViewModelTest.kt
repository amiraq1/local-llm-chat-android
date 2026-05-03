package com.example.localllm.ui.benchmark

import android.content.Context
import com.example.localllm.data.datastore.SettingsDataStore
import com.example.localllm.data.db.dao.BenchmarkDao
import com.example.localllm.data.db.entity.BenchmarkResultEntity
import com.example.localllm.data.repository.MlcModelRepository
import com.example.localllm.domain.model.AppSettings
import com.example.localllm.engine.FakeInferenceEngine
import com.example.localllm.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class BenchmarkViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inferenceEngine = FakeInferenceEngine()
    private val modelRepository = mockk<MlcModelRepository>()
    private val benchmarkDao = mockk<BenchmarkDao>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>()
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `runBenchmark without active model exposes an error and skips persistence`() = runTest {
        every { benchmarkDao.getAllResults() } returns flowOf(emptyList<BenchmarkResultEntity>())
        every { settingsDataStore.settings } returns flowOf(AppSettings())
        coEvery { modelRepository.getActiveModel() } returns null

        val viewModel = BenchmarkViewModel(
            inferenceEngine = inferenceEngine,
            modelRepository = modelRepository,
            benchmarkDao = benchmarkDao,
            settingsDataStore = settingsDataStore,
            context = context
        )
        advanceUntilIdle()

        viewModel.runBenchmark()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isRunning)
        assertEquals("لا يوجد نموذج نشط لتشغيل الاختبار", state.errorMessage)
        coVerify(exactly = 0) { benchmarkDao.insert(any()) }
    }
}
