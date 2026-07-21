package com.efreedom.mutter.feature.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkMid
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface
import kotlinx.coroutines.delay

/** 세그먼트 탭 — 링크 발급 / 연결 상대 직접 발송. */
private enum class SendMethod { LINK, CONNECTION }

/**
 * 저장 직후 뜨는 "보내기" 시트 — 두 방식: 전달 링크 발급 / 연결된 사람 직접 발송.
 * iOS SendSheet(ComposeModelData 바인딩) 이식. ComposeViewModel을 직접 소비한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendSheet(
    viewModel: ComposeViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var method by remember { mutableStateOf(SendMethod.LINK) }
    var linkCopied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // linkCopied 자동 복구 (2초 후 원복). 시트는 닫지 않는다 — 닫기는 "완료"가 담당.
    LaunchedEffect(linkCopied) {
        if (linkCopied) {
            delay(2_000)
            linkCopied = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 커스텀 세그먼트 컨트롤 — iOS Capsule 스타일 이식.
            SendMethodSelector(
                selected = method,
                onSelect = { method = it },
            )

            when (method) {
                SendMethod.LINK -> LinkTab(
                    state = state,
                    linkCopied = linkCopied,
                    onTogglePassword = viewModel::setUsePassword,
                    onPasswordChange = viewModel::setPassword,
                    onIssueLink = viewModel::issueLink,
                    onCopyLink = { link ->
                        clipboard.setText(AnnotatedString(link))
                        linkCopied = true
                    },
                    onDone = viewModel::finishSend,
                )
                SendMethod.CONNECTION -> ConnectionTab(
                    state = state,
                    onSend = viewModel::sendToConnection,
                )
            }

            // 에러는 탭 아래에 공통 표시
            state.error?.let { msg ->
                Text(
                    text = msg,
                    style = MutterText.caption,
                    color = GoldDeep,
                )
            }
        }
    }
}

// ─── 세그먼트 컨트롤 ──────────────────────────────────────────────────────────

@Composable
private fun SendMethodSelector(
    selected: SendMethod,
    onSelect: (SendMethod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(GoldSoft)
            .padding(4.dp),
    ) {
        listOf(SendMethod.LINK to "링크", SendMethod.CONNECTION to "연결").forEach { (m, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .then(if (selected == m) Modifier.background(Surface) else Modifier)
                    .clickable { onSelect(m) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MutterText.bodyMediumBold,
                    color = if (selected == m) Ink else InkSoft,
                )
            }
        }
    }
}

// ─── 링크 탭 ─────────────────────────────────────────────────────────────────

/**
 * 전달 링크 발급 탭.
 * 암호 보호 카드 → (ON이면) 암호 입력 → 발급 → 링크 미리보기 + 복사/완료.
 * iOS linkSection 이식.
 */
@Composable
private fun LinkTab(
    state: ComposeUiState,
    linkCopied: Boolean,
    onTogglePassword: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onIssueLink: () -> Unit,
    onCopyLink: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 암호 보호 카드
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Gold)
            Column(modifier = Modifier.weight(1f)) {
                Text("암호 보호", style = MutterText.bodyMediumBold, color = Ink)
                Text("수신자에게 암호를 전달해 주세요", style = MutterText.caption, color = InkSoft)
            }
            Switch(
                checked = state.usePassword,
                onCheckedChange = onTogglePassword,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Surface,
                    checkedTrackColor = Gold,
                ),
            )
        }

        // 암호 입력 필드(암호 보호 ON 시)
        if (state.usePassword) {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("암호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 발급된 링크 미리보기
        if (state.issuedLink != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = state.issuedLink,
                    style = MutterText.caption,
                    color = InkMid,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onCopyLink(state.issuedLink) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (linkCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = if (linkCopied) "복사됨" else "복사",
                        tint = Gold,
                    )
                }
            }
        }

        // CTA 버튼
        if (state.issuedLink != null) {
            MutterButton(
                text = if (linkCopied) "복사됐어요" else "링크 복사",
                onClick = { onCopyLink(state.issuedLink) },
                leadingIcon = if (linkCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            )
            MutterButton(
                text = "완료",
                onClick = onDone,
                style = MutterButtonStyle.Ghost,
            )
        } else {
            MutterButton(
                text = "링크 발급",
                onClick = onIssueLink,
                isLoading = state.isIssuing,
                enabled = state.canIssueLink,
                leadingIcon = Icons.Filled.Link,
            )
        }
    }
}

// ─── 연결 탭 ─────────────────────────────────────────────────────────────────

/**
 * 연결된 상대 목록 — 보내기 버튼으로 직접 발송.
 * iOS connectionSection 이식.
 */
@Composable
private fun ConnectionTab(
    state: ComposeUiState,
    onSend: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.connections.isEmpty()) {
            Text(
                text = "아직 연결된 사람이 없어요",
                style = MutterText.captionBold,
                color = InkFaint,
            )
            Text(
                text = "연결 탭에서 친구를 초대해 보세요.",
                style = MutterText.caption,
                color = InkSoft,
            )
        } else {
            Text(
                text = "연결된 사람에게 보내기",
                style = MutterText.captionBold,
                color = InkFaint,
            )
            state.connections.forEach { connection ->
                ConnectionRow(
                    nickname = connection.nickname,
                    isSending = state.isSending,
                    onSend = { onSend(connection.userId) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    nickname: String?,
    isSending: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 아바타: 이름 첫 글자 원형 — iOS connectionRow 대응.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (nickname ?: "?").take(1),
                style = MutterText.bodyLargeBold,
                color = GoldDeep,
            )
        }

        Text(
            text = nickname ?: "이름 없는 친구",
            style = MutterText.bodyMediumBold,
            color = Ink,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onSend,
            enabled = !isSending,
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "보내기",
                tint = Gold,
            )
        }
    }
}
