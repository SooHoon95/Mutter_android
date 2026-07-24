@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.MutterImage
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
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.OnGold
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface

private enum class HomeTab { SENT, DRAFT }

/** iOS HomeView 대응. 골드 통계카드 + 편지 쓰기 + 보낸/임시저장 세그먼트 + 편지 목록. */
@Composable
fun HomeScreen(
    onCompose: () -> Unit,
    onEditDraft: (String) -> Unit,
    onOpenDelivery: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(HomeTab.SENT) }

    LaunchedEffect(Unit) { viewModel.load() }

    val displayRows = if (selectedTab == HomeTab.SENT) state.sentRows else state.draftRows

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatCard(sentCount = state.sentCount, openedCount = state.openedCount)

        MutterButton(text = "편지 쓰기", onClick = onCompose, leadingIconRes = DsR.drawable.compose)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == HomeTab.SENT,
                onClick = { selectedTab = HomeTab.SENT },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("보낸 편지") }
            SegmentedButton(
                selected = selectedTab == HomeTab.DRAFT,
                onClick = { selectedTab = HomeTab.DRAFT },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("임시저장") }
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading && displayRows.isNotEmpty(),
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
        when {
            state.isLoading && displayRows.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
            }

            displayRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MutterImage(
                            id = DsR.drawable.empty_sent,
                            modifier = Modifier.size(120.dp),
                        )
                        Text(
                            text = if (selectedTab == HomeTab.SENT) "보낸 편지가 없어요" else "임시저장 편지가 없어요",
                            style = MutterText.bodyMedium,
                            color = InkSoft,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(displayRows, key = { it.id }) { row ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.delete(row.letter.id)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = Danger.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    MutterIcon(id = DsR.drawable.trash, contentDescription = "삭제", tint = Danger)
                                }
                            },
                        ) {
                            LetterCard(
                                row = row,
                                onClick = {
                                    if (row.isSent) onOpenDelivery(row.letter.id)
                                    else onEditDraft(row.letter.id)
                                },
                            )
                        }
                    }
                }
            }
        }
        }

        state.error?.let {
            Text(text = it, style = MutterText.caption, color = Danger)
        }
    }
}

@Composable
private fun StatCard(sentCount: Int, openedCount: Int) {
    val gradientBrush = Brush.linearGradient(colors = listOf(GoldLight, BlushPink, BlushLavender))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = gradientBrush, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "보낸 편지",
                style = MutterText.caption,
                color = OnGold.copy(alpha = 0.8f),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "$sentCount", style = MutterText.display, color = OnGold)
                Text(
                    text = "통 · 읽음 $openedCount",
                    style = MutterText.bodyMediumBold,
                    color = OnGold,
                )
            }
        }
    }
}

@Composable
private fun LetterCard(row: LetterRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MutterSurface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leadingIcon = when {
                row.isSent && row.isOpened -> DsR.drawable.envelope_open
                row.isSent -> DsR.drawable.envelope
                else -> DsR.drawable.compose
            }
            MutterIcon(
                id = leadingIcon,
                contentDescription = null,
                tint = if (row.isSent && row.isOpened) Gold else InkSoft,
                size = 22.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = row.letter.title.ifEmpty { "(제목 없음)" },
                    style = MutterText.bodyMediumBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    !row.isSent -> "이어쓰기 중"
                    row.openSummary != null -> "${row.openSummary.openCount}회 열람"
                    else -> "아직 열지 않았어요"
                }
                Text(
                    text = subtitle,
                    style = MutterText.caption,
                    color = if (row.isSent && row.isOpened) Gold else InkFaint,
                )
            }

            if (row.isSent) {
                Surface(
                    color = if (row.isOpened) GoldSoft else GoldSoft.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.isOpened) {
                            MutterIcon(
                                id = DsR.drawable.read,
                                contentDescription = null,
                                tint = Gold,
                                size = 14.dp,
                            )
                        }
                        Text(
                            text = if (row.isOpened) "읽음" else "안읽음",
                            style = MutterText.caption,
                            color = if (row.isOpened) Gold else InkFaint,
                        )
                    }
                }
            } else {
                Surface(
                    color = GoldSoft.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "작성 중",
                        style = MutterText.caption,
                        color = InkSoft,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
