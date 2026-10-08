package com.efreedom.mutter.feature.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
@Composable
fun ComposeScreen(
    mode: ComposeMode,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
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

    // 시스템 Photo Picker(이미지만) — 권한 요청 없이 고른 한 장만 앱에 넘어온다.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
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

        // 상단 뒤로 헤더는 두지 않는다 — 안드로이드는 시스템 뒤로(제스처/버튼)로 이 화면을 벗어난다.
        // NavHost 목적지라 시스템 뒤로가 popBackStack을 그대로 수행하므로 별도 처리가 필요 없다.
        // 시스템 바 영역은 여기서 피한다. 배경(Ivory)은 위 Box가 화면 전체를 칠하고 콘텐츠만 들인다.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
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
                MutterButton(
                    text = if (state.isAddingPhoto) {
                        stringResource(R.string.compose_photo_adding)
                    } else {
                        stringResource(R.string.compose_photo_add, state.photoCount, LetterPhoto.MAX_PER_LETTER)
                    },
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    style = MutterButtonStyle.Secondary,
                    enabled = state.canAddPhoto,
                )
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
                    text = theme.name,
                    style = MutterText.caption,
                    color = if (isSelected) Ink else InkSoft,
                )
            }
        }
    }
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
                        Text("제목", style = titleStyle.copy(color = theme.muted))
                    }
                    innerTextField()
                }
            },
        )

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        // 본문 블록 — 사진이 없으면 예전처럼 큰 텍스트 칸 하나, 있으면 사진 사이 칸은 한 줄 높이부터.
        val singleBlock = blocks.size == 1
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
                                if (singleBlock && block.text.isEmpty()) {
                                    Text("편지를 써보세요.", style = bodyStyle.copy(color = theme.muted))
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
            text = "음악",
            style = MutterText.captionBold,
            color = InkFaint,
        )

        // 선택된 큐 플레이어 바(골드 배경 + 미리듣기 버튼)
        if (state.cue != null) {
            CuePlayerBar(
                label = state.appliedCueLabel ?: "선택된 음악",
                isPlaying = isPlaying,
                isUnavailable = isUnavailable,
                soundCloudUrl = state.cue?.takeIf { it.source == MusicSource.SOUNDCLOUD }
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
                        text = "SoundCloud 링크를 붙여넣어 주세요",
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
                    text = if (state.isApplyingSoundCloud) "적용 중..." else "적용",
                    style = MutterText.captionBold,
                    color = GoldDeep,
                )
            }
        }

        if (isUnavailable) {
            Text(
                text = "음악을 재생할 수 없어요.",
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
 * 답장이면 "답장 보내기" + "임시저장", 신규·이어쓰기면 "저장하고 보내기" + "임시저장".
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
            text = if (isReply) "답장 보내기" else "저장하고 보내기",
            onClick = onSend,
            isLoading = isSaving,
        )
        MutterButton(
            text = "임시저장",
            onClick = onSave,
            style = MutterButtonStyle.Secondary,
            isLoading = isSaving,
        )
    }
}
