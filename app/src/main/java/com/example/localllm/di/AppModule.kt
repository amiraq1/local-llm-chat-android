package com.example.localllm.di

import android.app.ActivityManager
import android.content.Context
import androidx.room.Room
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.*
import com.example.localllm.data.repository.MlcModelRepository
import com.example.localllm.engine.FakeEngine
import com.example.localllm.engine.FakeInferenceEngine
import com.example.localllm.engine.FallbackInferenceEngine
import com.example.localllm.engine.InferenceEngine
import com.example.localllm.engine.MLCInferenceEngine
import com.example.localllm.engine.MlcEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + defaultDispatcher)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "localllm.db"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            // No fallbackToDestructiveMigration on purpose — schema bumps must
            // ship with explicit migrations to preserve user conversations.
            .build()
    }

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
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(
        engine: FallbackInferenceEngine
    ): InferenceEngine

    /** Concrete native engine — exposed to [FallbackInferenceEngine] via the interface. */
    @Binds
    @Singleton
    @MlcEngine
    abstract fun bindMlcEngine(impl: MLCInferenceEngine): InferenceEngine

    /** Deterministic fallback engine — exposed to [FallbackInferenceEngine] via the interface. */
    @Binds
    @Singleton
    @FakeEngine
    abstract fun bindFakeEngine(impl: FakeInferenceEngine): InferenceEngine
}
