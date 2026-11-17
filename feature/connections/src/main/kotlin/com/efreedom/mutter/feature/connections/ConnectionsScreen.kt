@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.connections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.shareText
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Danger
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

    // 진입(첫 RESUMED)과 앱 복귀 때마다 조용히 다시 받는다 — 상대가 연결을 끊었을 때 최신 상태 반영(iOS scenePhase .active).
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
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
                text = stringResource(R.string.connections_title),
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
            title = { Text(stringResource(R.string.connections_disconnect_confirm)) },
            text = { Text(stringResource(R.string.connections_disconnect_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect(target.userId)
                        disconnectTarget = null
                    },
                ) { Text(stringResource(R.string.connections_disconnect), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { disconnectTarget = null }) {
                    Text(stringResource(R.string.connections_cancel))
                }
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
            Text(
                text = stringResource(R.string.connections_connected),
                style = MutterText.bodyLargeBold,
                color = Ink,
            )
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
                        text = connection.nickname ?: stringResource(R.string.connections_person_fallback),
                        style = MutterText.bodyMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onDisconnectRequest(connection) }) {
                        Text(
                            text = stringResource(R.string.connections_disconnect),
                            style = MutterText.caption,
                            color = GoldDeep,
                        )
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
                text = stringResource(
                    if (hasConnections) R.string.connections_more else R.string.connections_empty,
                ),
                style = MutterText.bodyLargeBold,
                color = Ink,
            )
            Text(
                text = stringResource(R.string.connections_invite_hint),
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
                    // iOS처럼 복사·공유는 아이콘 버튼(20, gold).
                    IconButton(onClick = { onCopyLink(link) }) {
                        MutterIcon(
                            id = DsR.drawable.copy,
                            contentDescription = stringResource(R.string.connections_copy),
                            tint = Gold,
                            size = 20.dp,
                        )
                    }
                    IconButton(onClick = { onShareLink(link) }) {
                        MutterIcon(
                            id = DsR.drawable.share,
                            contentDescription = stringResource(R.string.connections_share),
                            tint = Gold,
                            size = 20.dp,
                        )
                    }
                }

                MutterButton(
                    text = stringResource(R.string.connections_invite_cancel),
                    onClick = onRevokeInvite,
                    style = MutterButtonStyle.Ghost,
                    isLoading = isLoading,
                )
            } else {
                MutterButton(
                    text = stringResource(R.string.connections_invite_create),
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
