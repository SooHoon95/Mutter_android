package com.efreedom.mutter.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterOpenSummary
import com.efreedom.mutter.domain.usecase.LetterUseCase
import com.efreedom.mutter.domain.usecase.ReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LetterRow(
    val letter: Letter,
    val openSummary: LetterOpenSummary?,
    val isSent: Boolean,
) {
    val id: String get() = letter.id
    val isOpened: Boolean get() = (openSummary?.openCount ?: 0) > 0
}

data class HomeUiState(
    val rows: List<LetterRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val sentRows: List<LetterRow> get() = rows.filter { it.isSent }
    val draftRows: List<LetterRow> get() = rows.filter { !it.isSent }
    val sentCount: Int get() = sentRows.size
    val openedCount: Int get() = sentRows.count { it.isOpened }
}

/** iOS HomeModelData(LetterUseCase + ReceiptUseCase) 대응. 보낸/임시저장 편지 목록 + 읽음 통계. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val letterUseCase: LetterUseCase,
    private val receiptUseCase: ReceiptUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // coroutineScope로 감싸야 async 자식 실패가 부모(viewModelScope)로 전파돼 앱이
                // 죽지 않고, 이 try/catch에서 잡힌다(구조적 동시성 — async-in-launch 함정 회피).
                val (letters, opens) = coroutineScope {
                    val lettersDeferred = async { letterUseCase.myLettersWithStatus() }
                    val opensDeferred = async { receiptUseCase.myLetterOpens() }
                    lettersDeferred.await() to opensDeferred.await()
                }
                val openMap = opens.associateBy { it.letterId }
                val rows = letters.map { lws ->
                    LetterRow(
                        letter = lws.letter,
                        openSummary = openMap[lws.letter.id],
                        isSent = lws.isSent,
                    )
                }
                _state.value = _state.value.copy(rows = rows)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "문제가 생겼어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun delete(letterId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            try {
                letterUseCase.delete(letterId)
                _state.value = _state.value.copy(
                    rows = _state.value.rows.filter { it.letter.id != letterId },
                )
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "삭제할 수 없었어요."
                _state.value = _state.value.copy(error = message)
            }
        }
    }
}
