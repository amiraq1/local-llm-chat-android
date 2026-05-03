package com.example.localllm.ui.history

import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.domain.model.Conversation
import com.example.localllm.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ConversationRepository>()

    @Test
    fun `search query filters conversations by title case-insensitively`() = runTest {
        val conversations = listOf(
            Conversation(id = 1, title = "محادثة الذكاء الاصطناعي", modelId = "model-a"),
            Conversation(id = 2, title = "رحلة نهاية الأسبوع", modelId = "model-b")
        )
        every { repository.getAllConversations() } returns flowOf(conversations)

        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("الذكاء")

        val state = viewModel.state.value
        assertEquals("الذكاء", state.searchQuery)
        assertEquals(1, state.filteredConversations.size)
        assertEquals(1L, state.filteredConversations.single().id)
    }

    @Test
    fun `delete and archive delegate to repository`() = runTest {
        every { repository.getAllConversations() } returns flowOf(emptyList<Conversation>())
        coEvery { repository.deleteConversation(7L) } returns true
        coEvery { repository.archiveConversation(9L) } returns true

        val viewModel = HistoryViewModel(repository)

        viewModel.deleteConversation(7L)
        viewModel.archiveConversation(9L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteConversation(7L) }
        coVerify(exactly = 1) { repository.archiveConversation(9L) }
        assertTrue(viewModel.state.value.filteredConversations.isEmpty())
    }
}
