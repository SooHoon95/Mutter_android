@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.connections

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText

/** iOS ConnectInviteView 대응. 초대 링크(/connect/:token) 열람·수락 화면. */
@Composable
fun ConnectInviteScreen(
    token: String,
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(token) { viewModel.start(token) }

    // 이 화면에서 방금 수락했을 때만 닫는다. 처리 표시를 먼저 해서 pop 직후 코루틴이 취소·재시작돼도 두 번 닫지 않는다.
    LaunchedEffect(state) {
        if ((state as? ConnectInviteUiState.Accepted)?.justAccepted == true) {
            viewModel.onAcceptedHandled()
            onAccepted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // navigationIcon 없음 — 화면 이탈은 시스템 뒤로로만 한다(보낸 편지 삭제함·법적 문서 화면과 같은 규칙).
        TopAppBar(
            title = { Text(text = stringResource(R.string.invite_title), style = MutterText.headline, color = Ink) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.widthIn(max = 420.dp), contentAlignment = Alignment.Center) {
                when (val s = state) {
                    is ConnectInviteUiState.Loading -> CircularProgressIndicator(color = Gold)

                    is ConnectInviteUiState.Ready -> InviteReadyContent(invite = s.invite, onAccept = viewModel::accept)

                    is ConnectInviteUiState.Accepted -> InviteMessage(
                        icon = DsR.drawable.check_circle,
                        title = stringResource(R.string.invite_connected_title),
                        detail = stringResource(R.string.invite_connected_detail),
                    )

                    is ConnectInviteUiState.Failed -> FailedContent(message = s.message, onRetry = viewModel::retry)
                }
            }
        }
    }
}

@Composable
private fun InviteReadyContent(invite: ConnectInvite, onAccept: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MutterIcon(id = DsR.drawable.connect, contentDescription = null, tint = Gold, size = 40.dp)
        Text(
            text = stringResource(
                R.string.invite_request,
                invite.inviterNickname ?: stringResource(R.string.invite_someone),
            ),
            style = MutterText.title,
            color = Ink,
            textAlign = TextAlign.Center,
        )

        if (invite.canAccept) {
            MutterButton(text = stringResource(R.string.invite_connect), onClick = onAccept)
        } else {
            // 서버 N:N(0027)을 따라 "이미 다른 사람과 연결" 사유는 두지 않는다 — 수락을 막는 건 자기 초대·같은 상대뿐.
            val reason = when {
                invite.isSelf -> R.string.invite_self
                invite.alreadyConnected -> R.string.invite_already_connected
                else -> R.string.invite_unavailable
            }
            Text(
                text = stringResource(reason),
                style = MutterText.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 로드·수락 실패 — 경고 메시지와 다시 시도(iOS failed). */
@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        InviteMessage(
            icon = DsR.drawable.warning,
            title = stringResource(R.string.invite_failed_title),
            detail = message,
        )
        MutterButton(
            text = stringResource(R.string.invite_retry),
            onClick = onRetry,
            style = MutterButtonStyle.Ghost,
        )
    }
}

/** iOS `message(icon:title:detail:)` — 아이콘 40 gold, 제목 title·ink, 상세 bodyMedium·inkSoft 가운데. */
@Composable
private fun InviteMessage(@DrawableRes icon: Int, title: String, detail: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MutterIcon(id = icon, contentDescription = null, tint = Gold, size = 40.dp)
        Text(text = title, style = MutterText.title, color = Ink, textAlign = TextAlign.Center)
        Text(text = detail, style = MutterText.bodyMedium, color = InkSoft, textAlign = TextAlign.Center)
    }
}
