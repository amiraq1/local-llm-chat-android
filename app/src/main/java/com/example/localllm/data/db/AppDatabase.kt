package com.example.localllm.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.localllm.data.db.dao.*
import com.example.localllm.data.db.entity.*

/**
 * Room database for LocalLLM.
 *
 * **Migration policy**
 * - Bump [Database.version] for any schema change (new column, table, index, etc.).
 * - For purely additive changes, prefer adding an [AutoMigration] entry below; Room
 *   generates the migration from the exported `schemas/` JSON files (`exportSchema = true`).
 * - For non-trivial changes (renames, type changes, splits) supply a manual [Migration]
 *   in [AppDatabase.MIGRATIONS] and register it in `DatabaseModule` via `addMigrations(...)`.
 *
 * Never enable `fallbackToDestructiveMigration()` in production — it silently wipes
 * user conversations and downloaded model metadata on schema bumps.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        InstalledModelEntity::class,
        BenchmarkResultEntity::class
    ],
    version = 1,
<<<<<<< HEAD
<<<<<<< HEAD
    exportSchema = false
=======
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
    exportSchema = true,
    autoMigrations = [
        // Add @AutoMigration(from = N, to = N+1) entries here for additive schema bumps.
    ]
<<<<<<< HEAD
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelDao(): ModelDao
    abstract fun benchmarkDao(): BenchmarkDao

    companion object {
        /**
         * Manual migrations to register via `Room.databaseBuilder(...).addMigrations(*MIGRATIONS)`
         * for schema changes that cannot be auto-generated.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
