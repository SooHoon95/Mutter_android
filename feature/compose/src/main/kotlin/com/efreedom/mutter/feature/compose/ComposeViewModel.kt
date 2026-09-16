package com.efreedom.mutter.feature.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.audiosync.LetterAudioPlayer
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.ScValidationFailReason
import com.efreedom.mutter.domain.usecase.AudioUseCase
import com.efreedom.mutter.domain.usecase.ConnectionUseCase
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Mode ────────────────────────────────────────────────────────────────────

/** iOS ComposeModelData.Mode 이식. */
sealed interface ComposeMode {
    data object New : ComposeMode
    data class Edit(val letterId: String) : ComposeMode
    data class Reply(val recipientId: String) : ComposeMode
}

// ─── UiState ─────────────────────────────────────────────────────────────────

data class ComposeUiState(
    val title: String = "",
    val content: String = "",
    val templateId: String = "classic-serif",
    val cue: MusicCue? = null,
    val soundcloudUrl: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedToast: Boolean = false,
    val isApplyingSoundCloud: Boolean = false,
    val isReply: Boolean = false,
    // 보내기 시트
    val showSendSheet: Boolean = false,
    val sentLetterId: String? = null,
    val usePassword: Boolean = true,
    val password: String = "",
    /** 발급된 전달 링크. 암호·예약 여부가 공유 문구에 쓰인다. URL은 [issuedLinkUrl]로 파생. */
    val issuedLink: DeliveryLink? = null,
    val connections: List<Connection> = emptyList(),
    val isSending: Boolean = false,
    val isIssuing: Boolean = false,
    val navigateUp: Boolean = false,
) {
    val issuedLinkUrl: String? get() = issuedLink?.let { AppLink.letter(it.token) }

    /** 전달 링크 발급 가능 여부(암호 ON이면 암호 입력 필수). */
    val canIssueLink: Boolean get() = if (usePassword) password.isNotEmpty() else true

    /** 현재 선택된 음악 이름(명사구). null이면 미선택. */
    val appliedCueLabel: String?
        get() = cue?.let { c ->
            when (c.source) {
                MusicSource.SOUNDCLOUD -> c.title?.let { "'$it'" } ?: "사운드클라우드 트랙"
                MusicSource.HOSTED -> "기본 음악"
            }
        }
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

/**
 * iOS ComposeModelData 대응. 편지 제작(신규·이어쓰기·답장) + 보내기 시트.
 * 첫 저장 후 new/reply → edit(id) 승격으로 중복 생성을 방지한다.
 */
@HiltViewModel
class ComposeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val letterUseCase: LetterUseCase,
    private val connectionUseCase: ConnectionUseCase,
    private val deliveryUseCase: DeliveryUseCase,
    private val audioUseCase: AudioUseCase,
) : ViewModel() {

    /** Compose mutableStateOf 관찰 — 화면이 isPlaying/isReady/isUnavailable/currentSource를 읽는다. */
    val player = LetterAudioPlayer(context, audioUseCase)

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    // 저장 후 new→edit 승격(중복 생성 방지). start()로 한 번 초기화된다.
    private var currentMode: ComposeMode? = null
    // 답장 대상 — mode 승격과 무관하게 유지.
    private var replyRecipientId: String? = null
    private var isPreparingPreview = false
    private var started = false

    // ─── 초기화 ───────────────────────────────────────────────────────────────

    /**
     * 화면 LaunchedEffect(Unit)에서 정확히 한 번 호출(재진입 방지).
     * iOS ComposeModelData.init + load() 역할.
     */
    fun start(mode: ComposeMode) {
        if (started) return
        started = true
        currentMode = mode
        if (mode is ComposeMode.Reply) {
            replyRecipientId = mode.recipientId
            _state.value = _state.value.copy(isReply = true)
        }
        if (mode is ComposeMode.Edit) {
            viewModelScope.launch {
                val letter = letterUseCase.letter(mode.letterId) ?: return@launch
                _state.value = _state.value.copy(
                    title = letter.title,
                    content = letter.body,
                    templateId = letter.templateId,
                    cue = letter.cue,
                )
                backfillCueTitleIfNeeded()
            }
        }
    }

    /**
     * 이어쓰기로 열 때, 제목 없이 저장된 레거시 SC 큐를 oEmbed로 보강한다.
     * 다음 저장 때 제목이 지속돼 자가치유된다.
     */
    private suspend fun backfillCueTitleIfNeeded() {
        val cue = _state.value.cue ?: return
        if (cue.source != MusicSource.SOUNDCLOUD || cue.title != null) return
        val result = audioUseCase.validateSoundCloud(cue.ref)
        if (result is ScValidation.Ok) {
            _state.value = _state.value.copy(
                cue = cue.copy(
                    title = result.title.takeIf { it.isNotEmpty() },
                    author = result.author.takeIf { it.isNotEmpty() },
                )
            )
        }
    }

    // ─── 필드 변경 ────────────────────────────────────────────────────────────

    fun onTitleChange(value: String) {
        _state.value = _state.value.copy(title = value, error = null)
    }

    fun onContentChange(value: String) {
        _state.value = _state.value.copy(content = value, error = null)
    }

    fun onTemplateChange(templateId: String) {
        _state.value = _state.value.copy(templateId = templateId)
    }

    fun onSoundcloudUrlChange(value: String) {
        _state.value = _state.value.copy(soundcloudUrl = value)
    }

    // ─── SoundCloud ───────────────────────────────────────────────────────────

    /**
     * SC URL 검증 후 큐 적용 — oEmbed canonical URL로 변환해 저장,
     * 비공개·임베드 금지 트랙은 그 자리에서 거른다. 미리듣기 준비는 재생 탭 시점으로 미룬다.
     */
    fun applySoundCloudUrl() {
        val url = _state.value.soundcloudUrl.trim()
        if (url.isEmpty() || _state.value.isApplyingSoundCloud) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isApplyingSoundCloud = true, error = null)
            try {
                when (val result = audioUseCase.validateSoundCloud(url)) {
                    is ScValidation.Ok -> {
                        val cue = MusicCue(
                            source = MusicSource.SOUNDCLOUD,
                            ref = result.canonicalUrl,
                            startMs = 0,
                            title = result.title.takeIf { it.isNotEmpty() },
                            author = result.author.takeIf { it.isNotEmpty() },
                            sourceUrl = url,
                        )
                        // 큐만 적용하고 미리듣기 준비(WebView 스핀업)는 미룬다 —
                        // iOS 대응: 재생을 누를 때 previewAudio가 prepare한다(불필요한 즉시 버퍼링 방지).
                        _state.value = _state.value.copy(cue = cue, soundcloudUrl = "")
                    }
                    is ScValidation.Fail -> {
                        _state.value = _state.value.copy(error = scErrorMessage(result.reason))
                    }
                }
            } finally {
                _state.value = _state.value.copy(isApplyingSoundCloud = false)
            }
        }
    }

    // ─── 미리듣기 ─────────────────────────────────────────────────────────────

    /**
     * 재생 중이면 일시정지, 아니면 준비 후 재생.
     * 준비 중 중복 탭은 무시(소스 중복 로드 레이스 방지).
     */
    fun previewAudio() {
        val cue = _state.value.cue ?: return
        if (player.isPlaying) { player.pause(); return }
        if (isPreparingPreview) return
        viewModelScope.launch(Dispatchers.Main) {
            isPreparingPreview = true
            try {
                player.prepare(cue)
                player.play()
            } finally {
                isPreparingPreview = false
            }
        }
    }

    // ─── 저장 ─────────────────────────────────────────────────────────────────

    /**
     * 저장(이어쓰기면 update, 아니면 create). 첫 저장 후 mode를 edit로 승격.
     * 성공 시 letterId 반환, 실패 시 null.
     */
    private suspend fun save(): String? {
        _state.value = _state.value.copy(isSaving = true, error = null)
        return try {
            val st = _state.value
            val draft = LetterDraft(
                title = st.title,
                body = st.content,
                templateId = st.templateId,
                cue = st.cue,
            )
            when (val m = currentMode) {
                is ComposeMode.Edit -> {
                    letterUseCase.update(m.letterId, draft)
                    m.letterId
                }
                is ComposeMode.New, is ComposeMode.Reply, null -> {
                    val letter = letterUseCase.create(draft)
                    // 이후 저장은 갱신 — 반복 저장 시 중복 생성 방지.
                    currentMode = ComposeMode.Edit(letter.id)
                    letter.id
                }
            }
        } catch (e: Throwable) {
            val message = (e as? MutterError)?.userMessage ?: "저장할 수 없었어요. 다시 시도해 주세요."
            _state.value = _state.value.copy(error = message)
            null
        } finally {
            _state.value = _state.value.copy(isSaving = false)
        }
    }

    /** 저장 후 닫기. navigateUp = true로 화면이 onDone을 호출하도록 신호를 보낸다. */
    fun saveAndClose() {
        viewModelScope.launch {
            if (save() != null) {
                _state.value = _state.value.copy(savedToast = true, navigateUp = true)
            }
        }
    }

    /**
     * 답장 발송 — 저장 후 연결 상대면 직접 전송, 비연결이면 전달 링크 시트로 폴백.
     * iOS ComposeModelData.sendReply() 이식.
     */
    fun sendReply() {
        val recipientId = replyRecipientId ?: return
        viewModelScope.launch {
            val letterId = save() ?: return@launch
            val connected = runCatching { connectionUseCase.myConnections() }.getOrDefault(emptyList())
            if (connected.any { it.userId == recipientId }) {
                try {
                    connectionUseCase.send(letterId, recipientId)
                    _state.value = _state.value.copy(navigateUp = true)
                } catch (e: Throwable) {
                    val message = (e as? MutterError)?.userMessage ?: "보낼 수 없었어요. 다시 시도해 주세요."
                    _state.value = _state.value.copy(error = message)
                }
            } else {
                // 비연결 상대 — 전달 링크로 폴백.
                _state.value = _state.value.copy(
                    sentLetterId = letterId,
                    issuedLink = null,
                    password = "",
                    usePassword = true,
                    connections = connected,
                    showSendSheet = true,
                )
            }
        }
    }

    // ─── 보내기 시트 ──────────────────────────────────────────────────────────

    /** 저장 후 보내기 시트 열기(새 편지/이어쓰기). 연결 상대를 미리 불러온다. */
    fun saveAndOpenSend() {
        viewModelScope.launch {
            val id = save() ?: return@launch
            val connections = runCatching { connectionUseCase.myConnections() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                sentLetterId = id,
                issuedLink = null,
                password = "",
                usePassword = true,
                error = null,
                connections = connections,
                showSendSheet = true,
            )
        }
    }

    /** 전달 링크 발급. 암호 ON이면 password 필수. */
    fun issueLink() {
        val id = _state.value.sentLetterId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isIssuing = true, error = null)
            try {
                val link = deliveryUseCase.issue(
                    letterId = id,
                    password = if (_state.value.usePassword) _state.value.password else null,
                    revealAt = null,
                )
                _state.value = _state.value.copy(
                    issuedLink = link,
                    password = "",
                )
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "링크를 만들 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message)
            } finally {
                _state.value = _state.value.copy(isIssuing = false)
            }
        }
    }

    /** 연결된 상대에게 링크 없이 직접 발송. 성공 시 finishSend(). */
    fun sendToConnection(recipientId: String) {
        val id = _state.value.sentLetterId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true, error = null)
            try {
                connectionUseCase.send(id, recipientId)
                finishSend()
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "보낼 수 없었어요. 다시 시도해 주세요."
                _state.value = _state.value.copy(error = message, isSending = false)
            }
        }
    }

    /** 보내기 완료 — 시트 닫고 제작 화면도 닫는다. */
    fun finishSend() {
        _state.value = _state.value.copy(showSendSheet = false, navigateUp = true, isSending = false)
    }

    // ─── 시트 바인딩 헬퍼 ────────────────────────────────────────────────────

    fun setShowSendSheet(visible: Boolean) {
        _state.value = _state.value.copy(showSendSheet = visible)
    }

    fun setUsePassword(value: Boolean) {
        _state.value = _state.value.copy(usePassword = value)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun scErrorMessage(reason: ScValidationFailReason): String = when (reason) {
        ScValidationFailReason.INVALID_URL -> "유효하지 않은 URL이에요."
        ScValidationFailReason.NETWORK -> "네트워크를 확인해 주세요."
        ScValidationFailReason.PRIVATE_TRACK -> "비공개 트랙은 사용할 수 없어요."
        ScValidationFailReason.NOT_FOUND -> "트랙을 찾을 수 없어요."
        ScValidationFailReason.EMBED_DISABLED -> "임베드가 허용되지 않은 트랙이에요."
    }

}
