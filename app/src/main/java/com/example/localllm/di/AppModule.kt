package com.example.localllm.di

import android.content.Context
import androidx.room.Room
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.BenchmarkDao
import com.example.localllm.data.db.dao.ConversationDao
import com.example.localllm.data.db.dao.MessageDao
import com.example.localllm.data.db.dao.ModelDao
import com.example.localllm.engine.FallbackInferenceEngine
import com.example.localllm.engine.InferenceEngine
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

    /**
     * Scope يعيش طوال عمر التطبيق.
     *
     * مناسب للأعمال التي يجب ألا تتوقف بخروج الشاشة:
     * - cleanup
     * - model unload
     * - deferred persistence
     * - background coordination
     */
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
            /**
             * فعّل هذا فقط أثناء التطوير إذا لم تكن migrations جاهزة.
             *
             * في الإنتاج الحقيقي:
             * الأفضل تعريف migrations صريحة بدل حذف البيانات.
             */
            // .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(
        db: AppDatabase
    ): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideMessageDao(
        db: AppDatabase
    ): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideModelDao(
        db: AppDatabase
    ): ModelDao = db.modelDao()

    @Provides
    @Singleton
    fun provideBenchmarkDao(
        db: AppDatabase
    ): BenchmarkDao = db.benchmarkDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
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
abstract class EngineBindingModule {

    /**
     * الربط الحالي:
     * - يجرب MLC الحقيقي أولًا
     * - ثم ينتقل إلى Fake إذا كانت المكتبات الأصلية غير جاهزة
     *
     * غيّر الربط هنا لاحقًا إلى MLCInferenceEngine فقط
     * عندما تصبح كل model libs مضمّنة ومضمونة.
     */
    @Binds
    @Singleton
    abstract fun bindInferenceEngine(
        engine: FallbackInferenceEngine
    ): InferenceEngine
}
