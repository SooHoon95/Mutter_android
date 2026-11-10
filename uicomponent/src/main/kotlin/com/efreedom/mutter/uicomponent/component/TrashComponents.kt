@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.uicomponent.component

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface

/**
 * 삭제함 화면 공통 틀(보낸 편지 삭제함·받은 편지 삭제함). 상단 제목 + 안내 문구 + 목록/로딩/빈 상태 + 에러 + 스낵바.
 * 화면별 차이(행 내용·RPC·확인 문구)는 호출부가 [rows]와 다이얼로그로 채운다.
 */
@Composable
fun TrashScreenLayout(
    title: String,
    notice: String,
    isInitialLoading: Boolean,
    isEmpty: Boolean,
    error: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    rows: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Ivory)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // navigationIcon 없음 — 화면 이탈은 시스템 뒤로(제스처/버튼)로만 한다(법적 문서 화면과 같은 규칙).
            TopAppBar(
                title = { Text(text = title, style = MutterText.headline, color = Ink) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
            )

            Text(
                text = notice,
                style = MutterText.caption,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    isInitialLoading -> {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
                    }

                    isEmpty -> {
                        Text(
                            text = "삭제함이 비어 있어요",
                            style = MutterText.bodyMedium,
                            color = InkSoft,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            content = rows,
                        )
                    }
                }
            }

            error?.let {
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
}

/**
 * 삭제함 행 — 제목(없으면 "제목 없음"), 보조 줄([detail], 예: 보낸이), 남은 보관 기간, 복구/완전히 삭제 버튼.
 * [daysUntilPurge]가 0 이하면 "오늘 완전히 삭제"(세 플랫폼 공통 문구).
 */
@Composable
fun TrashLetterRow(
    title: String,
    daysUntilPurge: Int,
    isBusy: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
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
                text = title.ifBlank { "제목 없음" },
                style = MutterText.bodyMediumBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            detail?.let {
                Text(
                    text = it,
                    style = MutterText.caption,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (daysUntilPurge <= 0) "오늘 완전히 삭제" else "${daysUntilPurge}일 후 완전히 삭제",
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
