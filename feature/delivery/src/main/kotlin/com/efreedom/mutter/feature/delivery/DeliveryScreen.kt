@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.delivery

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
import com.efreedom.mutter.uicomponent.theme.InkMid
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import com.efreedom.mutter.domain.model.DeliveryLink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DELIVERY_BASE_URL = "https://letter-app-nine-kohl.vercel.app"

/** iOS DeliveryView 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@Composable
fun DeliveryScreen(
    letterId: String,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeliveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(letterId) { viewModel.start(letterId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 뒤로", style = MutterText.bodyMedium, color = Ink)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = "편지 보내기", style = MutterText.titleLarge, color = Ink)

            MutterButton(
                text = "미리보기",
                onClick = onPreview,
                style = MutterButtonStyle.Ghost,
            )

            IssueCard(
                state = state,
                onUsePasswordChange = viewModel::setUsePassword,
                onPasswordChange = viewModel::setPassword,
                onUseRevealChange = viewModel::setUseReveal,
                onRevealAtChange = viewModel::setRevealAt,
                onIssue = viewModel::issue,
            )

            state.lastIssuedToken?.let { token ->
                IssuedLinkRow(
                    token = token,
                    onCopy = { copyToClipboard(context, "$DELIVERY_BASE_URL/l/$token") },
                )
            }

            if (state.links.isNotEmpty()) {
                ExistingLinksSection(
                    links = state.links,
                    onCopy = { token -> copyToClipboard(context, "$DELIVERY_BASE_URL/l/$token") },
                    onRevoke = viewModel::revoke,
                )
            }

            state.error?.let {
                Text(text = it, style = MutterText.caption, color = GoldDeep)
            }
        }
    }
}

@Composable
private fun IssueCard(
    state: DeliveryUiState,
    onUsePasswordChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUseRevealChange: (Boolean) -> Unit,
    onRevealAtChange: (Instant) -> Unit,
    onIssue: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 암호 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "암호 설정", style = MutterText.bodyMediumBold, color = Ink)
                Switch(
                    checked = state.usePassword,
                    onCheckedChange = onUsePasswordChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                )
            }

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

            // 예약 공개 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "예약 공개", style = MutterText.bodyMediumBold, color = Ink)
                Switch(
                    checked = state.useReveal,
                    onCheckedChange = onUseRevealChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                )
            }

            if (state.useReveal) {
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "공개 날짜: ${state.revealAt.toDateDisplay()}",
                        style = MutterText.bodySmall,
                        color = GoldDeep,
                    )
                }
            }

            MutterButton(
                text = "링크 발급",
                onClick = onIssue,
                enabled = state.canIssue,
                isLoading = state.isLoading,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.revealAt.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            onRevealAtChange(Instant.ofEpochMilli(it))
                        }
                        showDatePicker = false
                    },
                ) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun IssuedLinkRow(token: String, onCopy: () -> Unit) {
    val link = "$DELIVERY_BASE_URL/l/$token"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = GoldSoft, shape = RoundedCornerShape(8.dp))
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
        TextButton(onClick = onCopy) {
            Text("복사", style = MutterText.captionBold, color = GoldDeep)
        }
    }
}

@Composable
private fun ExistingLinksSection(
    links: List<DeliveryLink>,
    onCopy: (String) -> Unit,
    onRevoke: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = "발급된 링크", style = MutterText.bodyMediumBold, color = InkSoft)
        links.forEach { link ->
            Surface(
                color = MutterSurface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = link.token.take(10) + "…",
                            style = MutterText.caption,
                            color = InkMid,
                        )
                        val badgeText = buildString {
                            if (link.hasPassword) append("암호 ")
                            if (link.revealAt != null) append("예약공개 ")
                            if (link.revoked) append("· 취소됨")
                        }.trim()
                        if (badgeText.isNotEmpty()) {
                            Text(text = badgeText, style = MutterText.caption, color = InkFaint)
                        }
                    }

                    if (!link.revoked) {
                        TextButton(onClick = { onCopy(link.token) }) {
                            Text("복사", style = MutterText.caption, color = Gold)
                        }
                        TextButton(onClick = { onRevoke(link.token) }) {
                            Text("취소", style = MutterText.captionBold, color = GoldDeep)
                        }
                    } else {
                        Text(text = "취소됨", style = MutterText.caption, color = InkFaint)
                    }
                }
            }
        }
    }
}

private fun Instant.toDateDisplay(): String =
    DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault()).format(this)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("delivery_link", text))
}
