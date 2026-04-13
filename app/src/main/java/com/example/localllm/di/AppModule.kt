package com.example.localllm.di

import android.app.ActivityManager
import android.content.Context
import androidx.room.Room
import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.*
import com.example.localllm.data.repository.ModelRepository
import com.example.localllm.data.repository.InstalledModelRecord
import com.example.localllm.data.repository.ModelStore
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    @Provides
    @Singleton
    fun provideModelRepository(
        modelDao: ModelDao,
        @ApplicationContext context: Context
    ): ModelRepository {
        val installRootDir = context.getExternalFilesDir(null) ?: context.filesDir
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return ModelRepository(
            modelStore = object : ModelStore {
                override fun getAllInstalledModels(): Flow<List<InstalledModelRecord>> =
                    modelDao.getAllInstalledModels().map { list -> list.map { it.toRecord() } }

                override suspend fun getModelById(id: String): InstalledModelRecord? =
                    modelDao.getModelById(id)?.toRecord()

                override suspend fun getActiveModel(): InstalledModelRecord? =
                    modelDao.getActiveModel()?.toRecord()

                override suspend fun insert(model: InstalledModelRecord) {
                    modelDao.insert(model.toEntity())
                }

                override suspend fun deactivateAll() {
                    modelDao.deactivateAll()
                }

                override suspend fun setActive(id: String) {
                    modelDao.setActive(id)
                }

                override suspend fun setChecksumVerified(id: String, verified: Boolean) {
                    modelDao.setChecksumVerified(id, verified)
                }

                override suspend fun deleteById(id: String) {
                    modelDao.deleteById(id)
                }
            },
            installRootDir = installRootDir,
            availableRamMbProvider = {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                (memInfo.availMem / 1_000_000L).toInt()
            },
            availableStorageBytesProvider = {
                installRootDir.usableSpace
            }
        )
    }
}

private fun com.example.localllm.data.db.entity.InstalledModelEntity.toRecord() = InstalledModelRecord(
    id = id,
    name = name,
    family = family,
    sizeBytes = sizeBytes,
    filePath = filePath,
    installedAt = installedAt,
    checksumVerified = checksumVerified,
    isActive = isActive,
    quantization = quantization,
    contextLength = contextLength
)

private fun InstalledModelRecord.toEntity() = com.example.localllm.data.db.entity.InstalledModelEntity(
    id = id,
    name = name,
    family = family,
    sizeBytes = sizeBytes,
    filePath = filePath,
    installedAt = installedAt,
    checksumVerified = checksumVerified,
    isActive = isActive,
    quantization = quantization,
    contextLength = contextLength
)

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

    /**
     * Bind MLCInferenceEngine as the production InferenceEngine.
     *
     * Uses the real MLC4J native library for on-device LLM inference.
     */

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(engine: MLCInferenceEngine): InferenceEngine
}
