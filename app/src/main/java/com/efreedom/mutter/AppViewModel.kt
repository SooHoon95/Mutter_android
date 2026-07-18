package com.efreedom.mutter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.usecase.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 앱 루트 세션 상태(iOS SessionManager 최소판). null=확인 중. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authUseCase: AuthUseCase,
) : ViewModel() {

    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    val loggedIn: StateFlow<Boolean?> = _loggedIn.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loggedIn.value = runCatching { authUseCase.currentSession() != null }.getOrDefault(false)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authUseCase.signOut() }
            refresh()
        }
    }
}
