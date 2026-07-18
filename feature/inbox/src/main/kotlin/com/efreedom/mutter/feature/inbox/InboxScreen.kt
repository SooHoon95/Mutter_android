package com.efreedom.mutter.feature.inbox

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.core.designsystem.theme.Gold
import com.efreedom.mutter.core.designsystem.theme.GoldSoft
import com.efreedom.mutter.core.designsystem.theme.Ink
import com.efreedom.mutter.core.designsystem.theme.InkFaint
import com.efreedom.mutter.core.designsystem.theme.InkSoft
import com.efreedom.mutter.core.designsystem.theme.Ivory
import com.efreedom.mutter.core.designsystem.theme.MutterText
import com.efreedom.mutter.core.designsystem.theme.Surface
import com.efreedom.mutter.domain.model.InboxItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 받은함 탭 화면. 보관한 편지 목록, 탭하면 수신 화면으로 열기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpen: (token: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "받은 편지함",
                style = MutterText.titleLarge,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::load,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty() && !state.isLoading) {
                // 빈 상태
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
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
                        LetterCard(item = item, onClick = { onOpen(item.token) })
                    }
                }
            }
        }
    }
}

// ─── 편지 카드 ────────────────────────────────────────────────────────────────

@Composable
private fun LetterCard(item: InboxItem, onClick: () -> Unit) {
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
        // 봉투 아이콘 — 골드 원형
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✉", style = MutterText.bodyMedium, color = Gold)
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

// ─── 빈 상태 ─────────────────────────────────────────────────────────────────

@Composable
private fun InboxEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 60.dp),
    ) {
        Text(text = "✉", style = MutterText.display, color = InkFaint)
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
