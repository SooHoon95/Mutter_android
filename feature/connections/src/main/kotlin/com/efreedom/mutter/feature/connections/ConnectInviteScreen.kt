package com.efreedom.mutter.feature.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import com.efreedom.mutter.domain.model.ConnectInvite

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

    // 수락 완료 시 콜백 호출
    LaunchedEffect(state) {
        if (state is ConnectInviteUiState.Accepted) onAccepted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is ConnectInviteUiState.Loading -> {
                CircularProgressIndicator(color = Gold)
            }

            is ConnectInviteUiState.Ready -> {
                InviteReadyContent(invite = s.invite, onAccept = viewModel::accept)
            }

            is ConnectInviteUiState.Accepted -> {
                AcceptedContent()
            }

            is ConnectInviteUiState.Failed -> {
                FailedContent(message = s.message, onRetry = { viewModel.start(token) })
            }
        }
    }
}

@Composable
private fun InviteReadyContent(invite: ConnectInvite, onAccept: () -> Unit) {
    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "연결 초대",
                style = MutterText.titleLarge,
                color = Ink,
            )
            Text(
                text = "${invite.inviterNickname ?: "누군가"}님이 연결을 요청했어요.",
                style = MutterText.bodyMedium,
                color = InkSoft,
            )

            when {
                invite.isSelf -> {
                    Text(
                        text = "내가 만든 초대 링크예요.",
                        style = MutterText.bodySmall,
                        color = InkFaint,
                    )
                }
                invite.alreadyConnected -> {
                    Text(
                        text = "이미 연결된 분이에요.",
                        style = MutterText.bodySmall,
                        color = Gold,
                    )
                }
                invite.viewerHasConnection || invite.inviterHasConnection -> {
                    Text(
                        text = "현재 연결이 있어 수락할 수 없어요.",
                        style = MutterText.bodySmall,
                        color = InkFaint,
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    MutterButton(text = "수락하기", onClick = onAccept, enabled = invite.canAccept)
                }
            }
        }
    }
}

@Composable
private fun AcceptedContent() {
    Surface(
        color = GoldSoft,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "연결 완료", style = MutterText.titleLarge, color = GoldDeep)
            Text(
                text = "이제 편지를 주고받을 수 있어요.",
                style = MutterText.bodyMedium,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = message, style = MutterText.bodyMedium, color = Danger)
        MutterButton(
            text = "다시 시도",
            onClick = onRetry,
            style = MutterButtonStyle.Ghost,
        )
    }
}
