package com.example.localllm.data.db.dao

import androidx.room.*
import com.example.localllm.data.db.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :time, messageCount = messageCount + 1 WHERE id = :id")
    suspend fun incrementMessageCount(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE conversations SET isArchived = 1 WHERE id = :id")
    suspend fun archiveById(id: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 0")
    suspend fun getActiveCount(): Int
}
