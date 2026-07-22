@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.threads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.MutterImage
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.LetterDirection
import com.efreedom.mutter.domain.model.ThreadLetter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** iOS ThreadsView 대응. 주고받은 상대 목록 + 선택 상대와의 편지 흐름(ModalBottomSheet). */
@Composable
fun ThreadsScreen(
    onReply: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThreadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.counterparts.isNotEmpty(),
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        ) {
        when {
            state.isLoading && state.counterparts.isEmpty() -> {
                CircularProgressIndicator(
                    color = Gold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.counterparts.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MutterImage(id = DsR.drawable.empty_connections, modifier = Modifier.size(120.dp))
                    Text(text = "아직 주고받은 편지가 없어요", style = MutterText.bodyLarge, color = InkSoft)
                    Text(
                        text = "편지를 보내면 여기서 대화를 볼 수 있어요",
                        style = MutterText.caption,
                        color = InkFaint,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 16.dp,
                    ),
                ) {
                    items(state.counterparts, key = { it.id }) { counterpart ->
                        CounterpartRow(
                            counterpart = counterpart,
                            onClick = { viewModel.openThread(counterpart) },
                        )
                    }
                }
            }
        }
        }
    }

    // 스레드 시트
    if (state.selectedCounterpart != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeThread,
            sheetState = sheetState,
            containerColor = Ivory,
        ) {
            ThreadSheetContent(
                state = state,
                onRetry = { state.selectedCounterpart?.let { viewModel.openThread(it) } },
                onReply = { counterpart ->
                    viewModel.closeThread()
                    onReply(counterpart.userId)
                },
                onOpen = { token ->
                    viewModel.closeThread()
                    onOpen(token)
                },
            )
        }
    }
}

@Composable
private fun CounterpartRow(counterpart: Counterpart, onClick: () -> Unit) {
    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, Hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 아바타 원 — 이니셜
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = GoldSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (counterpart.nickname ?: "?").take(1),
                    style = MutterText.bodyLargeBold,
                    color = GoldDeep,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = counterpart.nickname ?: "이름 없음",
                    style = MutterText.bodyLargeBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${counterpart.exchangeCount}통 주고받음",
                    style = MutterText.caption,
                    color = InkFaint,
                )
            }

            MutterIcon(id = DsR.drawable.ic_chevron_right, contentDescription = null, tint = InkFaint, size = 16.dp)
        }
    }
}

@Composable
private fun ThreadSheetContent(
    state: ThreadsUiState,
    onRetry: () -> Unit,
    onReply: (Counterpart) -> Unit,
    onOpen: (String) -> Unit,
) {
    val counterpart = state.selectedCounterpart ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = GoldSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (counterpart.nickname ?: "?").take(1),
                    style = MutterText.captionBold,
                    color = GoldDeep,
                )
            }
            Text(
                text = counterpart.nickname ?: "편지",
                style = MutterText.headline,
                color = Ink,
            )
        }

        Divider(color = Hairline)

        // 본문 — 로딩/에러/빈/목록
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 8.dp),
        ) {
            when {
                state.isThreadLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Gold)
                    }
                }

                state.threadError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = state.threadError, style = MutterText.bodyMedium, color = InkSoft)
                        MutterButton(
                            text = "다시 시도",
                            onClick = onRetry,
                            style = MutterButtonStyle.Ghost,
                        )
                    }
                }

                state.thread.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "주고받은 편지가 없어요",
                            style = MutterText.bodyMedium,
                            color = InkFaint,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp,
                            vertical = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.thread, key = { it.id }) { letter ->
                            ThreadBubble(letter = letter, onOpen = onOpen)
                        }
                    }
                }
            }
        }

        // 하단 편지 쓰기 바
        Divider(color = Hairline)
        val buttonTitle = counterpart.nickname?.let { "${it}에게 편지 쓰기" } ?: "편지 쓰기"
        MutterButton(
            text = buttonTitle,
            onClick = { onReply(counterpart) },
            leadingIconRes = DsR.drawable.compose,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ThreadBubble(letter: ThreadLetter, onOpen: (String) -> Unit) {
    val isSent = letter.direction == LetterDirection.SENT
    Row(modifier = Modifier.fillMaxWidth()) {
        if (isSent) Spacer(modifier = Modifier.weight(1f))

        Surface(
            color = if (isSent) GoldSoft else MutterSurface,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isSent) 12.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 12.dp,
            ),
            shadowElevation = 1.dp,
            // iOS는 받은 말풍선에만 헤어라인 테두리(strokeBorder hairline 0.5).
            border = if (!isSent) BorderStroke(0.5.dp, Hairline) else null,
            modifier = Modifier
                .weight(0.75f, fill = false)
                .then(
                    letter.token?.takeIf { !isSent }?.let { token ->
                        Modifier.clickable { onOpen(token) }
                    } ?: Modifier,
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutterIcon(
                    id = if (isSent) DsR.drawable.envelope_open else DsR.drawable.envelope,
                    contentDescription = null,
                    tint = if (isSent) GoldDeep else Gold,
                    size = 16.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = letter.title.ifEmpty { "편지" },
                        style = MutterText.bodyMediumBold,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = letter.sentAt.toRelativeDisplayTime(),
                        style = MutterText.caption,
                        color = InkFaint,
                    )
                }
            }
        }

        if (!isSent) Spacer(modifier = Modifier.weight(1f))
    }
}

private fun java.time.Instant.toRelativeDisplayTime(): String {
    val nowEpoch = java.time.Instant.now().epochSecond
    val diffSec = nowEpoch - this.epochSecond
    return when {
        diffSec < 60 -> "방금"
        diffSec < 3600 -> "${diffSec / 60}분 전"
        diffSec < 86400 -> "${diffSec / 3600}시간 전"
        else -> DateTimeFormatter.ofPattern("MM.dd").withZone(ZoneId.systemDefault()).format(this)
    }
}
