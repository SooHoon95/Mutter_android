package com.efreedom.mutter.feature.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicSource
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterEqualizer
import com.efreedom.mutter.uicomponent.component.LetterPhotoImage
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.SoundCloudAttribution
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.LetterTheme
import com.efreedom.mutter.uicomponent.theme.LetterThemes
import com.efreedom.mutter.uicomponent.theme.MutterText

/**
 * 편지 제작 화면 — 편지지(테마) + 본문 + 음악 1곡 + 저장/발송.
 * iOS ComposeView 이식. 탐색은 onDone 콜백 단일 진입점.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    mode: ComposeMode,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** 영구 편지로 보내려는데 편지권이 없을 때 보내기 시트 위로 띄우는 결제 안내 시트(앱이 주입). */
    ticketCheckout: @Composable (TicketCheckoutRoute, (Boolean) -> Unit) -> Unit = { _, _ -> },
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LetterThemes.byId(state.templateId)

    // 화면 진입 시 한 번만 모드 초기화(이어쓰기면 기존 편지 로드).
    LaunchedEffect(Unit) {
        viewModel.start(mode)
    }

    // navigateUp 신호 — 저장 완료·발송 완료 시 상위로 이동.
    LaunchedEffect(state.navigateUp) {
        if (state.navigateUp) onDone()
    }

    if (state.showSendSheet) {
        SendSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowSendSheet(false) },
        )
    }
    // 보내기 시트보다 뒤에 그려야 그 위로 올라온다.
    state.checkoutRoute?.let { route -> ticketCheckout(route, viewModel::checkoutFinished) }

    // 시스템 Photo Picker(이미지만) — 권한 요청 없이 고른 사진만 앱에 넘어온다. iOS PhotosPicker(maxSelectionCount: 남은 칸).
    // 다중 선택 계약은 maxItems > 1이어야 하므로 남은 칸이 1이면 단일 선택기를 쓴다. 두 런처는 조건 없이 항상 등록한다
    // (조건부로 등록하면 남은 칸이 바뀔 때·프로세스 복원 때 결과를 받을 런처가 사라질 수 있다).
    val singlePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhotos(listOf(it)) }
    }
    val multiMaxItems = state.remainingPhotoSlots.coerceAtLeast(2)
    val multiPhotoContract = remember(multiMaxItems) { ActivityResultContracts.PickMultipleVisualMedia(multiMaxItems) }
    val multiPhotoPicker = rememberLauncherForActivityResult(multiPhotoContract) { uris ->
        viewModel.addPhotos(uris)
    }

    if (state.showSoundCloudPicker) {
        SoundCloudPickerSheet(
            onPick = viewModel::applyPickedSoundCloud,
            onClose = { viewModel.setShowSoundCloudPicker(false) },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // SoundCloud 미리듣기용 숨김 WebView(1dp) — Compose 트리에 있어야 Widget JS가 동작한다.
        // key(source)로 소스 인스턴스 교체 시에만 WebView 재생성 — iOS attachment.id(ObjectIdentifier(source)) 대응.
        viewModel.player.currentSource?.let { source ->
            key(source) { source.Attachment() }
        }

        // 시스템 바 영역은 여기서 피한다. 배경(Ivory)은 위 Box가 화면 전체를 칠하고 콘텐츠만 들인다.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            // 제목은 들어온 라우트(mode)로 고정한다 — 첫 저장 뒤 VM이 Edit로 승격해도 "편지 쓰기"는 그대로(iOS와 같음).
            // navigationIcon 없음 — 화면 이탈은 시스템 뒤로로만 한다. 인셋은 위 systemBarsPadding이 이미 맡는다.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (mode) {
                                ComposeMode.New -> R.string.compose_nav_new
                                is ComposeMode.Edit -> R.string.compose_nav_edit
                                is ComposeMode.Reply -> R.string.compose_nav_reply
                            },
                        ),
                        style = MutterText.headline,
                        color = Ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
                windowInsets = WindowInsets(0),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ThemePicker(
                    themes = LetterThemes.all,
                    selectedId = state.templateId,
                    onSelect = viewModel::onTemplateChange,
                )
                PaperSection(
                    theme = theme,
                    title = state.title,
                    blocks = state.blocks,
                    photoUrls = state.photoUrls,
                    onTitleChange = viewModel::onTitleChange,
                    onTextChange = viewModel::onTextBlockChange,
                    onTextFocused = viewModel::onTextBlockFocused,
                    onRemovePhoto = viewModel::removePhoto,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MutterButton(
                        text = stringResource(R.string.compose_photo_add),
                        onClick = {
                            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (state.remainingPhotoSlots == 1) {
                                singlePhotoPicker.launch(request)
                            } else {
                                multiPhotoPicker.launch(request)
                            }
                        },
                        style = MutterButtonStyle.Secondary,
                        enabled = state.canAddPhoto,
                        isLoading = state.isAddingPhoto,
                    )
                    // 다 찼을 때만 한도 안내(iOS photoSection).
                    if (state.remainingPhotoSlots == 0) {
                        Text(
                            text = stringResource(R.string.compose_photo_limit),
                            style = MutterText.caption,
                            color = InkFaint,
                        )
                    }
                }
                MusicSection(
                    state = state,
                    isPlaying = viewModel.player.isPlaying,
                    isUnavailable = viewModel.player.isUnavailable,
                    onUrlChange = viewModel::onSoundcloudUrlChange,
                    onBrowse = { viewModel.setShowSoundCloudPicker(true) },
                    onApply = viewModel::applySoundCloudUrl,
                    onPreview = viewModel::previewAudio,
                )
                state.error?.let { msg ->
                    Text(
                        text = msg,
                        style = MutterText.caption,
                        color = GoldDeep,
                    )
                }
                ActionsSection(
                    isReply = state.isReply,
                    isSaving = state.isSaving,
                    onSend = if (state.isReply) viewModel::sendReply else viewModel::saveAndOpenSend,
                    onSave = viewModel::saveAndClose,
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// ─── 테마 피커 ────────────────────────────────────────────────────────────────

/**
 * 가로 스크롤 테마 색상 칩 — iOS templatePicker 이식.
 * 선택 시 골드 테두리, 비선택 시 테마 자체 border 색.
 */
@Composable
private fun ThemePicker(
    themes: List<LetterTheme>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(themes, key = { it.id }) { theme ->
            val isSelected = theme.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(theme.id) },
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.background)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Gold else theme.border,
                            shape = RoundedCornerShape(6.dp),
                        )
                )
                Text(
                    text = themeNameRes(theme.id)?.let { stringResource(it) } ?: theme.name,
                    style = MutterText.caption,
                    color = if (isSelected) Ink else InkSoft,
                )
            }
        }
    }
}

/** 테마 칩 이름 — iOS `theme.<id>.name`(ko/en). 모르는 id면 null(호출부가 `LetterTheme.name`으로 폴백). */
@StringRes
private fun themeNameRes(id: String): Int? = when (id) {
    "classic-serif" -> R.string.compose_theme_classic_serif
    "modern-minimal" -> R.string.compose_theme_modern_minimal
    "warm-craft" -> R.string.compose_theme_warm_craft
    "night-sky" -> R.string.compose_theme_night_sky
    "spring-day" -> R.string.compose_theme_spring_day
    "vintage-typewriter" -> R.string.compose_theme_vintage_typewriter
    "pure-space" -> R.string.compose_theme_pure_space
    else -> null
}

// ─── 편지지(paper) ─────────────────────────────────────────────────────────────

/**
 * 선택된 테마를 배경·폰트·색상에 반영한 편집 영역.
 * 제목 + 구분선 + 본문 블록(텍스트 칸·사진 카드 교차). BasicTextField로 Material 데코 없이 테마 스타일 적용.
 * iOS paper 섹션 + 블록 편집기 이식.
 */
@Composable
private fun PaperSection(
    theme: LetterTheme,
    title: String,
    blocks: List<ComposeBlock>,
    photoUrls: Map<String, String>,
    onTitleChange: (String) -> Unit,
    onTextChange: (String, String) -> Unit,
    onTextFocused: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    val titleStyle = TextStyle(
        fontFamily = theme.fontFamily,
        fontSize = theme.headingSize,
        fontWeight = FontWeight.SemiBold,
        color = theme.foreground,
    )
    val bodyStyle = TextStyle(
        fontFamily = theme.fontFamily,
        fontSize = theme.bodySize,
        lineHeight = (theme.bodySize.value * theme.lineHeightMultiplier).sp,
        color = theme.foreground,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.cornerRadius))
            .background(theme.background)
            // 종이 질감 — accent 5% 세로 그라데이션 오버레이(iOS LetterPaperTexture 대응).
            .background(
                Brush.verticalGradient(listOf(theme.accent.copy(alpha = 0.05f), Color.Transparent)),
            )
            .border(1.dp, theme.border, RoundedCornerShape(theme.cornerRadius))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 제목 필드
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            textStyle = titleStyle,
            cursorBrush = SolidColor(theme.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (title.isEmpty()) {
                        Text(stringResource(R.string.compose_title_placeholder), style = titleStyle.copy(color = theme.muted))
                    }
                    innerTextField()
                }
            },
        )

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        // 본문 블록 — 사진이 없으면 예전처럼 큰 텍스트 칸 하나, 있으면 사진 사이 칸은 한 줄 높이부터.
        val singleBlock = blocks.size == 1
        val firstId = blocks.firstOrNull()?.id
        val lastTextId = blocks.lastOrNull { it is ComposeBlock.Text }?.id
        blocks.forEach { block ->
            key(block.id) {
                when (block) {
                    is ComposeBlock.Text -> BasicTextField(
                        value = block.text,
                        onValueChange = { onTextChange(block.id, it) },
                        textStyle = bodyStyle,
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(
                                minHeight = when {
                                    singleBlock -> 260.dp
                                    block.id == lastTextId -> 120.dp
                                    else -> 28.dp
                                },
                            )
                            .onFocusChanged { if (it.isFocused) onTextFocused(block.id) },
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // 첫 칸이 비었을 때만 안내 — 사진 뒤 빈 칸에는 없다(iOS textBlock isFirst).
                                if (block.id == firstId && block.text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.compose_body_placeholder),
                                        style = bodyStyle.copy(color = theme.muted),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    is ComposeBlock.Photo -> PhotoCard(
                        photo = block.photo,
                        url = block.photo.path?.let { photoUrls[it] },
                        theme = theme,
                        onRemove = { onRemovePhoto(block.id) },
                    )
                }
            }
        }
    }
}

/** 본문 사이 사진 카드 + 우상단 삭제 버튼. 업로드 전이면 대기 바이트로, 이후엔 서명 URL로 그린다. */
@Composable
private fun PhotoCard(
    photo: LetterPhoto,
    url: String?,
    theme: LetterTheme,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        LetterPhotoImage(
            cacheKey = photo.path ?: photo.id,
            url = url,
            bytes = photo.pendingJpeg,
            width = photo.width,
            height = photo.height,
            placeholderColor = theme.accent.copy(alpha = 0.08f),
            contentDescription = stringResource(R.string.compose_photo_description),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.copy(alpha = 0.55f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.compose_photo_remove),
                tint = Ivory,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── 음악 섹션 ────────────────────────────────────────────────────────────────

/**
 * SoundCloud URL 입력 + 적용 + 미리듣기 플레이어 바.
 * iOS musicSection 이식.
 */
@Composable
private fun MusicSection(
    state: ComposeUiState,
    isPlaying: Boolean,
    isUnavailable: Boolean,
    onUrlChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onApply: () -> Unit,
    onPreview: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.compose_music_section),
            style = MutterText.captionBold,
            color = InkFaint,
        )

        // 선택된 큐 플레이어 바(골드 배경 + 미리듣기 버튼)
        val cue = state.cue
        if (cue != null) {
            // 선택된 음악 이름(명사구) — iOS appliedCueLabel. 제목 있는 SC 곡은 굽은 따옴표로 감싼다.
            val cueLabel = when (cue.source) {
                MusicSource.SOUNDCLOUD -> cue.title?.let { stringResource(R.string.compose_music_title_quoted, it) }
                    ?: stringResource(R.string.compose_music_soundcloud_track)
                MusicSource.HOSTED -> stringResource(R.string.compose_music_default)
            }
            CuePlayerBar(
                label = cueLabel,
                isPlaying = isPlaying,
                isUnavailable = isUnavailable,
                soundCloudUrl = cue.takeIf { it.source == MusicSource.SOUNDCLOUD }
                    ?.let { it.sourceUrl ?: it.ref },
                onToggle = onPreview,
            )
        }

        MutterButton(
            text = stringResource(R.string.compose_soundcloud_browse),
            onClick = onBrowse,
            style = MutterButtonStyle.Secondary,
        )

        // URL 입력 + 적용 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.soundcloudUrl,
                onValueChange = onUrlChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.compose_soundcloud_placeholder),
                        style = MutterText.bodySmall,
                    )
                },
                singleLine = true,
                textStyle = MutterText.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onApply,
                enabled = !state.isApplyingSoundCloud && state.soundcloudUrl.isNotBlank(),
            ) {
                Text(
                    text = stringResource(
                        if (state.isApplyingSoundCloud) R.string.compose_applying else R.string.compose_apply,
                    ),
                    style = MutterText.captionBold,
                    color = GoldDeep,
                )
            }
        }

        if (isUnavailable) {
            Text(
                text = stringResource(R.string.compose_music_unavailable),
                style = MutterText.caption,
                color = InkFaint,
            )
        }
    }
}

@Composable
private fun CuePlayerBar(
    label: String,
    isPlaying: Boolean,
    isUnavailable: Boolean,
    soundCloudUrl: String? = null,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoldSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 재생 중이면 이퀄라이저 애니메이션(iOS MusicPlayerBar 대응). GoldSoft 배경이라 진한 골드로.
        if (isPlaying && !isUnavailable) {
            MutterEqualizer(barColor = GoldDeep)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MutterText.bodySmall,
                color = Ink,
            )
            // SoundCloud 트랙이면 브랜드 표기 + 원곡 링크백(Widget 이용약관 준수). iOS MusicPlayerBar 대응.
            if (soundCloudUrl != null) {
                SoundCloudAttribution(sourceUrl = soundCloudUrl)
            }
        }
        if (!isUnavailable) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(36.dp),
            ) {
                MutterIcon(
                    id = if (isPlaying) DsR.drawable.pause else DsR.drawable.play,
                    contentDescription = if (isPlaying) "일시정지" else "재생",
                    tint = GoldDeep,
                )
            }
        }
    }
}

// ─── 액션 버튼 ────────────────────────────────────────────────────────────────

/**
 * 답장이면 "답장 보내기" + "임시 저장", 신규·이어쓰기면 "저장하고 보내기" + "임시 저장".
 * iOS actions 섹션 이식.
 */
@Composable
private fun ActionsSection(
    isReply: Boolean,
    isSaving: Boolean,
    onSend: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MutterButton(
            text = stringResource(if (isReply) R.string.compose_reply_send else R.string.compose_save_and_send),
            onClick = onSend,
            isLoading = isSaving,
        )
        MutterButton(
            text = stringResource(R.string.compose_draft),
            onClick = onSave,
            style = MutterButtonStyle.Secondary,
            isLoading = isSaving,
        )
    }
}
