package com.example.localllm.data.repository

import com.example.localllm.data.db.AppDatabase
import com.example.localllm.data.db.dao.ConversationDao
import com.example.localllm.data.db.dao.MessageDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConversationRepositoryTest {

    private val db = mockk<AppDatabase>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val repository = ConversationRepository(db, conversationDao, messageDao)

    @Test
    fun `searchMessages escapes LIKE wildcards before delegating to Room`() = runTest {
        coEvery { messageDao.searchMessages("50\\%\\_off\\\\") } returns emptyList()

        repository.searchMessages("50%_off\\")

        coVerify(exactly = 1) {
            messageDao.searchMessages("50\\%\\_off\\\\")
        }
    }
}
