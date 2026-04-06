package com.example.localllm.di

import android.content.Context
import androidx.room.Room
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.*
import com.example.localllm.engine.FakeInferenceEngine
import com.example.localllm.engine.InferenceEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "localllm.db")
            .fallbackToDestructiveMigration()
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
abstract class EngineModule {
    /**
     * Bind FakeInferenceEngine as the default InferenceEngine.
     *
     * To switch to the real MLC engine:
     * 1. Implement MLCInferenceEngine : InferenceEngine
     * 2. Change @Binds here to MLCInferenceEngine
     * 3. Add the MLC AAR to app/build.gradle.kts
     */
    @Binds
    @Singleton
    abstract fun bindInferenceEngine(fake: FakeInferenceEngine): InferenceEngine
}
