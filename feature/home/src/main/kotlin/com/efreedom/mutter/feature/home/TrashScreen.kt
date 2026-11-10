package com.efreedom.mutter.feature.home

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
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.uicomponent.component.TrashLetterRow
import com.efreedom.mutter.uicomponent.component.TrashScreenLayout
import com.efreedom.mutter.uicomponent.theme.Danger
import java.time.Instant

/**
 * 보낸 편지 삭제함 화면(세 플랫폼 공통 계약 §3.2). 내 편지 화면 상단 휴지통 버튼으로 들어온다.
 * 행마다 복구 / 완전히 삭제(확인 다이얼로그). 돌아가면 홈이 다시 로드돼 복구한 편지가 목록에 나타난다.
 */
@Composable
fun TrashScreen(
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var purgeTarget by remember { mutableStateOf<DeletedLetter?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    // 0은 초기값이라 건너뛴다. 연속 복구면 이전 스낵바를 취소(닫음)하고 새로 띄운다.
    LaunchedEffect(state.restoredCount) {
        if (state.restoredCount > 0) snackbarHostState.showSnackbar("편지를 되살렸어요")
    }

    // 남은 일수 기준 시각은 목록이 바뀔 때 하나로 고정한다 — 행마다 now()를 부르면 경계에서 어긋난다.
    val now = remember(state.letters) { Instant.now() }

    TrashScreenLayout(
        title = "보낸 편지 삭제함",
        notice = "삭제한 편지는 30일 동안 보관된 뒤 완전히 지워져요.",
        isInitialLoading = state.letters.isEmpty() && (state.isLoading || !state.hasLoaded),
        isEmpty = state.letters.isEmpty(),
        error = state.error,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        items(state.letters, key = { it.id }) { letter ->
            TrashLetterRow(
                title = letter.title,
                daysUntilPurge = letter.daysUntilPurge(now),
                isBusy = letter.id in state.busyIds,
                onRestore = { viewModel.restore(letter.id) },
                onPurge = { purgeTarget = letter },
                modifier = Modifier.animateItem(),
            )
        }
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("완전히 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    purgeTarget = null
                    viewModel.purge(target.id)
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
