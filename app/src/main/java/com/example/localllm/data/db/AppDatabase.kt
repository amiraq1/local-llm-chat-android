package com.example.localllm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.localllm.data.db.dao.*
import com.example.localllm.data.db.entity.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        InstalledModelEntity::class,
        BenchmarkResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelDao(): ModelDao
    abstract fun benchmarkDao(): BenchmarkDao
}
