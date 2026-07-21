package com.efreedom.mutter.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.usecase.ProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NicknameUiState(
    val nickname: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/**
 * 가입 직후 닉네임 온보딩 ViewModel (iOS NicknameModelData 이식).
 *
 * [save]에서 [ProfileUseCase.updateNickname]을 호출할 때 비로소 프로필(회원가입)이 생성된다.
 * 그 전에는 어떤 회원가입/프로필 쓰기도 하지 않는다.
 */
@HiltViewModel
class NicknameViewModel @Inject constructor(
    private val profileUseCase: ProfileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(NicknameUiState())
    val state: StateFlow<NicknameUiState> = _state.asStateFlow()

    val isValid: Boolean get() = _state.value.nickname.trim().isNotEmpty()

    fun onNicknameChange(value: String) {
        _state.value = _state.value.copy(nickname = value, error = null)
    }

    fun save() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                profileUseCase.updateNickname(_state.value.nickname)
                _state.value = _state.value.copy(done = true)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "닉네임을 저장하지 못했어요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
