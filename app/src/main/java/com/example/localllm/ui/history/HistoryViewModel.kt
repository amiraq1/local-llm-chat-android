package com.example.localllm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllm.data.repository.ConversationRepository
import com.example.localllm.domain.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val filteredConversations: List<Conversation> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            conversationRepository.getAllConversations().collect { conversations ->
                _state.update { state ->
                    state.copy(
                        conversations = conversations,
                        filteredConversations = applySearch(conversations, state.searchQuery)
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { state ->
            state.copy(
                searchQuery = query,
                filteredConversations = applySearch(state.conversations, query)
            )
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            try {
                conversationRepository.deleteConversation(id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete conversation $id")
            }
        }
    }

    fun archiveConversation(id: Long) {
        viewModelScope.launch {
            conversationRepository.archiveConversation(id)
        }
    }

    private fun applySearch(list: List<Conversation>, query: String): List<Conversation> =
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
}
