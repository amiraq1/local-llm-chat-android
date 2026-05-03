package com.example.localllm.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── Conversation ──────────────────────────────────────────────────────────────

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isArchived: Boolean = false
)

// ─── Message ──────────────────────────────────────────────────────────────────

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String,           // "user" | "assistant" | "system" | "tool"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tokensUsed: Int? = null,
    val generationTimeMs: Long? = null
)

// ─── Installed Model ──────────────────────────────────────────────────────────

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val family: String,
    val sizeBytes: Long,
    val filePath: String,
    val installedAt: Long = System.currentTimeMillis(),
    val checksumVerified: Boolean = false,
    val isActive: Boolean = false,
    val quantization: String,
    val contextLength: Int
)

// ─── Benchmark Results ────────────────────────────────────────────────────────

@Entity(tableName = "benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelId: String,
    val runAt: Long = System.currentTimeMillis(),
    val ttftMs: Long,
    val tokensPerSecond: Double,
    val totalTokens: Int,
    val deviceRamMb: Int,
    val promptText: String
)
