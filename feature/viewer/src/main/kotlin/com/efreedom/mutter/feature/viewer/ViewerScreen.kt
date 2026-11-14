package com.efreedom.mutter.feature.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.core.audiosync.LetterAudioPlayer
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.LetterPhotoImage
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterEqualizer
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.SoundCloudAttribution
import com.efreedom.mutter.uicomponent.theme.BlushLavender
import com.efreedom.mutter.uicomponent.theme.BlushPink
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldLight
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.LetterTheme
import com.efreedom.mutter.uicomponent.theme.LetterThemes
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.OnGold
import com.efreedom.mutter.domain.model.LetterAccessDays
import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterCover
import com.efreedom.mutter.domain.model.LetterPayload
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Entry Point ─────────────────────────────────────────────────────────────

/**
 * 수신/열람 화면 (iOS ViewerView 이식).
 *
 * 진입 방식:
 * - 딥링크 수신: [token] 전달 (password는 암호 게이트에서 사용자 입력)
 * - 내 편지 미리보기: [letterId] 전달
 *
 * 화면을 벗어나는 건 시스템 뒤로(제스처/버튼)뿐이다 — 화면 내 뒤로 버튼은 두지 않는다.
 *
 * [onLoginRequest] — 로그인해야 열리는 편지의 봉투에서 "편지 열기"를 누르면(로그아웃 상태) 호출된다.
 * 호출부가 로그인 화면을 보인 뒤 같은 토큰으로 이 화면을 다시 띄운다. null이면 그냥 다시 불러온다.
 * [onNeedTickets] — 잠긴 편지를 간직하려는데 편지권이 없을 때(NO_TICKET) 구매 화면으로 보낸다.
 */
@Composable
fun ViewerScreen(
    token: String? = null,
    letterId: String? = null,
    onLoginRequest: (() -> Unit)? = null,
    onNeedTickets: () -> Unit = {},
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOpened = viewModel.isOpened
    // 간직하기 확인(편지 끝·닫힘 예고·잠김 화면 공통). 확인 뒤 편지권 1장을 쓴다.
    var confirmingKeep by remember { mutableStateOf(false) }
    val keepOffer = KeepOffer(
        isRecipient = viewModel.isRecipient,
        canKeep = viewModel.canKeep,
        isKept = viewModel.isKept,
        isKeeping = viewModel.isKeeping,
        error = viewModel.keepError,
        warnDays = viewModel.warnDays,
        onKeep = { confirmingKeep = true },
    )

    LaunchedEffect(viewModel.needTickets) {
        if (viewModel.needTickets) {
            viewModel.onNeedTicketsHandled()
            onNeedTickets()
        }
    }

    // 소스를 한 번만 결정해 ViewModel에 전달한다 (ViewModel 내부에서 중복 호출 가드).
    LaunchedEffect(token, letterId) {
        val src = when {
            token != null -> ViewerViewModel.Source.Token(token)
            letterId != null -> ViewerViewModel.Source.MyLetter(letterId)
            else -> return@LaunchedEffect
        }
        viewModel.start(src)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 숨김 오디오 위젯 ────────────────────────────────────────────────
        // SoundCloud는 WebView를 Compose 트리에 배치해야 READY 이벤트가 발생한다.
        // key(source)로 소스 인스턴스가 바뀔 때만 재생성 — 리컴포즈마다 WebView 파괴 방지.
        val currentSource = viewModel.player.currentSource
        if (currentSource != null) {
            key(currentSource) {
                Box(modifier = Modifier.size(1.dp)) {
                    currentSource.Attachment()
                }
            }
        }

        // ── 상태별 콘텐츠 ────────────────────────────────────────────────────
        when (val s = state) {
            ViewerState.Loading -> LoadingContent()
            ViewerState.PasswordRequired -> PasswordGate(onSubmit = viewModel::submitPassword)
            is ViewerState.RevealPending -> RevealPendingContent(s.revealAt)
            is ViewerState.Failed -> FailedContent(s.message)
            ViewerState.Gone -> FailedContent(stringResource(R.string.viewer_gone))
            is ViewerState.Envelope -> EnvelopeContent(
                cover = s.cover,
                onOpen = {
                    val login = onLoginRequest
                    if (!viewModel.isSignedIn && login != null) login() else viewModel.reload()
                },
            )
            is ViewerState.Locked -> LockedContent(
                state = s,
                isKeeping = viewModel.isKeeping,
                error = viewModel.keepError,
                onKeep = { confirmingKeep = true },
            )
            is ViewerState.Ready -> {
                if (isOpened) {
                    LetterReader(
                        payload = s.payload,
                        player = viewModel.player,
                        photoUrls = viewModel.photoUrls,
                        keepOffer = keepOffer,
                    )
                    // 받은함 자동 저장 표시(인증 + 토큰 수신 시). iOS ViewerView savedToInbox 대응.
                    if (viewModel.savedToInbox) {
                        SavedToInboxBadge(
                            theme = LetterThemes.byId(s.payload.templateId),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 8.dp),
                        )
                    }
                } else {
                    OpenGate(payload = s.payload, keepOffer = keepOffer, onOpen = viewModel::open)
                }
            }
        }

        // 뒤로가기 오버레이는 두지 않는다 — 시스템 뒤로(제스처/버튼)로 이 화면을 벗어난다.
        // NavHost 목적지라 시스템 뒤로가 popBackStack을 그대로 수행하며, 편지를 가리는 요소도 없어진다.
    }

    if (confirmingKeep) {
        AlertDialog(
            onDismissRequest = { confirmingKeep = false },
            text = { Text(stringResource(R.string.viewer_keep_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingKeep = false
                    viewModel.keep()
                }) { Text(stringResource(R.string.viewer_keep_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingKeep = false }) { Text(stringResource(R.string.viewer_cancel)) }
            },
        )
    }
}

// ─── Loading ─────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Gold)
    }
}

// ─── Failed ──────────────────────────────────────────────────────────────────

@Composable
private fun FailedContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("편지를 열 수 없어요", style = MutterText.title, color = Ink)
            Text(
                text = message,
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Envelope (LOGIN_REQUIRED) ───────────────────────────────────────────────

/** 로그인 전 봉투 — 제목·보낸 사람만(get_letter_cover). 본문·열람 기록 없음. */
@Composable
private fun EnvelopeContent(cover: LetterCover?, onOpen: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            EnvelopeBadge()
            CoverTitle(cover)
            Text(
                stringResource(R.string.viewer_envelope_login_hint),
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
            MutterButton(
                text = stringResource(R.string.viewer_envelope_open),
                onClick = onOpen,
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            )
        }
    }
}

/** 봉투·잠김 화면 공통 — 제목과 보낸 사람(있을 때만). */
@Composable
private fun CoverTitle(cover: LetterCover?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cover?.senderNickname?.takeIf { it.isNotBlank() }?.let { sender ->
            Text(
                stringResource(R.string.viewer_cover_sender, sender),
                style = MutterText.caption,
                color = InkSoft,
            )
        }
        cover?.title?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = title,
                style = MutterText.titleLarge,
                color = Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EnvelopeBadge() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(
                brush = Brush.linearGradient(listOf(GoldLight, BlushPink, BlushLavender)),
                shape = RoundedCornerShape(28.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        MutterIcon(id = DsR.drawable.envelope, contentDescription = null, tint = OnGold, size = 46.dp)
    }
}

// ─── Locked (LETTER_LOCKED) ──────────────────────────────────────────────────

/** 무료 기간이 끝난 편지 — 삭제 예정일까지 편지권으로 간직할 수 있다(확인은 [ViewerScreen]이 띄운다). */
@Composable
private fun LockedContent(
    state: ViewerState.Locked,
    isKeeping: Boolean,
    error: String?,
    onKeep: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = Gold, size = 32.dp)
            }
            CoverTitle(state.cover)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.viewer_locked_title), style = MutterText.title, color = Ink)
                state.deleteAt?.let { deleteAt ->
                    val date = remember(deleteAt) { deleteAt.atZone(ZoneId.systemDefault()).toLocalDate() }
                    Text(
                        stringResource(R.string.viewer_locked_body, date.monthValue, date.dayOfMonth),
                        style = MutterText.bodyMedium,
                        color = InkSoft,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (state.canKeep) {
                MutterButton(
                    text = stringResource(R.string.viewer_locked_keep),
                    onClick = onKeep,
                    isLoading = isKeeping,
                    modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                )
            }
            error?.let {
                Text(text = it, style = MutterText.caption, color = GoldDeep, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─── Password Gate ───────────────────────────────────────────────────────────

/**
 * 암호 게이트 — 잠금 배지 + 입력 필드 + 확인 버튼.
 * 암호 입력 상태는 화면 로컬로 관리하고, 확인 시 [onSubmit]에 전달한다.
 */
@Composable
private fun PasswordGate(onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
        ) {
            // 자물쇠 배지 (골드 소프트 원형)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = Gold, size = 32.dp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("암호가 필요해요", style = MutterText.title, color = Ink)
                Text(
                    "이 편지는 암호로 보호돼 있어요",
                    style = MutterText.bodyMedium,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("암호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            MutterButton(
                text = "확인",
                onClick = { onSubmit(password) },
                enabled = password.isNotBlank(),
            )
        }
    }
}

// ─── Reveal Pending ──────────────────────────────────────────────────────────

@Composable
private fun RevealPendingContent(revealAt: Instant) {
    val formatted = remember(revealAt) { formatInstant(revealAt) }

    Box(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = Gold, size = 28.dp)
            Text("아직 열 수 없어요", style = MutterText.title, color = Ivory)
            Text(
                text = "${formatted}에 열려요",
                style = MutterText.bodyMedium,
                color = GoldSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Open Gate (Screen 4) ────────────────────────────────────────────────────

/**
 * 봉투 배지 + 편지 제목 + "열기 ▶" CTA.
 * [onOpen]은 사용자 제스처(onClick)에서 호출되므로 SoundCloud 오디오 언락이 성립한다.
 */
@Composable
private fun OpenGate(
    payload: LetterPayload,
    keepOffer: KeepOffer,
    onOpen: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            // 봉투 배지 — 골드 그라데이션 96dp 라운드 스퀘어
            EnvelopeBadge()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("편지가 도착했어요", style = MutterText.caption, color = InkSoft)
                Text(
                    text = payload.title.ifBlank { "무제" },
                    style = MutterText.titleLarge,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                "음악과 함께 편지를 열어보세요",
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )

            MutterButton(
                text = "열기 ▶",
                onClick = onOpen,
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            )

            ClosingSoonLine(payload, keepOffer)
        }
    }
}

/**
 * 받은 사람의 간직하기 상태 — 편지 끝 안내(①)·닫힘 예고(②)가 같이 쓴다.
 * [isRecipient] 로그인·소유자 아님, [canKeep] 버튼 노출(`tickets > 0 || paid_enabled || is_admin`).
 */
private data class KeepOffer(
    val isRecipient: Boolean,
    val canKeep: Boolean,
    val isKept: Boolean,
    val isKeeping: Boolean,
    val error: String?,
    val warnDays: Int,
    val onKeep: () -> Unit,
)

/**
 * ② 닫힘 예고 — 영구가 아니고 남은 기간이 warn_days 이하일 때 조용히 한 줄 + "간직하기"(간직할 수 있는 받은 사람).
 * 방금 간직했으면 "간직한 편지예요". 보낸 사람 본인(`is_mine`)에게는 아무것도 없다.
 */
@Composable
private fun ClosingSoonLine(payload: LetterPayload, keepOffer: KeepOffer) {
    if (payload.isMine) return
    if (keepOffer.isKept) {
        Text(
            text = stringResource(R.string.viewer_kept),
            style = MutterText.caption,
            color = InkFaint,
            textAlign = TextAlign.Center,
        )
        return
    }
    val accessUntil = payload.accessUntil ?: return
    if (payload.isPermanent) return
    val days = remember(accessUntil) { LetterAccessDays.until(accessUntil, Instant.now()) }
    if (days > keepOffer.warnDays) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (days <= 0) {
                    stringResource(R.string.viewer_closing_today)
                } else {
                    stringResource(R.string.viewer_closing_in_days, days)
                },
                style = MutterText.caption,
                color = InkFaint,
                textAlign = TextAlign.Center,
            )
            if (keepOffer.isRecipient && keepOffer.canKeep) {
                KeepTextButton(
                    text = stringResource(R.string.viewer_closing_keep),
                    color = GoldDeep,
                    keepOffer = keepOffer,
                )
            }
        }
        keepOffer.error?.let {
            Text(text = it, style = MutterText.caption, color = GoldDeep, textAlign = TextAlign.Center)
        }
    }
}

/** 간직하기 글자 버튼 — 진행 중이면 작은 원형 표시. */
@Composable
private fun KeepTextButton(text: String, color: Color, keepOffer: KeepOffer) {
    if (keepOffer.isKeeping) {
        CircularProgressIndicator(
            modifier = Modifier.padding(horizontal = 12.dp).size(16.dp),
            strokeWidth = 2.dp,
            color = color,
        )
    } else {
        TextButton(onClick = keepOffer.onKeep) {
            Text(text = text, style = MutterText.captionBold, color = color)
        }
    }
}

/**
 * ① 편지 맨 끝 — 기간 제한 편지를 연 받은 사람에게 작은 한 줄 "이 편지는 {M}월 {D}일까지 열려요" + "계속 간직하기".
 * 영구 편지·익명·내 편지는 아무것도 없다. 방금 간직했으면 "간직한 편지예요".
 */
@Composable
private fun LetterEndKeepLine(payload: LetterPayload, keepOffer: KeepOffer, foreground: Color) {
    val muted = foreground.copy(alpha = 0.6f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        if (keepOffer.isKept) {
            Text(text = stringResource(R.string.viewer_kept), style = MutterText.caption, color = muted)
            return@Column
        }
        val accessUntil = payload.accessUntil ?: return@Column
        val date = remember(accessUntil) { accessUntil.atZone(ZoneId.systemDefault()).toLocalDate() }
        Text(
            text = stringResource(R.string.viewer_letter_end_until, date.monthValue, date.dayOfMonth),
            style = MutterText.caption,
            color = muted,
            textAlign = TextAlign.Center,
        )
        if (keepOffer.canKeep) {
            KeepTextButton(
                text = stringResource(R.string.viewer_letter_end_keep),
                color = foreground,
                keepOffer = keepOffer,
            )
        }
        keepOffer.error?.let {
            Text(text = it, style = MutterText.caption, color = muted, textAlign = TextAlign.Center)
        }
    }
}

// ─── Letter Reader (Screen 5) ────────────────────────────────────────────────

/**
 * 편지 본문 열람 화면.
 *
 * 첫 화면은 "아래로 스크롤" 안내로 가득 채워 편지를 아래로 밀어낸다.
 * 각 단락은 LazyColumn 아이템으로 분리되며, 뷰포트에 진입한 순간부터
 * alpha·offset 애니메이션으로 서서히 나타난다(iOS revealOnScroll 대응).
 */
@Composable
private fun LetterReader(
    payload: LetterPayload,
    player: LetterAudioPlayer,
    photoUrls: Map<String, String>,
    keepOffer: KeepOffer,
) {
    val theme = remember(payload.templateId) { LetterThemes.byId(payload.templateId) }
    // 줄 단위 등장(iOS per-line reveal) — 텍스트 블록을 하드 개행(\n)으로 분할, 빈 줄은 문단 간격.
    // 사진 블록은 제자리에서 한 아이템으로 끼워 같은 등장 애니메이션을 탄다.
    val items = remember(payload.blocks) { readerItems(payload.blocks) }
    val hasPlayer = !payload.audioDisabled && payload.cue != null && !player.isUnavailable

    // 아이템 키 기반 등장 추적 — 한 번 보인 아이템은 사라지지 않는다.
    val appearedKeys = remember { mutableStateListOf<Any>() }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key } }
            .collect { keys -> keys.forEach { k -> if (k !in appearedKeys) appearedKeys.add(k) } }
    }

    // 테마에서 본문/제목 TextStyle 도출
    val bodyStyle = remember(theme) {
        TextStyle(
            fontFamily = theme.fontFamily,
            fontSize = theme.bodySize,
            lineHeight = (theme.bodySize.value * theme.lineHeightMultiplier).sp,
            fontWeight = FontWeight.Normal,
        )
    }
    val headingStyle = remember(theme) {
        TextStyle(
            fontFamily = theme.fontFamily,
            fontSize = theme.headingSize,
            lineHeight = (theme.headingSize.value * theme.lineHeightMultiplier).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
    ) {
        // 종이 질감 — 편지 테마 accent를 5% 세로 그라데이션으로 얹는다(iOS LetterPaperTexture 대응).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(theme.accent.copy(alpha = 0.05f), Color.Transparent),
                    ),
                ),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── 인트로: 한 화면 가득 스크롤 안내 ─────────────────────────────
            item(key = "prompt") {
                ScrollPromptView(
                    foreground = theme.foreground,
                    modifier = Modifier.fillParentMaxHeight(),
                )
            }

            // ── 편지 제목 ──────────────────────────────────────────────────
            if (payload.title.isNotBlank()) {
                item(key = "title") {
                    val appeared = "title" in appearedKeys
                    val alpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(durationMillis = 600),
                        label = "title-alpha",
                    )
                    val offsetY by animateDpAsState(
                        targetValue = if (appeared) 0.dp else 20.dp,
                        animationSpec = tween(durationMillis = 600),
                        label = "title-offset",
                    )
                    Text(
                        text = payload.title,
                        style = headingStyle,
                        color = theme.foreground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                            .alpha(alpha)
                            .offset(y = offsetY),
                    )
                }
            }

            // ── 줄별 계단식 등장 ───────────────────────────────────────────
            itemsIndexed(items, key = { i, _ -> "line-$i" }) { index, item ->
                val line = (item as? ReaderItem.Line)?.text
                if (item is ReaderItem.Photo) {
                    val appeared = "line-$index" in appearedKeys
                    val alpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(durationMillis = 600, delayMillis = 80),
                        label = "photo-alpha-$index",
                    )
                    val photo = item.photo
                    LetterPhotoImage(
                        // 서명 URL은 열 때마다 바뀌므로 캐시 키는 path.
                        cacheKey = photo.path ?: photo.id,
                        url = photo.path?.let { photoUrls[it] },
                        bytes = null,
                        width = photo.width,
                        height = photo.height,
                        placeholderColor = theme.accent.copy(alpha = 0.08f),
                        contentDescription = stringResource(R.string.viewer_photo_description),
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .alpha(alpha),
                    )
                } else if (line.isNullOrBlank()) {
                    // 빈 줄 = 문단 간격.
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    val itemKey = "line-$index"
                    val appeared = itemKey in appearedKeys
                    val alpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(durationMillis = 600, delayMillis = 80),
                        label = "line-alpha-$index",
                    )
                    val offsetY by animateDpAsState(
                        targetValue = if (appeared) 0.dp else 20.dp,
                        animationSpec = tween(durationMillis = 600, delayMillis = 80),
                        label = "line-offset-$index",
                    )
                    Text(
                        text = line,
                        style = bodyStyle,
                        color = theme.foreground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 2.dp)
                            .alpha(alpha)
                            .offset(y = offsetY),
                    )
                }
            }

            // ── ① 편지 끝 간직 안내(받은 사람, 기간 제한 편지) — 본문·사진이 끝난 자리, 스크롤 내용의 일부 ──
            if (keepOffer.isRecipient && (keepOffer.isKept || (!payload.isPermanent && payload.accessUntil != null))) {
                item(key = "keep-end") {
                    LetterEndKeepLine(payload = payload, keepOffer = keepOffer, foreground = theme.foreground)
                }
            }

            // ── 하단 여백 ────────────────────────────────────────────────
            // 플레이어 바 높이 + 뷰포트의 1/4.
            //
            // 뒤에 여백을 더 두는 이유: 인트로 안내가 뷰포트 한 개를 차지하므로 최대 스크롤량은
            // '편지 높이'와 같다. 그래서 편지가 한 화면보다 짧으면 끝까지 내려도 편지가 화면
            // 하단에 붙고 위쪽이 안내의 잔여 여백으로 남는다. 뒤에 여백을 주면 그만큼 더 올라온다.
            //
            // 1/4인 이유: 절반을 주면 이번엔 편지가 화면 위로 붙어 아래가 비었다. 짧은 편지가
            // 대략 화면 중앙에 놓이는 값이다. 편지 길이와 무관하게 '정확히' 중앙에 맞추려면
            // 제목+본문을 한 아이템으로 묶어 최소 높이를 뷰포트로 주고 세로 중앙 정렬해야 하는데,
            // 그러면 줄 단위 등장 애니메이션(아이템 가시성 기반)이 한꺼번에 터진다 → 여백으로 해결.
            item(key = "spacer") {
                Column {
                    Spacer(modifier = Modifier.height(if (hasPlayer) 96.dp else 40.dp))
                    Spacer(modifier = Modifier.fillParentMaxHeight(0.25f))
                }
            }
        }

        // ── 하단 고정 음악 플레이어 바 ─────────────────────────────────────
        if (hasPlayer) {
            NowPlayingBar(
                cue = payload.cue,
                isPlaying = player.isPlaying,
                onToggle = player::toggle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/** 열람 아이템 — 본문 한 줄 또는 사진 한 장. */
private sealed interface ReaderItem {
    data class Line(val text: String) : ReaderItem
    data class Photo(val photo: LetterPhoto) : ReaderItem
}

private fun readerItems(blocks: List<LetterBlock>): List<ReaderItem> = blocks.flatMap { block ->
    when (block) {
        is LetterBlock.Text -> block.text.split("\n").map { ReaderItem.Line(it) }
        is LetterBlock.Photo -> listOf(ReaderItem.Photo(block.photo))
    }
}

// ─── Scroll Prompt ───────────────────────────────────────────────────────────

/**
 * 열람 첫 화면을 가득 채우는 스크롤 안내 — 편지를 아래로 밀어낸다.
 * 높이는 호출부가 [Modifier.fillParentMaxHeight]로 LazyColumn 뷰포트만큼 준다.
 *
 * 폭은 여기서 채운다. CenterHorizontally는 Column '안에서'만 정렬하므로,
 * 폭을 채우지 않으면 Column이 텍스트 너비로 줄어들어 안내가 화면 왼쪽에 붙는다.
 */
@Composable
private fun ScrollPromptView(
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "아래로 스크롤",
            style = MutterText.bodyMedium,
            color = foreground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        MutterIcon(
            id = DsR.drawable.ic_chevron_right,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.6f),
            size = 28.dp,
            modifier = Modifier.rotate(90f),
        )
    }
}

// ─── Saved-to-inbox badge ─────────────────────────────────────────────────────

/** "받은함에 저장됨" 표시 — 편지 테마 색으로 상단에 얹는 알약(iOS savedToInbox 행 대응). */
@Composable
private fun SavedToInboxBadge(theme: LetterTheme, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color = theme.background.copy(alpha = 0.92f), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MutterIcon(id = DsR.drawable.check_circle, contentDescription = null, tint = theme.accent, size = 14.dp)
        Text(text = "받은함에 저장됨", style = MutterText.caption, color = theme.foreground)
    }
}

// ─── Now Playing Bar ─────────────────────────────────────────────────────────

/**
 * 화면 하단 고정 음악 플레이어 바.
 * 트랙명·아티스트 + 재생/일시정지 토글. [player.isPlaying]이 true일 때만 표시된다.
 */
@Composable
private fun NowPlayingBar(
    cue: MusicCue?,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Ink.copy(alpha = 0.90f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 재생 중이면 이퀄라이저 애니메이션, 정지 중이면 정적 음표 아이콘(iOS MusicPlayerBar 대응).
            if (isPlaying) {
                MutterEqualizer(barColor = Gold)
            } else {
                MutterIcon(id = DsR.drawable.note, contentDescription = null, tint = Gold, size = 18.dp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cue?.title ?: "트랙",
                    style = MutterText.captionBold,
                    color = Ivory,
                    maxLines = 1,
                )
                cue?.author?.let { author ->
                    Text(
                        text = author,
                        style = MutterText.caption,
                        color = InkFaint,
                        maxLines = 1,
                    )
                }
                // SoundCloud 트랙이면 브랜드 표기 + 원곡 링크백(Widget 이용약관 준수). iOS MusicPlayerBar 대응.
                val scUrl = cue?.takeIf { it.source == MusicSource.SOUNDCLOUD }
                    ?.let { it.sourceUrl ?: it.ref }
                if (scUrl != null) {
                    SoundCloudAttribution(sourceUrl = scUrl)
                }
            }
            IconButton(onClick = onToggle) {
                MutterIcon(
                    id = if (isPlaying) DsR.drawable.pause else DsR.drawable.play,
                    contentDescription = if (isPlaying) "일시정지" else "재생",
                    tint = Ivory,
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** iOS DateFormatter("M월 d일 a h시", ko_KR) 대응. */
private fun formatInstant(instant: Instant): String {
    val zdt = instant.atZone(ZoneId.systemDefault())
    return DateTimeFormatter
        .ofPattern("M월 d일 a h시", Locale.KOREAN)
        .format(zdt)
}
