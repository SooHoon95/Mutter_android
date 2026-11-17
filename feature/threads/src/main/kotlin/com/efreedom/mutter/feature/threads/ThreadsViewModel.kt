package com.efreedom.mutter.feature.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.ThreadLetter
import com.efreedom.mutter.domain.usecase.ThreadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreadsUiState(
    val counterparts: List<Counterpart> = emptyList(),
    val selectedCounterpart: Counterpart? = null,
    val thread: List<ThreadLetter> = emptyList(),
    val isLoading: Boolean = false,
    /** 스레드 시트 로드 중 — 빈 시트 대신 로딩 스피너를 표시하기 위함. */
    val isThreadLoading: Boolean = false,
    /** 스레드 로드 실패 — 에러를 삼키지 않고 시트에 드러낸다(문구는 iOS처럼 항상 같아 화면이 리소스로 그린다). */
    val threadLoadFailed: Boolean = false,
)

/** iOS ThreadsModelData 대응. 스레드 탭 상대 목록 + 선택 상대와의 편지 흐름. */
@HiltViewModel
class ThreadsViewModel @Inject constructor(
    private val threadUseCase: ThreadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ThreadsUiState())
    val state: StateFlow<ThreadsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val counterparts = threadUseCase.counterparts()
                _state.value = _state.value.copy(counterparts = counterparts)
            } catch (e: Throwable) {
                // 목록 로드 실패는 빈 목록으로 폴백(재시도는 pull-to-refresh).
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun openThread(counterpart: Counterpart) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedCounterpart = counterpart,
                thread = emptyList(),
                threadLoadFailed = false,
                isThreadLoading = true,
            )
            try {
                val thread = threadUseCase.thread(counterpart.userId)
                _state.value = _state.value.copy(thread = thread)
            } catch (e: Throwable) {
                // 실패를 삼키지 않고 시트에 에러 상태로 드러낸다(빈 시트 방지).
                _state.value = _state.value.copy(threadLoadFailed = true)
            } finally {
                _state.value = _state.value.copy(isThreadLoading = false)
            }
        }
    }

    fun closeThread() {
        _state.value = _state.value.copy(
            selectedCounterpart = null,
            thread = emptyList(),
            threadLoadFailed = false,
        )
    }
}
