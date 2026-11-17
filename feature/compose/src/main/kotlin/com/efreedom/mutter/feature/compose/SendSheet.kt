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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.PermanentSendNotice
import com.efreedom.mutter.uicomponent.component.SendChoiceCard
import com.efreedom.mutter.uicomponent.component.shareText
import com.efreedom.mutter.domain.LetterShareMessage
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
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

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
    val context = LocalContext.current

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
            // 보내기 전 선택 — 고르기 전에는 링크·연결 탭을 보이지 않는다.
            if (state.showsSendChoice) {
                SendChoiceCard(
                    tickets = state.entitlements?.tickets ?: 0,
                    freeDays = state.entitlements?.freeDays ?: 0,
                    graceDays = state.entitlements?.graceDays,
                    hasTicket = state.entitlements?.hasTicket == true,
                    onPermanent = viewModel::choosePermanent,
                    onFreeDays = viewModel::chooseFreeDays,
                )
            } else {
                // 커스텀 세그먼트 컨트롤 — iOS Capsule 스타일 이식. iOS처럼 시트 맨 위(안내들 위).
                SendMethodSelector(
                    selected = method,
                    onSelect = { method = it },
                )

                if (state.showsPermanentLabel) {
                    Text(
                        text = stringResource(R.string.compose_send_permanent),
                        style = MutterText.bodyMediumBold,
                        color = GoldDeep,
                    )
                }
                // 영구를 골랐지만 아직 보내지 않았다 — 편지권은 아래에서 링크를 만들거나 보낼 때 쓰인다.
                if (state.showsPermanentPending) {
                    PermanentSendNotice(onChooseAgain = viewModel::chooseAgain)
                }
                // 무료 기간으로 보낸다 — 링크·연결 어느 쪽으로 보내든 언제 닫히고 삭제되는지 알린다.
                state.freeDaysNotice?.let { entitlements ->
                    Text(
                        text = stringResource(
                            R.string.compose_send_free_days_notice,
                            entitlements.freeDays,
                            entitlements.graceDays,
                        ),
                        style = MutterText.caption,
                        color = InkSoft,
                    )
                }

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
                        onShare = { text -> shareText(context, text) },
                        onDone = viewModel::finishSend,
                    )
                    SendMethod.CONNECTION -> ConnectionTab(
                        state = state,
                        onSend = viewModel::sendToConnection,
                    )
                }
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
        listOf(
            SendMethod.LINK to stringResource(R.string.compose_send_method_link),
            SendMethod.CONNECTION to stringResource(R.string.compose_send_method_connection),
        ).forEach { (m, label) ->
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
    onShare: (String) -> Unit,
    onDone: () -> Unit,
) {
    // issuedLink(backing field가 있는 val)는 스마트캐스트되지만 파생 getter(issuedLinkUrl)는 안 되므로 지역 변수로 고정.
    val issued = state.issuedLink
    val issuedUrl = state.issuedLinkUrl

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
            MutterIcon(id = DsR.drawable.lock, contentDescription = null, tint = Gold)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.compose_send_password_title), style = MutterText.bodyMediumBold, color = Ink)
                Text(stringResource(R.string.compose_send_password_subtitle), style = MutterText.caption, color = InkSoft)
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
                label = { Text(stringResource(R.string.compose_send_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 발급된 링크 미리보기
        if (issuedUrl != null) {
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
                    text = issuedUrl,
                    style = MutterText.caption,
                    color = InkMid,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onCopyLink(issuedUrl) },
                    modifier = Modifier.size(24.dp),
                ) {
                    MutterIcon(
                        id = if (linkCopied) DsR.drawable.ic_check else DsR.drawable.copy,
                        contentDescription = if (linkCopied) "복사됨" else "복사",
                        tint = Gold,
                    )
                }
            }
        }

        // CTA 3단 — 공유(동봉 문구+링크) > 복사(문구가 부담스러운 사용자용, 시트 유지) > 완료. iOS SendSheet 대응.
        if (issued != null && issuedUrl != null) {
            MutterButton(
                text = stringResource(R.string.compose_send_share),
                onClick = {
                    onShare(
                        LetterShareMessage.text(
                            url = issuedUrl,
                            hasPassword = issued.hasPassword,
                            revealAt = issued.revealAt,
                        ),
                    )
                },
                leadingIconRes = DsR.drawable.share,
            )
            MutterButton(
                text = stringResource(if (linkCopied) R.string.compose_send_copied else R.string.compose_send_copy_link),
                onClick = { onCopyLink(issuedUrl) },
                style = MutterButtonStyle.Secondary,
                leadingIconRes = if (linkCopied) DsR.drawable.ic_check else DsR.drawable.copy,
            )
            MutterButton(
                text = stringResource(R.string.compose_send_done),
                onClick = onDone,
                style = MutterButtonStyle.Ghost,
            )
        } else {
            MutterButton(
                text = stringResource(R.string.compose_send_issue_link),
                onClick = onIssueLink,
                isLoading = state.isIssuing,
                enabled = state.canIssueLink,
                leadingIconRes = DsR.drawable.link,
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
                text = stringResource(R.string.compose_send_no_connections),
                style = MutterText.captionBold,
                color = InkFaint,
            )
            Text(
                text = stringResource(R.string.compose_send_no_connections_detail),
                style = MutterText.caption,
                color = InkSoft,
            )
        } else {
            Text(
                text = stringResource(R.string.compose_send_to_connections),
                style = MutterText.captionBold,
                color = InkFaint,
            )
            state.connections.forEach { connection ->
                ConnectionRow(
                    nickname = connection.nickname,
                    connectedAt = connection.connectedAt,
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
    connectedAt: Instant,
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

        // 이름 + 연결된 뒤 지난 시간 — iOS connectionRow `Text(connectedAt, style: .relative)` 대응.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = nickname ?: stringResource(R.string.compose_send_unnamed_friend),
                style = MutterText.bodyMediumBold,
                color = Ink,
            )
            val locale = LocalConfiguration.current.locales[0]
            // 행이 그려질 때(시트를 열 때) 한 번 계산한다 — 실시간 갱신은 하지 않는다(범위 밖).
            val elapsed = remember(connectedAt, locale) { connectedAt.toElapsedDisplayTime(locale) }
            Text(
                text = elapsed,
                style = MutterText.caption,
                color = InkSoft,
            )
        }

        IconButton(
            onClick = onSend,
            enabled = !isSending,
        ) {
            MutterIcon(id = DsR.drawable.send, contentDescription = "보내기", tint = Gold)
        }
    }
}

/**
 * 연결된 뒤 지난 시간을 가장 큰 단위와 바로 아래 단위(0이면 생략) 두 개로 보여 준다("3일 5시간", en "3 days, 5 hours").
 * iOS `Text(date, style: .relative)` 대응 — T4 스레드 말풍선(`ThreadsScreen.kt` `toElapsedDisplayTime`)과 같은 규칙의 사본
 * (feature 간 의존 금지라 복제, `uicomponent` 공용화는 후속). 년·월·일은 달력(시스템 시간대)으로, 남은 시·분·초는 [Duration]으로 잰다.
 */
private fun Instant.toElapsedDisplayTime(locale: Locale): String {
    val zone = ZoneId.systemDefault()
    val since = ZonedDateTime.ofInstant(this, zone)
    val now = ZonedDateTime.now(zone)
    if (!now.isAfter(since)) return formatMeasures(locale, Measure(0, MeasureUnit.SECOND))

    // 날짜 차. 지금의 시각이 연결된 시각보다 이르면 마지막 하루는 아직 꽉 차지 않았으므로 하루를 뺀다.
    var period = Period.between(since.toLocalDate(), now.toLocalDate())
    if (since.plus(period).isAfter(now)) {
        period = Period.between(since.toLocalDate(), now.toLocalDate().minusDays(1))
    }
    val seconds = Duration.between(since.plus(period), now).seconds
    val days = period.days
    val units = listOf(
        Measure(period.years, MeasureUnit.YEAR),
        Measure(period.months, MeasureUnit.MONTH),
        Measure(days / 7, MeasureUnit.WEEK),
        Measure(days % 7, MeasureUnit.DAY),
        Measure(seconds / 3600, MeasureUnit.HOUR),
        Measure(seconds % 3600 / 60, MeasureUnit.MINUTE),
        Measure(seconds % 60, MeasureUnit.SECOND),
    )
    val first = units.indexOfFirst { it.number.toLong() != 0L }
    if (first < 0) return formatMeasures(locale, Measure(0, MeasureUnit.SECOND))
    val next = units.getOrNull(first + 1)?.takeIf { it.number.toLong() != 0L }
    return formatMeasures(locale, *listOfNotNull(units[first], next).toTypedArray())
}

private fun formatMeasures(locale: Locale, vararg measures: Measure): String =
    MeasureFormat.getInstance(ULocale.forLocale(locale), MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures)
