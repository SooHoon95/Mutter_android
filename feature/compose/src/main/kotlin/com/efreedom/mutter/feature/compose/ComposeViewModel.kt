package com.efreedom.mutter.feature.compose

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efreedom.mutter.core.audiosync.LetterAudioPlayer
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.analytics.Analytics
import com.efreedom.mutter.domain.analytics.AnalyticsEvent
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.error.LetterPhotoSaveException
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Connection
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.Entitlements
import com.efreedom.mutter.domain.model.LetterAccessKind
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterBlocks
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.domain.model.ScValidation
import com.efreedom.mutter.domain.model.ScValidationFailReason
import com.efreedom.mutter.domain.model.SendChoice
import com.efreedom.mutter.domain.usecase.AudioUseCase
import com.efreedom.mutter.domain.usecase.ConnectionUseCase
import com.efreedom.mutter.domain.usecase.DeliveryUseCase
import com.efreedom.mutter.domain.usecase.LetterAccessUseCase
import com.efreedom.mutter.domain.usecase.LetterPhotoUseCase
import com.efreedom.mutter.domain.usecase.LetterUseCase
import com.efreedom.mutter.domain.usecase.TicketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

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
    // 보내기 전 선택(영구 / 무료 기간) — 시트를 열 때 받는다. 조회 실패면 null → 카드 없이 기존 시트.
    val sendAccess: LetterAccessState? = null,
    val entitlements: Entitlements? = null,
    /** 보낼 편지에 이미 발급된 링크가 있다(이미 보낸 편지). */
    val sendHasLinks: Boolean = false,
    /** 시트에서 무료 기간을 골랐다. 시트를 다시 열면 다시 묻는다. */
    val choseFreeDays: Boolean = false,
    /** 시트에서 영구 편지를 골랐다 — **고른 것만 기억한다.** 편지권은 링크를 만들거나 보낼 때 서버가 쓴다. */
    val chosePermanent: Boolean = false,
    /** 결제 안내 시트(구매) — 영구 편지를 골랐는데 편지권이 없을 때 보내기 시트 위로 띄운다(null이면 닫힘). */
    val checkoutRoute: TicketCheckoutRoute? = null,
    val navigateUp: Boolean = false,
) {
    /** 보내기 전 선택 카드. 보이는 동안 링크·연결 탭을 숨긴다. */
    val showsSendChoice: Boolean
        get() = SendChoice.shows(
            sendAccess,
            entitlements,
            hasLinks = sendHasLinks,
            hasChosen = choseFreeDays || chosePermanent,
        )

    /** 영구 편지를 골랐고 아직 보내지 않았다 — 발급·발송 위에 "보낼 때 편지권 1장이 사용돼요"와 "다시 고르기"를 보인다. */
    val showsPermanentPending: Boolean
        get() = chosePermanent && sendAccess?.kind == LetterAccessKind.OPEN && !sendHasLinks && issuedLink == null

    /** 영구로 만든 뒤 시트 위에 "영구" 표시. 편지권 UI가 보이는 계정에만(목록 표시 규칙과 같음). */
    val showsPermanentLabel: Boolean
        get() = sendAccess?.kind == LetterAccessKind.PERMANENT && entitlements?.showsTicketUi == true

    /**
     * 보내기 시트 안내("받는 분이 처음 연 날부터 N일 동안 열려요. 닫힌 뒤 M일이 지나면 삭제돼요.") — 무료 기간으로 보내는 편지에만.
     * 영구 편지를 골랐으면 없다. 일수는 서버 값만 쓰므로 권한을 못 받았으면 안내도 없다. 계약 "삭제 안내".
     */
    val freeDaysNotice: Entitlements?
        get() = entitlements?.takeIf { sendAccess?.kind == LetterAccessKind.OPEN && !chosePermanent }

    val issuedLinkUrl: String? get() = issuedLink?.let { AppLink.letter(it.token) }

    val photoCount: Int get() = blocks.count { it is ComposeBlock.Photo }

    /** 더 넣을 수 있는 사진 수(iOS remainingPhotoSlots). 선택기 한도와 한도 안내에 쓴다. */
    val remainingPhotoSlots: Int get() = max(0, LetterPhoto.MAX_PER_LETTER - photoCount)

    val canAddPhoto: Boolean get() = !isAddingPhoto && photoCount < LetterPhoto.MAX_PER_LETTER

    /** 전달 링크 발급 가능 여부(암호 ON이면 암호 입력 필수). */
    val canIssueLink: Boolean get() = if (usePassword) password.isNotEmpty() else true
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
    private val letterAccessUseCase: LetterAccessUseCase,
    private val ticketUseCase: TicketUseCase,
    private val analytics: Analytics,
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
     * Photo Picker로 고른 사진들을 고른 순서대로 줄여(긴 변 2048·JPEG 80·EXIF 회전) 대기 사진으로 넣는다. 업로드는 저장 때.
     * 위치는 마지막으로 포커스된 텍스트 블록 바로 뒤 — 사진들 다음에 빈 텍스트 블록을 붙여 이어 쓸 수 있게 한다.
     * 남은 칸보다 많이 오면(구형 기기 폴백 선택기) 앞에서부터 남은 칸만큼만 넣고 한도 안내를 보인다(B군 — iOS는 말없이 자름).
     * 한 장이 깨져도 나머지는 넣고 "사진을 불러오지 못했어요…"를 보인다(iOS addPhotos와 같음).
     */
    fun addPhotos(uris: List<Uri>) {
        val remaining = _state.value.remainingPhotoSlots
        if (uris.isEmpty() || _state.value.isAddingPhoto || remaining == 0) return
        val overflow = uris.size > remaining
        _state.value = _state.value.copy(
            isAddingPhoto = true,
            error = if (overflow) context.getString(R.string.compose_photo_limit) else null,
        )
        viewModelScope.launch {
            try {
                val photos = mutableListOf<ComposeBlock>()
                for (uri in uris.take(remaining)) {
                    try {
                        val processed = withContext(Dispatchers.Default) { PhotoProcessor.process(context, uri) }
                        photos += ComposeBlock.Photo(
                            LetterPhoto(
                                id = newId(),
                                path = null,
                                width = processed.width,
                                height = processed.height,
                                pendingJpeg = processed.jpeg,
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        _state.value = _state.value.copy(error = context.getString(R.string.compose_photo_failed))
                    }
                }
                if (photos.isEmpty()) return@launch
                val blocks = _state.value.blocks
                val anchor = blocks.indexOfFirst { it is ComposeBlock.Text && it.id == lastFocusedTextId }
                    .takeIf { it >= 0 }
                    ?: blocks.indexOfLast { it is ComposeBlock.Text }
                val inserted = blocks.toMutableList().apply {
                    addAll(anchor + 1, photos + ComposeBlock.Text(newId(), ""))
                }
                _state.value = _state.value.copy(blocks = normalizeBlocks(inserted))
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
                // 새 편지 생성은 이때 한 번뿐이다(이후 저장은 갱신) → letter_create도 여기서만.
                onCreated = {
                    currentMode = ComposeMode.Edit(it)
                    analytics.log(AnalyticsEvent.LetterCreate(reply = replyRecipientId != null))
                },
            )
            applySavedPhotos(saved.blocks)
            saved.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: LetterPhotoSaveException) {
            // 행은 이미 있다(onCreated로 승격됨) — 다시 저장하면 같은 편지를 갱신한다. iOS compose.error.photoUpload.
            _state.value = _state.value.copy(error = "사진을 올리지 못했어요. 연결을 확인하고 다시 저장해 주세요.")
            null
        } catch (e: Throwable) {
            val message = (e as? MutterError)?.userMessage ?: "저장하지 못했어요."
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
                    analytics.log(AnalyticsEvent.LetterSend(permanent = false, reply = true))
                    _state.value = _state.value.copy(navigateUp = true)
                } catch (e: Throwable) {
                    val message = (e as? MutterError)?.userMessage ?: "보내지 못했어요."
                    _state.value = _state.value.copy(error = message)
                }
            } else {
                // 비연결 상대 — 전달 링크로 폴백.
                val choice = loadSendChoice(letterId)
                _state.value = choice(
                    _state.value.copy(
                        sentLetterId = letterId,
                        issuedLink = null,
                        password = "",
                        usePassword = true,
                        connections = connected,
                        showSendSheet = true,
                    ),
                )
            }
        }
    }

    // ─── 보내기 시트 ──────────────────────────────────────────────────────────

    /** 저장 후 보내기 시트 열기(새 편지/이어쓰기). 연결 상대를 미리 불러온다. */
    fun saveAndOpenSend() {
        viewModelScope.launch {
            val id = save() ?: return@launch
            val (connections, choice) = coroutineScope {
                val connectionsDeferred = async {
                    runCatching { connectionUseCase.myConnections() }.getOrDefault(emptyList())
                }
                val choiceDeferred = async { loadSendChoice(id) }
                connectionsDeferred.await() to choiceDeferred.await()
            }
            _state.value = choice(
                _state.value.copy(
                    sentLetterId = id,
                    issuedLink = null,
                    password = "",
                    usePassword = true,
                    error = null,
                    connections = connections,
                    showSendSheet = true,
                ),
            )
        }
    }

    /**
     * 보내기 전 선택에 필요한 상태·잔액·링크 유무를 받아 상태에 합치는 변환을 돌려준다.
     * 상태·잔액 조회가 실패하면 카드 없이(기존 시트) 보인다.
     * 시트를 열 때마다 고른 것(영구·무료 기간)을 지운다 — 링크 없이 나갔다 오면 다시 묻는다.
     */
    private suspend fun loadSendChoice(letterId: String): (ComposeUiState) -> ComposeUiState = coroutineScope {
        val accessDeferred = async { letterAccessUseCase.accessStates(listOf(letterId))[letterId] }
        val entitlementsDeferred = async { ticketUseCase.entitlementsOrNull() }
        val hasLinksDeferred = async {
            try {
                deliveryUseCase.links(letterId).isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 조회 실패는 "링크 없음"으로 본다 — 카드를 띄운다(iOS `(try? links) ?? []`).
                // 편지권은 실제 발급·발송 RPC에서만 쓰이므로 이미 보낸 편지에 카드가 떠도 이중 차감은 없다.
                false
            }
        }
        val access = accessDeferred.await()
        val entitlements = entitlementsDeferred.await()
        val hasLinks = hasLinksDeferred.await()
        // 시트를 여는 시점의 상태에 덮어쓰도록 변환으로 돌려준다(조회 중 바뀐 다른 값을 지우지 않게).
        return@coroutineScope { st: ComposeUiState ->
            st.copy(
                sendAccess = access,
                entitlements = entitlements,
                sendHasLinks = hasLinks,
                choseFreeDays = false,
                chosePermanent = false,
            )
        }
    }

    /**
     * 보내기 전 선택 — 영구 편지를 고르고 버튼을 눌렀다.
     * 편지권이 있으면(관리자 포함) 선택만 기억하고 발급·발송으로 넘어간다. 없으면 결제 안내 시트(구매)를 띄운다.
     * **여기서는 편지권을 쓰지 않는다** — 고르고 나서 보내지 않으면 편지권만 나가기 때문이다.
     */
    fun choosePermanent() {
        _state.value = if (_state.value.entitlements?.hasTicket == true) {
            _state.value.copy(chosePermanent = true)
        } else {
            _state.value.copy(checkoutRoute = TicketCheckoutRoute.SendPermanent)
        }
    }

    /** "다시 고르기" — 선택 카드로 돌아간다. */
    fun chooseAgain() {
        _state.value = _state.value.copy(chosePermanent = false, choseFreeDays = false)
    }

    /**
     * 결제 안내 시트가 닫혔다. 잔액을 다시 받고, 편지권을 샀으면 영구 편지를 고른 것으로 넘어간다.
     * 사지 않고 닫았어도 잔액은 다시 받는다 — 결제 대기가 그 사이 끝났을 수 있다(카드의 보유 수).
     */
    fun checkoutFinished(completed: Boolean) {
        _state.value = _state.value.copy(checkoutRoute = null)
        viewModelScope.launch {
            ticketUseCase.entitlementsOrNull()?.let { _state.value = _state.value.copy(entitlements = it) }
            if (completed) _state.value = _state.value.copy(chosePermanent = true)
        }
    }

    /** 영구로 보낸 뒤 — "영구" 표시를 위해 편지 상태와 잔액을 다시 받는다. */
    private suspend fun refreshSendAccess(id: String) {
        val access = letterAccessUseCase.accessStates(listOf(id))[id]
        val entitlements = ticketUseCase.entitlementsOrNull() ?: _state.value.entitlements
        _state.value = _state.value.copy(sendAccess = access, entitlements = entitlements)
    }

    /** 발급·발송 때 편지권이 없었다(고른 뒤 다른 기기에서 사용 등) — 살 수 있는 계정은 결제 안내 시트로, 아니면 문구로 알린다. */
    private fun handleNoTicket(error: MutterError.NoTicket) {
        _state.value = if (_state.value.entitlements?.canPurchase == true) {
            _state.value.copy(checkoutRoute = TicketCheckoutRoute.SendPermanent)
        } else {
            _state.value.copy(error = error.userMessage)
        }
    }

    /** 보내기 전 선택 — 무료 기간. 보내기 탭을 연다. */
    fun chooseFreeDays() {
        _state.value = _state.value.copy(choseFreeDays = true)
    }

    /** 전달 링크 발급. 암호 ON이면 password 필수. */
    fun issueLink() {
        val id = _state.value.sentLetterId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isIssuing = true, error = null)
            try {
                // 영구 편지를 골랐으면 서버가 링크 발급과 편지권 1장 사용을 한 트랜잭션으로 처리한다.
                val permanent = _state.value.showsPermanentPending
                val password = if (_state.value.usePassword) _state.value.password else null
                val link = deliveryUseCase.issue(letterId = id, password = password, revealAt = null, permanent = permanent)
                analytics.log(AnalyticsEvent.LinkIssue(permanent = permanent, hasPassword = password != null))
                _state.value = _state.value.copy(
                    issuedLink = link,
                    password = "",
                )
                if (permanent) refreshSendAccess(id)
            } catch (e: MutterError.NoTicket) {
                handleNoTicket(e)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "링크를 만들지 못했어요."
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
                val permanent = _state.value.showsPermanentPending
                connectionUseCase.send(id, recipientId, permanent = permanent)
                analytics.log(AnalyticsEvent.LetterSend(permanent = permanent, reply = replyRecipientId != null))
                finishSend()
            } catch (e: MutterError.NoTicket) {
                _state.value = _state.value.copy(isSending = false)
                handleNoTicket(e)
            } catch (e: Throwable) {
                val message = (e as? MutterError)?.userMessage ?: "보내지 못했어요."
                _state.value = _state.value.copy(error = message, isSending = false)
            }
        }
    }

    /** 보내기 완료 — 시트 닫고 제작 화면도 닫는다. 시트 오류는 시트와 함께 지운다. */
    fun finishSend() {
        _state.value = _state.value.copy(showSendSheet = false, navigateUp = true, isSending = false, error = null)
    }

    // ─── 시트 바인딩 헬퍼 ────────────────────────────────────────────────────

    /** 시트를 닫으면 시트 오류도 지운다 — 작성 화면에 남지 않게(iOS SendSheet `.onDisappear { errorMessage = nil }`). */
    fun setShowSendSheet(visible: Boolean) {
        _state.value = if (visible) {
            _state.value.copy(showSendSheet = true)
        } else {
            _state.value.copy(showSendSheet = false, error = null)
        }
    }

    fun setUsePassword(value: Boolean) {
        _state.value = _state.value.copy(usePassword = value)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value)
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /** iOS compose.error.* 한국어 원문(ViewModel 오류 문구는 한국어 상수 — 저장소 관례). */
    private fun scErrorMessage(reason: ScValidationFailReason): String = when (reason) {
        ScValidationFailReason.INVALID_URL -> "SoundCloud 링크가 맞는지 확인해 주세요 (soundcloud.com 주소)."
        ScValidationFailReason.NETWORK -> "네트워크 연결을 확인하고 다시 시도해 주세요."
        ScValidationFailReason.PRIVATE_TRACK -> "비공개이거나 지역 제한이 있는 트랙이에요. 다른 곡을 골라 주세요."
        ScValidationFailReason.NOT_FOUND -> "트랙을 찾을 수 없어요. 링크를 다시 확인해 주세요."
        ScValidationFailReason.EMBED_DISABLED -> "이 트랙은 외부 재생이 허용되지 않아요. 다른 곡을 골라 주세요."
    }

}
