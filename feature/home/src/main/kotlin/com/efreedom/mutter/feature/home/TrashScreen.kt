package com.efreedom.mutter.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.uicomponent.component.MutterSnackbarHost
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import java.time.Instant

/**
 * 삭제함 화면(세 플랫폼 공통 계약 §3.2). 내 편지 화면 상단 휴지통 버튼으로 들어온다.
 * 행마다 복구 / 완전히 삭제(확인 다이얼로그). 돌아가면 홈이 다시 로드돼 복구한 편지가 목록에 나타난다.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Box(modifier = modifier.fillMaxSize().background(Ivory)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // navigationIcon 없음 — 화면 이탈은 시스템 뒤로(제스처/버튼)로만 한다(법적 문서 화면과 같은 규칙).
            TopAppBar(
                title = { Text(text = "삭제함", style = MutterText.headline, color = Ink) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
            )

            Text(
                text = "삭제한 편지는 30일 동안 보관된 뒤 완전히 지워져요.",
                style = MutterText.caption,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.letters.isEmpty() && (state.isLoading || !state.hasLoaded) -> {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
                    }

                    state.letters.isEmpty() -> {
                        Text(
                            text = "삭제함이 비어 있어요",
                            style = MutterText.bodyMedium,
                            color = InkSoft,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        // 남은 일수 기준 시각은 화면 진입(재구성) 시점 하나로 고정한다 — 행마다 now()를 부르면 경계에서 어긋난다.
                        val now = remember(state.letters) { Instant.now() }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.letters, key = { it.id }) { letter ->
                                TrashRow(
                                    letter = letter,
                                    now = now,
                                    isBusy = letter.id in state.busyIds,
                                    onRestore = { viewModel.restore(letter.id) },
                                    onPurge = { purgeTarget = letter },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MutterText.caption,
                    color = Danger,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        MutterSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
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

@Composable
private fun TrashRow(
    letter: DeletedLetter,
    now: Instant,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MutterSurface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = letter.title.ifBlank { "제목 없음" },
                style = MutterText.bodyMediumBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            val days = letter.daysUntilPurge(now)
            Text(
                text = if (days <= 0) "오늘 완전히 삭제" else "${days}일 후 완전히 삭제",
                style = MutterText.caption,
                color = InkFaint,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRestore, enabled = !isBusy) {
                    Text("복구", color = if (isBusy) InkFaint else Gold)
                }
                TextButton(onClick = onPurge, enabled = !isBusy) {
                    Text("완전히 삭제", color = if (isBusy) InkFaint else Danger)
                }
            }
        }
    }
}
