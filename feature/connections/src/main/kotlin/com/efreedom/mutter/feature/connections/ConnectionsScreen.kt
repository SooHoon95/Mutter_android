@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.connections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.shareText
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.theme.InkMid
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.model.Connection

/** iOS ConnectionsView 대응. N:N 연결 목록 + 초대 링크 생성/복사/취소. */
@Composable
fun ConnectionsScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var disconnectTarget by remember { mutableStateOf<Connection?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::load,
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "연결",
                style = MutterText.titleLarge,
                color = Ink,
            )

            if (state.connections.isNotEmpty()) {
                ConnectionsCard(
                    connections = state.connections,
                    onDisconnectRequest = { disconnectTarget = it },
                )
            }

            InviteCard(
                inviteToken = state.inviteToken,
                hasConnections = state.connections.isNotEmpty(),
                isLoading = state.isLoading,
                onCreateInvite = viewModel::createInvite,
                onRevokeInvite = viewModel::revokeInvite,
                onCopyLink = { link -> copyToClipboard(context, link) },
                onShareLink = { link -> shareText(context, link) },
            )

            state.error?.let {
                Text(text = it, style = MutterText.caption, color = GoldDeep)
            }
        }
    }

    // 연결 해제 확인 다이얼로그
    disconnectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { disconnectTarget = null },
            title = { Text("연결을 해제할까요?") },
            text = { Text("${target.nickname ?: "상대방"}과의 연결이 해제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect(target.userId)
                        disconnectTarget = null
                    },
                ) { Text("해제", color = GoldDeep) }
            },
            dismissButton = {
                TextButton(onClick = { disconnectTarget = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun ConnectionsCard(
    connections: List<Connection>,
    onDisconnectRequest: (Connection) -> Unit,
) {
    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "연결된 사람", style = MutterText.bodyLargeBold, color = Ink)
            connections.forEach { connection ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MutterIcon(
                        id = DsR.drawable.connect,
                        contentDescription = null,
                        tint = Gold,
                        size = 20.dp,
                    )
                    Text(
                        text = connection.nickname ?: "이름 없음",
                        style = MutterText.bodyMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onDisconnectRequest(connection) }) {
                        Text(text = "해제", style = MutterText.caption, color = GoldDeep)
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    inviteToken: String?,
    hasConnections: Boolean,
    isLoading: Boolean,
    onCreateInvite: () -> Unit,
    onRevokeInvite: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
) {
    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (hasConnections) "더 연결하기" else "연결이 없어요",
                style = MutterText.bodyLargeBold,
                color = Ink,
            )
            Text(
                text = "초대 링크를 만들어 친구에게 공유해 보세요.",
                style = MutterText.bodyMedium,
                color = InkSoft,
            )

            if (inviteToken != null) {
                val link = AppLink.connectInvite(inviteToken)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Ivory, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = link,
                        style = MutterText.caption,
                        color = InkMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onCopyLink(link) }) {
                        Text("복사", style = MutterText.captionBold, color = GoldDeep)
                    }
                    TextButton(onClick = { onShareLink(link) }) {
                        Text("공유", style = MutterText.captionBold, color = GoldDeep)
                    }
                }

                MutterButton(
                    text = "초대 취소",
                    onClick = onRevokeInvite,
                    style = MutterButtonStyle.Ghost,
                    isLoading = isLoading,
                )
            } else {
                MutterButton(
                    text = "초대 링크 만들기",
                    onClick = onCreateInvite,
                    isLoading = isLoading,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("invite_link", text))
}

/** iOS ShareLink 대응 — OS 공유 시트(메시지/카카오톡 등)로 초대 링크를 내보낸다. */
