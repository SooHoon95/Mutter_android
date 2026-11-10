package com.efreedom.mutter.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.MutterImage
import com.efreedom.mutter.uicomponent.component.MutterSnackbarHost
import com.efreedom.mutter.uicomponent.component.showUndo
import com.efreedom.mutter.uicomponent.component.PullableFullSizeBox
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface
import com.efreedom.mutter.domain.model.InboxItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 받은함 탭 화면. 보관한 편지 목록, 탭하면 수신 화면으로 열기.
 * 헤더 오른쪽 = 받은 편지 삭제함 + 편집(계약 §6.2 — iOS의 동작 없던 톱니바퀴 자리). 편집 모드에선 카드 탭이 선택 토글이고
 * 하단 바로 여러 통을 지운다. 삭제는 즉시 서버에 보내고(삭제함 30일 보관) "되돌리기" 스낵바를 안전망으로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpen: (token: String) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 삭제함 등 다른 화면에서 돌아오면 이 컴포저블이 다시 들어오며 재실행된다 → 복구 결과가 목록에 반영된다.
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    // 삭제마다 5초 "되돌리기" 스낵바. 연속 삭제면 키가 바뀌어 이전 스낵바는 취소(닫힘)되고 새로 뜬다.
    state.undo?.let { undo ->
        LaunchedEffect(undo.key) {
            val count = undo.letterIds.size
            val message = if (count == 1) "편지를 삭제함으로 옮겼어요" else "${count}통을 삭제함으로 옮겼어요"
            try {
                if (snackbarHostState.showUndo(message)) viewModel.undoDelete(undo)
            } finally {
                viewModel.undoDismissed(undo.key)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ivory)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "받은 편지함",
                    style = MutterText.titleLarge,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenTrash, enabled = !state.isEditing) {
                    MutterIcon(
                        id = DsR.drawable.trash,
                        contentDescription = "받은 편지 삭제함",
                        tint = if (state.isEditing) InkFaint else InkSoft,
                    )
                }
                TextButton(
                    onClick = viewModel::toggleEditing,
                    enabled = state.isEditing || state.items.isNotEmpty(),
                ) {
                    Text(
                        text = if (state.isEditing) "완료" else "편집",
                        style = MutterText.bodyMediumBold,
                        color = if (state.isEditing || state.items.isNotEmpty()) Gold else InkFaint,
                    )
                }
            }

            // PullToRefreshBox엔 끄는 옵션이 없어 같은 구성(modifier + Indicator)을 풀어 쓰고 편집 모드에서 enabled=false로 끈다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullToRefresh(
                        isRefreshing = state.isLoading,
                        state = pullRefreshState,
                        enabled = !state.isEditing,
                        onRefresh = viewModel::load,
                    ),
            ) {
                if (state.items.isEmpty() && !state.isLoading) {
                    // 빈 상태 — 당겨서 새로고침이 되도록 스크롤 컨테이너로 감싼다.
                    PullableFullSizeBox {
                        InboxEmptyState()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp,
                            vertical = 8.dp,
                        ),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            SwipeToDeleteContainer(
                                enabled = !state.isEditing,
                                onDelete = { viewModel.delete(listOf(item)) },
                                modifier = Modifier.animateItem(),
                            ) {
                                LetterCard(
                                    item = item,
                                    selected = if (state.isEditing) item.letterId in state.selectedIds else null,
                                    onClick = {
                                        if (state.isEditing) viewModel.toggleSelection(item.letterId)
                                        else onOpen(item.token)
                                    },
                                )
                            }
                        }
                    }
                }
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = state.isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MutterText.caption,
                    color = Danger,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            if (state.isEditing) {
                EditBar(
                    isAllSelected = state.isAllSelected,
                    selectedCount = state.selectedIds.size,
                    onToggleSelectAll = viewModel::toggleSelectAll,
                    onDelete = viewModel::deleteSelected,
                )
            }
        }

        MutterSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

// ─── 편집 하단 바 ─────────────────────────────────────────────────────────────

/** 편집 모드 하단 바 — 왼쪽 "전체 선택"/"선택 해제", 오른쪽 "삭제 (N)"(0이면 비활성). */
@Composable
private fun EditBar(
    isAllSelected: Boolean,
    selectedCount: Int,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleSelectAll) {
            Text(text = if (isAllSelected) "선택 해제" else "전체 선택", style = MutterText.bodyMediumBold, color = Ink)
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
            Text(
                text = "삭제 ($selectedCount)",
                style = MutterText.bodyMediumBold,
                color = if (selectedCount > 0) Danger else InkFaint,
            )
        }
    }
}

// ─── 스와이프 삭제 ────────────────────────────────────────────────────────────

/** 끝→시작(오른쪽→왼쪽) 스와이프로 삭제. 배경은 카드와 같은 라운드의 Danger 면 + 휴지통 아이콘. [enabled]=false면 스와이프를 끈다(편집 모드). */
@Composable
private fun SwipeToDeleteContainer(
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Danger)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                MutterIcon(
                    id = DsR.drawable.trash,
                    contentDescription = "삭제",
                    tint = Color.White,
                    size = 20.dp,
                )
            }
        },
    ) {
        content()
    }
}

// ─── 편지 카드 ────────────────────────────────────────────────────────────────

/** [selected]가 null이 아니면 편집 모드 — 맨 앞에 선택 원을 붙인다. */
@Composable
private fun LetterCard(item: InboxItem, selected: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            SelectionCircle(selected = selected)
            Spacer(modifier = Modifier.size(12.dp))
        }

        // 봉투 아이콘 — 골드 원형
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            MutterIcon(id = DsR.drawable.envelope, contentDescription = null, tint = Gold, size = 20.dp)
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifEmpty { "제목 없는 편지" },
                style = MutterText.bodyMediumBold,
                color = Ink,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDate(item),
                style = MutterText.caption,
                color = InkSoft,
            )
        }

        // 미열람 골드 배지
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Gold),
        )
    }
}

/** 편집 모드 선택 원 — 선택되면 골드로 채우고 체크, 아니면 빈 테두리. */
@Composable
private fun SelectionCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Gold)
                else Modifier.border(width = 1.5.dp, color = InkFaint, shape = CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            MutterIcon(id = DsR.drawable.ic_check, contentDescription = "선택됨", tint = Color.White, size = 14.dp)
        }
    }
}

// ─── 빈 상태 ─────────────────────────────────────────────────────────────────

@Composable
private fun InboxEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 60.dp),
    ) {
        MutterImage(id = DsR.drawable.empty_received, modifier = Modifier.size(120.dp))
        Text(
            text = "아직 보관한 편지가 없어요",
            style = MutterText.bodyLarge,
            color = InkSoft,
        )
    }
}

// ─── 날짜 포맷 ────────────────────────────────────────────────────────────────

private val dateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("M월 d일")
    .withLocale(Locale.KOREAN)
    .withZone(ZoneId.systemDefault())

private fun formatDate(item: InboxItem): String = dateFormatter.format(item.savedAt)
