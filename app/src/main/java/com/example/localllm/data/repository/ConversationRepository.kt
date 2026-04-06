package com.example.localllm.data.repository

import com.example.localllm.data.db.dao.ConversationDao
import com.example.localllm.data.db.dao.MessageDao
import com.example.localllm.data.db.entity.ConversationEntity
import com.example.localllm.data.db.entity.MessageEntity
import com.example.localllm.domain.model.Conversation
import com.example.localllm.domain.model.Message
import com.example.localllm.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    // ─── Conversations ─────────────────────────────────────────────────────────

    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { it.map(ConversationEntity::toDomain) }

    suspend fun getConversationById(id: Long): Conversation? =
        conversationDao.getConversationById(id)?.toDomain()

    suspend fun createConversation(title: String, modelId: String): Long {
        val entity = ConversationEntity(title = title, modelId = modelId)
        return conversationDao.insert(entity).also {
            Timber.d("Created conversation $it: $title")
        }
    }

    suspend fun updateConversationTitle(id: Long, title: String) =
        conversationDao.updateTitle(id, title)

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
        Timber.d("Deleted conversation $id")
    }

    suspend fun archiveConversation(id: Long) =
        conversationDao.archiveById(id)

    // ─── Messages ──────────────────────────────────────────────────────────────

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId)
            .map { it.map(MessageEntity::toDomain) }

    suspend fun addMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        tokensUsed: Int? = null,
        generationTimeMs: Long? = null
    ): Long {
        val entity = MessageEntity(
            conversationId = conversationId,
            role = role.name.lowercase(),
            content = content,
            tokensUsed = tokensUsed,
            generationTimeMs = generationTimeMs
        )
        val id = messageDao.insert(entity)
        conversationDao.incrementMessageCount(conversationId)
        return id
    }

    suspend fun updateMessage(message: Message) =
        messageDao.update(message.toEntity())
}

// ─── Mappers ───────────────────────────────────────────────────────────────────

fun ConversationEntity.toDomain() = Conversation(
    id = id,
    title = title,
    modelId = modelId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    isArchived = isArchived
)

fun MessageEntity.toDomain() = Message(
    id = id,
    conversationId = conversationId,
    role = when (role.uppercase()) {
        "USER" -> MessageRole.USER
        "ASSISTANT" -> MessageRole.ASSISTANT
        else -> MessageRole.SYSTEM
    },
    content = content,
    createdAt = createdAt,
    tokensUsed = tokensUsed,
    generationTimeMs = generationTimeMs
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name.lowercase(),
    content = content,
    createdAt = createdAt,
    tokensUsed = tokensUsed,
    generationTimeMs = generationTimeMs
)
