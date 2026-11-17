package com.efreedom.mutter.feature.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.LetterPhotoImage
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterEqualizer
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.SoundCloudOrange
import com.efreedom.mutter.uicomponent.theme.BlushLavender
import com.efreedom.mutter.uicomponent.theme.BlushPink
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldLight
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.LetterThemes
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.OnGold
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
 * 호출부가 로그인 화면을 보인 뒤 같은 토큰으로 이 화면을 다시 띄운다. null이거나 이미 로그인 상태면 봉투에 버튼을 두지 않는다.
 * [ticketCheckout] — 간직하려는데 편지권이 없을 때 편지 위로 띄우는 결제 안내 시트(앱이 주입). null이면 띄우지 않는다.
 */
@Composable
fun ViewerScreen(
    token: String? = null,
    letterId: String? = null,
    onLoginRequest: (() -> Unit)? = null,
    ticketCheckout: (@Composable (TicketCheckoutRoute, (Boolean) -> Unit) -> Unit)? = null,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOpened = viewModel.isOpened
    val keepOffer = KeepOffer(
        isTokenViewer = viewModel.isTokenViewer,
        isSignedIn = viewModel.isSignedIn,
        isRecipient = viewModel.isRecipient,
        canKeep = viewModel.canKeep,
        hasTicket = viewModel.hasTicket,
        isKept = viewModel.isKept,
        isKeeping = viewModel.isKeeping,
        error = viewModel.keepError,
        warnDays = viewModel.warnDays,
        graceDays = viewModel.graceDays,
        onKeep = viewModel::requestKeep,
    )

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
            ViewerState.PasswordRequired -> PasswordGate(
                password = viewModel.passwordInput,
                onPasswordChange = viewModel::updatePassword,
                onSubmit = viewModel::submitPassword,
            )
            is ViewerState.RevealPending -> RevealPendingContent(s.revealAt)
            is ViewerState.Failed -> FailedContent(s.message)
            ViewerState.Gone -> FailedContent(stringResource(R.string.viewer_gone))
            // 로그인 상태면 버튼을 숨긴다(iOS는 콜백을 nil로 넘긴다).
            is ViewerState.Envelope -> EnvelopeContent(
                cover = s.cover,
                onOpen = onLoginRequest?.takeIf { !viewModel.isSignedIn },
            )
            is ViewerState.Locked -> LockedContent(
                state = s,
                hasTicket = viewModel.hasTicket,
                isKeeping = viewModel.isKeeping,
                error = viewModel.keepError,
                onKeep = viewModel::requestKeep,
            )
            is ViewerState.Ready -> {
                if (isOpened) {
                    LetterReader(
                        payload = s.payload,
                        player = viewModel.player,
                        photoUrls = viewModel.photoUrls,
                        keepOffer = keepOffer,
                        savedToInbox = viewModel.savedToInbox,
                    )
                } else {
                    OpenGate(payload = s.payload, onOpen = viewModel::open)
                }
            }
        }

        // 뒤로가기 오버레이는 두지 않는다 — 시스템 뒤로(제스처/버튼)로 이 화면을 벗어난다.
        // NavHost 목적지라 시스템 뒤로가 popBackStack을 그대로 수행하며, 편지를 가리는 요소도 없어진다.
    }

    // 간직하려는데 편지권이 없으면 편지 위로 결제 안내가 올라온다(편지와 음악은 뒤에 그대로).
    viewModel.checkoutRoute?.let { route -> ticketCheckout?.invoke(route, viewModel::checkoutFinished) }
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
            Text(stringResource(R.string.viewer_error_cant_open), style = MutterText.title, color = Ink)
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

/**
 * 로그인 전 봉투 — 제목·보낸 사람만(get_letter_cover). 본문·열람 기록 없음. iOS `ViewerCoverView`.
 * [onOpen]이 null이면(로그인 상태 등) 버튼을 숨긴다.
 */
@Composable
private fun EnvelopeContent(cover: LetterCover?, onOpen: (() -> Unit)?) {
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
            if (onOpen != null) {
                MutterButton(
                    text = stringResource(R.string.viewer_envelope_open),
                    onClick = onOpen,
                    leadingIconRes = DsR.drawable.play,
                    modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                )
            }
            Text(
                stringResource(R.string.viewer_envelope_login_hint),
                style = MutterText.caption,
                color = InkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 봉투 전용 — 보낸이(없으면 "편지가 도착했어요")와 제목(없으면 "소중한 편지"). */
@Composable
private fun CoverTitle(cover: LetterCover?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val sender = cover?.senderNickname?.takeIf { it.isNotBlank() }
        Text(
            text = if (sender != null) {
                stringResource(R.string.viewer_cover_sender, sender)
            } else {
                stringResource(R.string.viewer_arrived)
            },
            style = MutterText.caption,
            color = InkSoft,
        )
        Text(
            text = cover?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.viewer_untitled),
            style = MutterText.titleLarge,
            color = Ink,
            textAlign = TextAlign.Center,
        )
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

/**
 * 무료 기간이 끝난 편지 — 언제 삭제되는지 알리고, 그 전까지 편지권으로 간직할 수 있다.
 * 버튼 문구가 편지권을 말하므로 확인 없이 바로 진행한다. [hasTicket]이면 누르는 즉시 1장을 쓰므로 버튼 아래에 미리 알리고,
 * 없으면 결제 안내 시트가 뜬다.
 */
@Composable
private fun LockedContent(
    state: ViewerState.Locked,
    hasTicket: Boolean,
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
                .padding(32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(GoldSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = Gold, size = 32.dp)
            }
            // iOS ViewerLockedView 순서: 안내 제목 → 편지 제목 → 보낸이 → 삭제 날짜(제목·보낸이는 있을 때만, 폴백 없음).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.viewer_locked_title),
                    style = MutterText.title,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
                state.cover?.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(title, style = MutterText.bodyMediumBold, color = Ink, textAlign = TextAlign.Center)
                }
                state.cover?.senderNickname?.takeIf { it.isNotBlank() }?.let { sender ->
                    Text(
                        stringResource(R.string.viewer_cover_sender, sender),
                        style = MutterText.caption,
                        color = InkSoft,
                    )
                }
                state.deleteAt?.let { deleteAt ->
                    val date = remember(deleteAt) { deleteAt.atZone(ZoneId.systemDefault()).toLocalDate() }
                    // "{M}월 {D}일에 삭제돼요." + (간직 버튼이 보일 때만) "그 전에 간직하면 계속 열 수 있어요."
                    val deleteOn = stringResource(R.string.viewer_locked_delete_on, date.monthValue, date.dayOfMonth)
                    Text(
                        if (state.canKeep) deleteOn + "\n" + stringResource(R.string.viewer_locked_keep_hint) else deleteOn,
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
                if (hasTicket) {
                    Text(
                        text = stringResource(R.string.viewer_keep_confirm_body),
                        style = MutterText.caption,
                        color = InkSoft,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            error?.let {
                Text(text = it, style = MutterText.caption, color = Danger, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─── Password Gate ───────────────────────────────────────────────────────────

/**
 * 암호 게이트 — 잠금 배지 + 입력 필드 + 열기 버튼.
 * 입력값은 ViewModel([ViewerViewModel.passwordInput])이 가진다 — 틀려서 이 화면이 다시 떠도 남는다(iOS `model.password`).
 */
@Composable
private fun PasswordGate(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
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
                Text(stringResource(R.string.viewer_password_title), style = MutterText.title, color = Ink)
                Text(
                    stringResource(R.string.viewer_password_subtitle),
                    style = MutterText.bodyMedium,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.viewer_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            MutterButton(
                text = stringResource(R.string.viewer_open_short),
                onClick = onSubmit,
                enabled = password.isNotEmpty(),
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
            Text(stringResource(R.string.viewer_not_yet_title), style = MutterText.title, color = Ivory)
            Text(
                text = stringResource(R.string.viewer_not_yet_at, formatted),
                style = MutterText.bodyMedium,
                color = GoldSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Open Gate (Screen 4) ────────────────────────────────────────────────────

/**
 * 봉투 배지 + 편지 제목 + 안내 + "편지 열기"(재생 아이콘) + 설치 불필요 줄. iOS `ViewerView.openGate`.
 * [onOpen]은 사용자 제스처(onClick)에서 호출되므로 SoundCloud 오디오 언락이 성립한다.
 */
@Composable
private fun OpenGate(
    payload: LetterPayload,
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
                Text(stringResource(R.string.viewer_arrived), style = MutterText.caption, color = InkSoft)
                Text(
                    text = payload.title.ifBlank { stringResource(R.string.viewer_untitled) },
                    style = MutterText.titleLarge,
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                stringResource(R.string.viewer_subtitle),
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )

            MutterButton(
                text = stringResource(R.string.viewer_envelope_open),
                onClick = onOpen,
                leadingIconRes = DsR.drawable.play,
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutterIcon(id = DsR.drawable.ic_check, contentDescription = null, tint = InkFaint, size = 14.dp)
                Text(stringResource(R.string.viewer_no_install), style = MutterText.caption, color = InkFaint)
            }
        }
    }
}

// ─── Letter Reader (Screen 5) ────────────────────────────────────────────────

/**
 * 편지 본문 열람 화면.
 *
 * 첫 화면은 "스크롤 해주세요" 안내로 가득 채워 편지를 아래로 밀어낸다.
 * 각 단락은 LazyColumn 아이템으로 분리되며, 뷰포트에 진입한 순간부터
 * alpha·offset 애니메이션으로 서서히 나타난다(iOS revealOnScroll 대응).
 */
@Composable
private fun LetterReader(
    payload: LetterPayload,
    player: LetterAudioPlayer,
    photoUrls: Map<String, String>,
    keepOffer: KeepOffer,
    savedToInbox: Boolean,
) {
    val theme = remember(payload.templateId) { LetterThemes.byId(payload.templateId) }
    // 편지 끝 안내 값(iOS ViewerModelData.openUntil / closingSoonDays) — 토큰 수신·내 편지 아님은 아래 아이템 조건이 본다.
    // ① 로그인 + 기간 제한 편지의 열린 기한. ② 간직 전 + 남은 날 ≤ warnDays(로그인 무관). now는 remember로 고정(iOS처럼 다시 그릴 때만 재평가).
    val openUntil = payload.accessUntil?.takeIf { keepOffer.isSignedIn && !payload.isPermanent }
    val closingSoonDays = if (keepOffer.isKept) {
        null
    } else {
        remember(payload.accessUntil, keepOffer.warnDays) { payload.closingSoonDays(keepOffer.warnDays) }
    }
    val showKeepFooter = keepOffer.isTokenViewer && !payload.isMine &&
        (openUntil != null || closingSoonDays != null || keepOffer.isKept || keepOffer.error != null)
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

            // ── 편지 끝 간직 안내(열린 기한·닫힘 예고·간직한 편지) — 읽기를 끊지 않게 스크롤 마지막에만 ──
            if (showKeepFooter) {
                item(key = "keep-end") {
                    ViewerKeepFooter(
                        openUntil = openUntil,
                        closingSoonDays = closingSoonDays,
                        keepOffer = keepOffer,
                        foreground = theme.foreground,
                    )
                }
            }

            // ── 받은함 자동 저장 표시(로그인 + 토큰 수신 + 로드 성공 — 내 편지도). iOS ViewerView savedToInbox ──
            if (savedToInbox) {
                item(key = "saved") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        MutterIcon(id = DsR.drawable.ic_check, contentDescription = null, tint = Gold, size = 16.dp)
                        Text(
                            text = stringResource(R.string.viewer_saved_to_inbox),
                            style = MutterText.caption,
                            color = InkSoft,
                        )
                    }
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
            text = stringResource(R.string.viewer_scroll),
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

// ─── Now Playing Bar ─────────────────────────────────────────────────────────

/**
 * 화면 하단 고정 음악 플레이어 바.
 * 트랙명·아티스트 + 재생/일시정지 토글. 플레이어가 있으면 재생 중이든 멈춤이든 표시된다.
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
            // 곡 정보 영역 전체가 한 버튼 — 탭하면 원곡(SoundCloud)으로(Widget 이용약관: 브랜딩 + 링크백). iOS MusicPlayerBar.
            // iOS는 기본 제공 곡에도 "SoundCloud"를 붙이지만 사실과 달라 SoundCloud 큐에만 출처 줄·링크를 둔다(정책 1).
            val isSoundCloud = cue?.source == MusicSource.SOUNDCLOUD
            val scUrl = cue?.takeIf { isSoundCloud }?.let { it.sourceUrl ?: it.ref }
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = scUrl != null) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(scUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
            ) {
                Text(
                    text = cue?.title ?: stringResource(
                        if (cue?.source == MusicSource.HOSTED) R.string.viewer_builtin_music else R.string.viewer_soundcloud_track,
                    ),
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
                if (isSoundCloud) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("SoundCloud", style = MutterText.caption, color = SoundCloudOrange)
                        if (scUrl != null) {
                            MutterIcon(id = DsR.drawable.link, contentDescription = null, tint = SoundCloudOrange, size = 10.dp)
                        }
                    }
                }
            }
            IconButton(onClick = onToggle) {
                MutterIcon(
                    id = if (isPlaying) DsR.drawable.pause else DsR.drawable.play,
                    contentDescription = stringResource(if (isPlaying) R.string.viewer_pause else R.string.viewer_play),
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
