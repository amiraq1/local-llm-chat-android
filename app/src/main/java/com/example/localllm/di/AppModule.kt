package com.example.localllm.di

import android.content.Context
import androidx.room.Room
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.*
import com.example.localllm.engine.InferenceEngine
import com.example.localllm.engine.MLCInferenceEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for a [CoroutineScope] bound to the application lifecycle. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "localllm.db")
            .build()

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideModelDao(db: AppDatabase): ModelDao = db.modelDao()

    @Provides
    fun provideBenchmarkDao(db: AppDatabase): BenchmarkDao = db.benchmarkDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * Application-scoped [CoroutineScope] for work that must outlive ViewModels
     * (e.g., native model unloading, pending DB writes).
     *
     * Uses [SupervisorJob] so child failures don't cancel the entire scope.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {
codex/fix-audit-findings
    /**
     * Bind MLCInferenceEngine as the production InferenceEngine.
     *
     * Uses the real MLC4J native library for on-device LLM inference.
     * To switch back to the fake engine for UI testing, change binding to:
     *   abstract fun bindInferenceEngine(fake: FakeInferenceEngine): InferenceEngine
     */

main
    @Binds
    @Singleton
    abstract fun bindInferenceEngine(engine: MLCInferenceEngine): InferenceEngine
}
