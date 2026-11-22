package com.nofollow.app.features.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofollow.app.features.auth.domain.usecase.SaveSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val saveSession: SaveSessionUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onCookies(cookieHeader: String?) {
        if (_state.value.isSaving || _state.value.loggedIn) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val saved = saveSession(cookieHeader)
            _state.update { it.copy(isSaving = false, loggedIn = saved) }
        }
    }
}