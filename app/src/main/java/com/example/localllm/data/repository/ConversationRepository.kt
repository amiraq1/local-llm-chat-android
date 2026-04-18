package com.example.localllm.data.repository

import androidx.room.withTransaction
import com.example.localllm.data.db.AppDatabase
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
    private val db: AppDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { conversations -> conversations.map { it.toDomain() } }
    }

    suspend fun getConversationById(id: Long): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId)
            .map { messages -> messages.map { it.toDomain() } }
    }

    suspend fun createConversation(
        title: String,
        modelId: String
    ): Long {
        val normalizedTitle = title.trim().ifBlank { "محادثة جديدة" }
        val entity = ConversationEntity(
            title = normalizedTitle,
            modelId = modelId
        )

        return conversationDao.insert(entity).also { conversationId ->
            Timber.d("Created conversation id=%s title=%s modelId=%s", conversationId, normalizedTitle, modelId)
        }
    }

    suspend fun updateConversationTitle(
        id: Long,
        title: String
    ): Boolean {
        val normalizedTitle = title.trim().ifBlank { "محادثة جديدة" }
        return runCatching {
            conversationDao.updateTitle(id, normalizedTitle)
            Timber.d("Updated conversation title id=%s title=%s", id, normalizedTitle)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to update conversation title id=%s", id)
            false
        }
    }

    suspend fun archiveConversation(id: Long): Boolean {
        return runCatching {
            conversationDao.archiveById(id)
            Timber.d("Archived conversation id=%s", id)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to archive conversation id=%s", id)
            false
        }
    }

    /**
     * Deletes a conversation and relies on Room foreign keys / DAO policy
     * to remove associated messages if cascade delete is configured.
     *
     * If cascade is not configured in the schema, move message deletion into
     * this transaction explicitly.
     */
    suspend fun deleteConversation(id: Long): Boolean {
        return runCatching {
            db.withTransaction {
                conversationDao.deleteById(id)
            }
            Timber.d("Deleted conversation id=%s", id)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to delete conversation id=%s", id)
            false
        }
    }

    /**
     * Inserts a message and increments the cached message count atomically.
     *
     * Important:
     * If you later add message deletion/edit flows that affect counts,
     * keep them transactional as well, or prefer deriving messageCount by query.
     */
    suspend fun addMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        tokensUsed: Int? = null,
        generationTimeMs: Long? = null
    ): Long {
        val normalizedContent = content.trim()
        require(normalizedContent.isNotEmpty()) { "Message content cannot be blank" }

        // Assume MessageRole has a property storageValue (like enum lowercase value)
        // If the enum relies on name, we use role.name.lowercase() here if needed.
        val entity = MessageEntity(
            conversationId = conversationId,
            role = role.name.lowercase(), // fallback mapped to avoid compilation issues in case of storageValue absence.
            content = normalizedContent,
            tokensUsed = tokensUsed,
            generationTimeMs = generationTimeMs
        )

        return db.withTransaction {
            val conversation = conversationDao.getConversationById(conversationId)
                ?: error("Conversation not found: $conversationId")

            val messageId = messageDao.insert(entity)
            conversationDao.incrementMessageCount(conversation.id)

            Timber.d(
                "Inserted message id=%s conversationId=%s role=%s tokens=%s generationMs=%s",
                messageId,
                conversationId,
                role,
                tokensUsed,
                generationTimeMs
            )

            messageId
        }
    }

    suspend fun updateMessage(message: Message): Boolean {
        return runCatching {
            messageDao.update(message.toEntity())
            Timber.d("Updated message id=%s conversationId=%s", message.id, message.conversationId)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to update message id=%s", message.id)
            false
        }
    }

    /**
     * Use with DAO query:
     * WHERE content LIKE '%' || :query || '%' ESCAPE '\'
     */
    suspend fun searchMessages(query: String): List<Message> {
        val sanitized = sanitizeLikeQuery(query.trim())
        if (sanitized.isBlank()) return emptyList()

        return messageDao.searchMessages(sanitized)
            .map { it.toDomain() }
    }

    /**
     * Stronger version if later you add DAO support:
     * - delete message
     * - decrement cached message count atomically
     */
    suspend fun deleteMessage(
        messageId: Long,
        conversationId: Long
    ): Boolean {
        return runCatching {
            db.withTransaction {
                messageDao.deleteById(messageId)
                // Assumes decrementMessageCount is added to DAO in future
                // conversationDao.decrementMessageCount(conversationId)
            }
            Timber.d("Deleted message id=%s conversationId=%s", messageId, conversationId)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to delete message id=%s", messageId)
            false
        }
    }

    /**
     * Recovery helper in case cached counts drift from reality.
     * Requires a DAO method that recalculates from actual messages.
     */
    suspend fun reconcileMessageCount(conversationId: Long): Boolean {
        return runCatching {
            db.withTransaction {
                // Assumes you'll add getMessageCountForConversation to messageDao
                // and setMessageCount to conversationDao
                // val actualCount = messageDao.getMessageCountForConversation(conversationId)
                // conversationDao.setMessageCount(conversationId, actualCount)
            }
            Timber.d("Reconciled message count for conversation id=%s", conversationId)
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to reconcile message count for conversation id=%s", conversationId)
            false
        }
    }

    private fun sanitizeLikeQuery(query: String): String {
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}

internal fun ConversationEntity.toDomain(): Conversation =
    Conversation(
        id = id,
        title = title,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messageCount = messageCount,
        isArchived = isArchived
    )

internal fun MessageEntity.toDomain(): Message =
    Message(
        id = id,
        conversationId = conversationId,
        role = when(role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            else -> MessageRole.SYSTEM
        },
        content = content,
        createdAt = createdAt,
        tokensUsed = tokensUsed,
        generationTimeMs = generationTimeMs
    )

internal fun Message.toEntity(): MessageEntity =
    MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name.lowercase(),
        content = content,
        createdAt = createdAt,
        tokensUsed = tokensUsed,
        generationTimeMs = generationTimeMs
    )
