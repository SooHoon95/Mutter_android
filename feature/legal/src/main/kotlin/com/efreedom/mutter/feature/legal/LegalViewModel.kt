package com.efreedom.mutter.feature.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.usecase.TakedownUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TakedownUiState(
    val claimant: String = "",
    val contact: String = "",
    val reason: String = "",
    val trackRef: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
) {
    val isValid: Boolean
        get() = claimant.isNotBlank() && contact.isNotBlank() && reason.isNotBlank()
}

/** iOS TakedownModelData 대응. TakedownUseCase 주입. */
@HiltViewModel
class LegalViewModel @Inject constructor(
    private val takedownUseCase: TakedownUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TakedownUiState())
    val state: StateFlow<TakedownUiState> = _state.asStateFlow()

    fun onClaimantChange(value: String) {
        _state.value = _state.value.copy(claimant = value, error = null)
    }

    fun onContactChange(value: String) {
        _state.value = _state.value.copy(contact = value, error = null)
    }

    fun onReasonChange(value: String) {
        _state.value = _state.value.copy(reason = value, error = null)
    }

    fun onTrackRefChange(value: String) {
        _state.value = _state.value.copy(trackRef = value, error = null)
    }

    fun submit() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                takedownUseCase.report(
                    letterId = null,
                    trackRef = s.trackRef.trim().ifEmpty { null },
                    claimant = s.claimant.trim(),
                    contact = s.contact.trim(),
                    reason = s.reason.trim(),
                )
                _state.value = _state.value.copy(submitted = true)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "신고 접수 중 오류가 발생했어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }
}
