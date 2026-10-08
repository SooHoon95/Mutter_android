package com.efreedom.mutter.feature.viewer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.audiosync.LetterAudioPlayer
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.LetterBlocks
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.usecase.AudioUseCase
import com.efreedom.mutter.domain.usecase.AuthUseCase
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterPhotoUseCase
import com.efreedom.mutter.domain.usecase.LetterUseCase
import com.efreedom.mutter.domain.usecase.ReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// ─── State ───────────────────────────────────────────────────────────────────

sealed interface ViewerState {
    /** 로딩 중 */
    object Loading : ViewerState

    /** 암호 필요(미입력 또는 틀림) */
    object PasswordRequired : ViewerState

    /** 예약공개 전 — [revealAt] 시각에 열림 */
    data class RevealPending(val revealAt: Instant) : ViewerState

    /** 편지 로드 완료 — 게이트(▶) 통과 전/후 구분은 [ViewerViewModel.isOpened]로 한다 */
    data class Ready(val payload: LetterPayload) : ViewerState

    /** 복구 불가 오류 */
    data class Failed(val message: String) : ViewerState
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

/**
 * 수신/열람 플로우 ViewModel (iOS ViewerModelData 이식).
 *
 * 딥링크 토큰([Source.Token]) 또는 내 편지 미리보기([Source.MyLetter])를 지원한다.
 * [start]를 LaunchedEffect에서 한 번 호출해 로드를 시작한다.
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val deliveryUseCase: DeliveryUseCase,
    private val receiptUseCase: ReceiptUseCase,
    private val letterUseCase: LetterUseCase,
    private val authUseCase: AuthUseCase,
    private val letterPhotoUseCase: LetterPhotoUseCase,
    audioUseCase: AudioUseCase,
) : ViewModel() {

    /** 데이터 출처 — 화면 진입 시 결정된다. */
    sealed interface Source {
        data class Token(val token: String) : Source
        data class MyLetter(val letterId: String) : Source
    }

    /** 편지 1통의 오디오 플레이어. Compose state 기반이므로 UI에서 직접 관찰한다. */
    val player = LetterAudioPlayer(context, audioUseCase)

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    /** "열기 ▶" 게이트 통과 여부 — mutableStateOf로 Compose 리컴포즈 트리거. */
    var isOpened by mutableStateOf(false)
        private set

    /**
     * 받은함 자동 저장 표시 — 인증된 사용자가 토큰 링크(/l/)를 열면 서버가 get_letter_by_token
     * 사이드이펙트로 받은함에 자동 저장한다(마이그레이션 0022). iOS savedToInbox 대응 — 저장 API를
     * 따로 호출하지 않고 인증 + 토큰 수신 + 로드 성공일 때만 표시한다.
     */
    var savedToInbox by mutableStateOf(false)
        private set

    /**
     * 사진 path → 서명 URL(1시간 유효). 본문을 받은 직후 같은 모드로 한 번만 받는다.
     * 실패하면 비어 있고 사진 자리는 플레이스홀더로 남는다 — 본문 표시는 이것과 무관하다.
     */
    var photoUrls by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var source: Source? = null
    private var password: String? = null

    /**
     * 로드를 시작한다. LaunchedEffect에서 한 번만 호출한다(중복 호출 무시).
     * ViewModel 생명주기가 유지되는 한 재호출해도 안전하다.
     */
    fun start(src: Source) {
        if (source != null) return
        source = src
        load()
    }

    /** 암호 입력 후 재시도. */
    fun submitPassword(pw: String) {
        password = pw
        load()
    }

    /**
     * "열기 ▶" 버튼 탭 — 사용자 제스처 컨텍스트에서 호출해야 한다(SoundCloud 오디오 언락).
     * [player.start]를 동기 호출한 뒤, 토큰 수신이면 읽음 기록을 fire-and-forget으로 전송한다.
     */
    fun open() {
        isOpened = true
        player.start()
        (source as? Source.Token)?.let { s ->
            viewModelScope.launch {
                runCatching { receiptUseCase.recordOpen(s.token) }
            }
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun load() {
        viewModelScope.launch {
            _state.value = ViewerState.Loading
            when (val src = source ?: return@launch) {
                is Source.Token -> loadByToken(src.token)
                is Source.MyLetter -> loadMyLetter(src.letterId)
            }
        }
    }

    private suspend fun loadByToken(token: String) {
        runCatching { deliveryUseCase.open(token, password) }
            .onSuccess {
                present(it)
                // 로그인 상태면 서버가 받은함에 자동 저장했으므로 표시(익명 수신자는 표시 안 함).
                if (runCatching { authUseCase.currentSession() }.getOrNull() != null) {
                    savedToInbox = true
                }
            }
            .onFailure { e ->
                _state.value = when (e) {
                    is MutterError.WrongPassword -> ViewerState.PasswordRequired
                    is MutterError.LinkNotYetRevealed -> ViewerState.RevealPending(e.revealAt)
                    is MutterError -> ViewerState.Failed(e.userMessage)
                    else -> ViewerState.Failed("편지를 불러오지 못했어요.")
                }
            }
    }

    private suspend fun loadMyLetter(letterId: String) {
        runCatching { letterUseCase.letter(letterId) }
            .onSuccess { letter ->
                if (letter == null) {
                    _state.value = ViewerState.Failed("편지를 찾을 수 없어요.")
                    return
                }
                present(
                    LetterPayload(
                        id = letter.id,
                        title = letter.title,
                        blocks = letter.blocks,
                        templateId = letter.templateId,
                        cue = letter.cue,
                        audioDisabled = false,
                    )
                )
            }
            .onFailure { e ->
                _state.value = when (e) {
                    is MutterError -> ViewerState.Failed(e.userMessage)
                    else -> ViewerState.Failed("편지를 불러오지 못했어요.")
                }
            }
    }

    /** 수신 링크면 토큰(+입력한 암호), 내 편지 미리보기면 letterId(사용자 JWT)로 서명 URL을 받는다. */
    private suspend fun loadPhotoUrls() {
        val urls = runCatching {
            when (val src = source) {
                is Source.Token -> letterPhotoUseCase.signedUrls(src.token, password)
                is Source.MyLetter -> letterPhotoUseCase.signedUrls(src.letterId)
                null -> emptyMap()
            }
        }.getOrDefault(emptyMap())
        photoUrls = urls
    }

    /**
     * 상태를 Ready로 전환하고, 오디오가 활성화된 편지라면 큐를 준비한다.
     * SoundCloud 소스는 [player.prepare] 이전에 [player.currentSource] 가 노출되고,
     * 화면이 Attachment()를 Compose 트리에 배치한 뒤 load()가 완료된다.
     */
    private suspend fun present(payload: LetterPayload) {
        _state.value = ViewerState.Ready(payload)
        // 오디오 준비(prepare)는 오래 걸릴 수 있어 서명 URL 요청을 따로 띄운다 — 사진이 음악을 기다리지 않게.
        if (LetterBlocks.photoPaths(payload.blocks).isNotEmpty()) {
            viewModelScope.launch { loadPhotoUrls() }
        }
        val cue = payload.cue
        if (!payload.audioDisabled && cue != null) {
            player.prepare(cue)
        }
    }
}
