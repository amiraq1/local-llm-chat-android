package com.example.localllm.accessibility

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AccessibilityAgentStateStore @Inject constructor() {

    private val _screenState = MutableStateFlow(ParsedScreenState())
    val screenState: StateFlow<ParsedScreenState> = _screenState.asStateFlow()

    fun update(snapshot: ParsedScreenState) {
        _screenState.value = snapshot
    }

    fun clear() {
        _screenState.value = ParsedScreenState()
    }
}
