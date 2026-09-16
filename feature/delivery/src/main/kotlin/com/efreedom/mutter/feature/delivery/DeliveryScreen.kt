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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.efreedom.mutter.uicomponent.component.shareText
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
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.LetterShareMessage
import com.efreedom.mutter.domain.model.DeliveryLink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** iOS DeliveryView 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@Composable
fun DeliveryScreen(
    letterId: String,
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
            .background(Ivory)
            // background 뒤에 둬야 배경은 화면 전체를 칠하고 콘텐츠만 인셋만큼 들어간다.
            // 아래 상단 바는 순수 Row라(M3 TopAppBar가 아니라) 직접 넣지 않으면 상태바와 겹친다.
            .systemBarsPadding(),
    ) {
        // 상단 뒤로 헤더는 두지 않는다 — 안드로이드는 시스템 뒤로(제스처/버튼)로 이 화면을 벗어난다.
        // NavHost 목적지라 시스템 뒤로가 popBackStack을 그대로 수행한다.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        ) {
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

                state.lastIssuedLink?.let { link ->
                    IssuedLinkRow(
                        link = link,
                        onCopy = { copyToClipboard(context, AppLink.letter(link.token)) },
                        onShare = { shareText(context, shareMessage(link)) },
                    )
                }

                if (state.links.isNotEmpty()) {
                    ExistingLinksSection(
                        links = state.links,
                        onCopy = { token -> copyToClipboard(context, AppLink.letter(token)) },
                        onShare = { link -> shareText(context, shareMessage(link)) },
                        onRevoke = viewModel::revoke,
                    )
                }

                state.error?.let {
                    Text(text = it, style = MutterText.caption, color = GoldDeep)
                }
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
        // 오늘 이전(과거) 날짜는 선택 불가 — 예약 공개는 미래 시점만 유효. iOS DatePicker(in: Date()...) 대응.
        val today = remember { LocalDate.now() }
        val minMillis = remember { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.revealAt.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            // 선택한 날짜 + 기존 시각(기본 now+1h)을 합쳐 시각 정밀도를 유지한다(iOS는 날짜·시각 모두 선택).
                            val pickedDate = Instant.ofEpochMilli(picked)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val keepTime = state.revealAt.atZone(ZoneId.systemDefault()).toLocalTime()
                            onRevealAtChange(
                                pickedDate.atTime(keepTime).atZone(ZoneId.systemDefault()).toInstant(),
                            )
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

/** 공유 시트용 동봉 문구(암호·예약 반영) + 링크. iOS `LetterShareMessage` 대응. */
private fun shareMessage(link: DeliveryLink): String =
    LetterShareMessage.text(
        url = AppLink.letter(link.token),
        hasPassword = link.hasPassword,
        revealAt = link.revealAt,
    )

@Composable
private fun IssuedLinkRow(link: DeliveryLink, onCopy: () -> Unit, onShare: () -> Unit) {
    val url = AppLink.letter(link.token)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = GoldSoft, shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = url,
            style = MutterText.caption,
            color = InkMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 공유(동봉 문구 포함)가 1순위, 복사는 병기.
        TextButton(onClick = onShare) {
            Text("공유", style = MutterText.captionBold, color = GoldDeep)
        }
        TextButton(onClick = onCopy) {
            Text("복사", style = MutterText.captionBold, color = GoldDeep)
        }
    }
}

@Composable
private fun ExistingLinksSection(
    links: List<DeliveryLink>,
    onCopy: (String) -> Unit,
    onShare: (DeliveryLink) -> Unit,
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
                        TextButton(onClick = { onShare(link) }) {
                            Text("공유", style = MutterText.caption, color = Gold)
                        }
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
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(this)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("delivery_link", text))
}
