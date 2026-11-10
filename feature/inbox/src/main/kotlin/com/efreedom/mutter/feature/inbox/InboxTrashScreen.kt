package com.efreedom.mutter.feature.inbox

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.uicomponent.component.TrashLetterRow
import com.efreedom.mutter.uicomponent.component.TrashScreenLayout
import com.efreedom.mutter.uicomponent.theme.Danger
import java.time.Instant

/**
 * 받은 편지 삭제함 화면(계약 §6.3). 받은 편지함 헤더의 휴지통 버튼으로 들어온다.
 * 완전히 삭제해도 받은 편지함에서만 지워진다 — 링크를 다시 열면 다시 담긴다(확인 문구로 알린다).
 */
@Composable
fun InboxTrashScreen(
    modifier: Modifier = Modifier,
    viewModel: InboxTrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var purgeTarget by remember { mutableStateOf<DeletedInboxItem?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // 0은 초기값이라 건너뛴다. 연속 복구면 이전 스낵바를 취소(닫음)하고 새로 띄운다.
    LaunchedEffect(state.restoredCount) {
        if (state.restoredCount > 0) snackbarHostState.showSnackbar("편지를 되살렸어요")
    }

    // 남은 일수 기준 시각은 목록이 바뀔 때 하나로 고정한다 — 행마다 now()를 부르면 경계에서 어긋난다.
    val now = remember(state.items) { Instant.now() }

    TrashScreenLayout(
        title = "받은 편지 삭제함",
        notice = "삭제한 편지는 30일 동안 보관된 뒤 받은 편지함에서 완전히 지워져요.",
        isInitialLoading = state.items.isEmpty() && (state.isLoading || !state.hasLoaded),
        isEmpty = state.items.isEmpty(),
        error = state.error,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        items(state.items, key = { it.letterId }) { item ->
            TrashLetterRow(
                title = item.title,
                detail = "보낸이: ${item.senderNickname?.takeIf { it.isNotBlank() } ?: "알 수 없음"}",
                daysUntilPurge = item.daysUntilPurge(now),
                isBusy = item.letterId in state.busyIds,
                onRestore = { viewModel.restore(item.letterId) },
                onPurge = { purgeTarget = item },
                modifier = Modifier.animateItem(),
            )
        }
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("완전히 삭제할까요?") },
            text = { Text("받은 편지함에서만 지워지고, 링크를 다시 열면 다시 담겨요.") },
            confirmButton = {
                TextButton(onClick = {
                    purgeTarget = null
                    viewModel.purge(target.letterId)
                }) {
                    Text("완전히 삭제", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) {
                    Text("취소")
                }
            },
        )
    }
}
