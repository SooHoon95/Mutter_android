package com.efreedom.mutter.feature.compose

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.audiosync.LetterAudioPlayer
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterBlocks
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.ScValidationFailReason
import com.efreedom.mutter.domain.usecase.AudioUseCase
import com.efreedom.mutter.domain.usecase.ConnectionUseCase
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterPhotoUseCase
import com.efreedom.mutter.domain.usecase.LetterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

// ─── Mode ────────────────────────────────────────────────────────────────────

/** iOS ComposeModelData.Mode 이식. */
sealed interface ComposeMode {
    data object New : ComposeMode
    data class Edit(val letterId: String) : ComposeMode
    data class Reply(val recipientId: String) : ComposeMode
}

// ─── Editor Block ────────────────────────────────────────────────────────────

/**
 * 편집기 블록. 도메인 [LetterBlock]과 달리 텍스트에도 id가 있다 — LazyColumn/key와 포커스 추적에
 * 안정적인 식별자가 필요해서다(iOS 블록 편집기 대응).
 */
sealed interface ComposeBlock {
    val id: String

    data class Text(override val id: String, val text: String) : ComposeBlock
    data class Photo(val photo: LetterPhoto) : ComposeBlock {
        override val id: String get() = photo.id
    }
}

private fun newId(): String = UUID.randomUUID().toString()

/**
 * 편집기 블록 규칙: 맨 앞은 텍스트, 사진 바로 뒤엔 텍스트(사이에 글을 쓸 수 있게),
 * 이웃한 텍스트는 하나로 합친다(사진을 지운 자리 정리). 둘 다 내용이 있으면 빈 줄로 잇는다.
 */
internal fun normalizeBlocks(blocks: List<ComposeBlock>): List<ComposeBlock> {
    val out = mutableListOf<ComposeBlock>()
    for (block in blocks) {
        val last = out.lastOrNull()
        when {
            block is ComposeBlock.Text && last is ComposeBlock.Text -> {
                val merged = listOf(last.text, block.text).filter { it.isNotEmpty() }.joinToString("\n\n")
                out[out.lastIndex] = last.copy(text = merged)
            }
            block is ComposeBlock.Photo && last !is ComposeBlock.Text -> {
                out += ComposeBlock.Text(newId(), "")
                out += block
            }
            else -> out += block
        }
    }
    if (out.isEmpty() || out.last() is ComposeBlock.Photo) out += ComposeBlock.Text(newId(), "")
    return out
}

/** 도메인 → 편집기. */
private fun List<LetterBlock>.toEditorBlocks(): List<ComposeBlock> = normalizeBlocks(
    map { block ->
        when (block) {
            is LetterBlock.Text -> ComposeBlock.Text(newId(), block.text)
            is LetterBlock.Photo -> ComposeBlock.Photo(block.photo)
        }
    },
)

/**
 * 편집기 → 도메인. 빈 텍스트 블록(사진 사이 입력 칸)은 버린다 — 저장본에 의미 없는 빈 단락이 쌓이지 않게.
 * 텍스트가 하나도 없으면 코덱이 빈 단락을 앞에 둬 큐를 지킨다.
 */
private fun List<ComposeBlock>.toDomainBlocks(): List<LetterBlock> = mapNotNull { block ->
    when (block) {
        is ComposeBlock.Text -> block.text.takeIf { it.isNotEmpty() }?.let { LetterBlock.Text(it) }
        is ComposeBlock.Photo -> LetterBlock.Photo(block.photo)
    }
}

// ─── UiState ─────────────────────────────────────────────────────────────────

data class ComposeUiState(
    val title: String = "",
    /** 본문 블록(텍스트·사진). 항상 텍스트로 시작·끝난다([normalizeBlocks]). */
    val blocks: List<ComposeBlock> = listOf(ComposeBlock.Text(newId(), "")),
    /** 이미 올라간 사진의 path → 서명 URL(이어쓰기 썸네일). 실패하면 비어 있고 플레이스홀더로 보인다. */
    val photoUrls: Map<String, String> = emptyMap(),
    /** 고른 사진을 줄이는 중(사진 넣기 버튼 잠금). */
    val isAddingPhoto: Boolean = false,
    val templateId: String = "classic-serif",
    val cue: MusicCue? = null,
    val soundcloudUrl: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedToast: Boolean = false,
    val isApplyingSoundCloud: Boolean = false,
    /** 인앱 SoundCloud 탐색 시트 표시 여부. */
    val showSoundCloudPicker: Boolean = false,
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

    val photoCount: Int get() = blocks.count { it is ComposeBlock.Photo }

    val canAddPhoto: Boolean get() = !isAddingPhoto && photoCount < LetterPhoto.MAX_PER_LETTER

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
    private val letterPhotoUseCase: LetterPhotoUseCase,
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
    // 마지막으로 저장된 사진 경로 — 다음 저장에서 "지워진 사진"을 Storage에서 정리하는 기준.
    private var savedPhotoPaths: Set<String> = emptySet()
    // 사진을 넣을 위치 — 마지막으로 포커스된 텍스트 블록 뒤(없으면 마지막 텍스트 블록 뒤).
    private var lastFocusedTextId: String? = null
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
                savedPhotoPaths = LetterBlocks.photoPaths(letter.blocks)
                _state.value = _state.value.copy(
                    title = letter.title,
                    blocks = letter.blocks.toEditorBlocks(),
                    templateId = letter.templateId,
                    cue = letter.cue,
                )
                loadPhotoUrls(mode.letterId)
                backfillCueTitleIfNeeded()
            }
        }
    }

    /** 이어쓰기 썸네일용 서명 URL(소유자 모드). 실패해도 편집은 계속된다 — 사진 자리는 플레이스홀더. */
    private suspend fun loadPhotoUrls(letterId: String) {
        if (savedPhotoPaths.isEmpty()) return
        val urls = runCatching { letterPhotoUseCase.signedUrls(letterId) }.getOrDefault(emptyMap())
        _state.value = _state.value.copy(photoUrls = urls)
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

    fun onTextBlockChange(id: String, value: String) {
        _state.value = _state.value.copy(
            blocks = _state.value.blocks.map { if (it is ComposeBlock.Text && it.id == id) it.copy(text = value) else it },
            error = null,
        )
    }

    fun onTextBlockFocused(id: String) {
        lastFocusedTextId = id
    }

    // ─── 사진 ─────────────────────────────────────────────────────────────────

    /**
     * Photo Picker로 고른 사진을 줄여(긴 변 2048·JPEG 80·EXIF 회전) 대기 사진으로 넣는다. 업로드는 저장 때.
     * 위치는 마지막으로 포커스된 텍스트 블록 바로 뒤 — 사진 다음에 빈 텍스트 블록을 붙여 이어 쓸 수 있게 한다.
     */
    fun addPhoto(uri: Uri) {
        if (!_state.value.canAddPhoto) return
        _state.value = _state.value.copy(isAddingPhoto = true, error = null)
        viewModelScope.launch {
            try {
                val processed = withContext(Dispatchers.Default) { PhotoProcessor.process(context, uri) }
                val photo = LetterPhoto(
                    id = newId(),
                    path = null,
                    width = processed.width,
                    height = processed.height,
                    pendingJpeg = processed.jpeg,
                )
                val blocks = _state.value.blocks
                val anchor = blocks.indexOfFirst { it is ComposeBlock.Text && it.id == lastFocusedTextId }
                    .takeIf { it >= 0 }
                    ?: blocks.indexOfLast { it is ComposeBlock.Text }
                val inserted = blocks.toMutableList().apply {
                    addAll(anchor + 1, listOf(ComposeBlock.Photo(photo), ComposeBlock.Text(newId(), "")))
                }
                _state.value = _state.value.copy(blocks = normalizeBlocks(inserted))
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = context.getString(R.string.compose_photo_failed))
            } finally {
                _state.value = _state.value.copy(isAddingPhoto = false)
            }
        }
    }

    /** 사진 블록 삭제. Storage 객체는 다음 저장 때 지운다(저장 전 취소하면 원본이 그대로 남아야 하므로). */
    fun removePhoto(photoId: String) {
        _state.value = _state.value.copy(
            blocks = normalizeBlocks(_state.value.blocks.filterNot { it is ComposeBlock.Photo && it.id == photoId }),
        )
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

    fun setShowSoundCloudPicker(show: Boolean) {
        _state.value = _state.value.copy(showSoundCloudPicker = show)
    }

    /** 인앱 탐색 시트에서 고른 트랙을 붙여넣기와 같은 검증 경로로 적용한다. */
    fun applyPickedSoundCloud(url: String) {
        _state.value = _state.value.copy(showSoundCloudPicker = false, soundcloudUrl = url)
        applySoundCloudUrl()
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
     * 저장(LetterUseCase.saveWithPhotos — 생성 → 사진 업로드 → update → 지운 사진 정리). 첫 생성 시 mode를 edit로 승격.
     * 성공 시 letterId 반환, 실패 시 null.
     */
    private suspend fun save(): String? {
        _state.value = _state.value.copy(isSaving = true, error = null)
        return try {
            val st = _state.value
            val draft = LetterDraft(
                title = st.title,
                blocks = st.blocks.toDomainBlocks(),
                templateId = st.templateId,
                cue = st.cue,
            )
            val saved = letterUseCase.saveWithPhotos(
                letterId = (currentMode as? ComposeMode.Edit)?.letterId,
                draft = draft,
                previousPhotoPaths = savedPhotoPaths,
                // 생성 직후 승격 — 사진 업로드가 실패해도 다음 저장이 새 편지를 또 만들지 않게.
                onCreated = { currentMode = ComposeMode.Edit(it) },
            )
            applySavedPhotos(saved.blocks)
            saved.id
        } catch (e: Throwable) {
            val message = (e as? MutterError)?.userMessage ?: "저장할 수 없었어요. 다시 시도해 주세요."
            _state.value = _state.value.copy(error = message)
            null
        } finally {
            _state.value = _state.value.copy(isSaving = false)
        }
    }

    /**
     * 업로드로 받은 path를 편집기 사진에 반영한다(다음 저장이 다시 올리지 않게). 대기 바이트는 남겨 둔다 —
     * 새로 올린 사진은 서명 URL이 없어 그 바이트로 계속 미리보기한다(저장소엔 다시 안 올라감: path가 있으므로).
     */
    private fun applySavedPhotos(savedBlocks: List<LetterBlock>) {
        val paths = LetterBlocks.photos(savedBlocks).associate { it.id to it.path }
        savedPhotoPaths = LetterBlocks.photoPaths(savedBlocks)
        _state.value = _state.value.copy(
            blocks = _state.value.blocks.map { block ->
                if (block is ComposeBlock.Photo && block.photo.path == null) {
                    paths[block.id]?.let { ComposeBlock.Photo(block.photo.copy(path = it)) } ?: block
                } else {
                    block
                }
            },
        )
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
