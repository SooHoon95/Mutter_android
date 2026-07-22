package com.efreedom.mutter.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DeliveryUiState(
    val links: List<DeliveryLink> = emptyList(),
    /** 암호 기본 ON (기본값이 프라이버시). */
    val usePassword: Boolean = true,
    val password: String = "",
    val useReveal: Boolean = false,
    val revealAt: Instant = Instant.now().plusSeconds(3600),
    val isLoading: Boolean = false,
    /** 목록 새로고침(당겨서 새로고침) 진행 표시 — 발급/취소 버튼의 isLoading과 분리. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val lastIssuedToken: String? = null,
) {
    val canIssue: Boolean get() = if (usePassword) password.isNotEmpty() else true
}

/** iOS DeliveryModelData 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val deliveryUseCase: DeliveryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state.asStateFlow()

    private var letterId: String = ""

    /** Screen의 LaunchedEffect에서 한 번 호출. */
    fun start(letterId: String) {
        if (this.letterId == letterId) return
        this.letterId = letterId
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val links = deliveryUseCase.links(letterId)
                _state.value = _state.value.copy(links = links)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    fun issue() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val st = _state.value
                val link = deliveryUseCase.issue(
                    letterId = letterId,
                    password = if (st.usePassword) st.password else null,
                    revealAt = if (st.useReveal) st.revealAt else null,
                )
                _state.value = _state.value.copy(
                    lastIssuedToken = link.token,
                    password = "",
                )
                load()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "발급할 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun revoke(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                deliveryUseCase.revoke(token)
                load()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "취소할 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun setUsePassword(value: Boolean) {
        _state.value = _state.value.copy(usePassword = value)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    fun setUseReveal(value: Boolean) {
        _state.value = _state.value.copy(useReveal = value)
    }

    fun setRevealAt(value: Instant) {
        _state.value = _state.value.copy(revealAt = value)
    }
}
