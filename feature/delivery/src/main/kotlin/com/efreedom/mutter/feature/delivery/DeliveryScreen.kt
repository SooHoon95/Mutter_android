@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.delivery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.component.LockedStatusLabel
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.PermanentSendNotice
import com.efreedom.mutter.uicomponent.component.SendChoiceCard
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.shareText
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
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.AppLink
import com.efreedom.mutter.domain.LetterShareMessage
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterAccessLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** iOS DeliveryView 대응. 전달 링크 발급/관리 — 암호 기본 ON, 예약공개, revoke. */
@Composable
fun DeliveryScreen(
    letterId: String,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    /** 영구 편지로 보내거나 만들려는데 편지권이 없을 때 띄우는 결제 안내 시트(앱이 주입). */
    ticketCheckout: @Composable (TicketCheckoutRoute, (Boolean) -> Unit) -> Unit = { _, _ -> },
    /** 사본을 만든 뒤 그 새 편지의 발송 화면으로. */
    onDuplicated: (String) -> Unit = {},
    viewModel: DeliveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(letterId) { viewModel.start(letterId) }
    state.checkoutRoute?.let { route -> ticketCheckout(route, viewModel::checkoutFinished) }

    LaunchedEffect(state.duplicatedLetterId) {
        state.duplicatedLetterId?.let { newId ->
            viewModel.onDuplicatedHandled()
            onDuplicated(newId)
        }
    }

    // 상태·잔액으로 정한 표시 — "영구"는 제목 줄 오른쪽에(iOS showsPermanentBadge), "n일 남음"·닫힘은 AccessSection에.
    val accessLabel = remember(state.access, state.entitlements) {
        LetterAccessLabel.of(state.access, state.entitlements?.showsTicketUi == true, Instant.now())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            // background 뒤에 둬야 배경은 화면 전체를 칠하고 콘텐츠만 인셋만큼 들어간다(상단 바 인셋도 여기서 맡는다).
            .systemBarsPadding(),
    ) {
        // navigationIcon 없음 — 안드로이드는 시스템 뒤로(제스처/버튼)로 이 화면을 벗어난다(NavHost popBackStack).
        TopAppBar(
            title = { Text(text = stringResource(R.string.delivery_nav), style = MutterText.headline, color = Ink) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory),
            windowInsets = WindowInsets(0),
        )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.delivery_link_send),
                        style = MutterText.titleLarge,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (accessLabel == LetterAccessLabel.Permanent) {
                        Text(
                            text = stringResource(R.string.delivery_access_permanent),
                            style = MutterText.bodyMediumBold,
                            color = GoldDeep,
                        )
                    }
                }

                MutterButton(
                    text = stringResource(R.string.delivery_preview),
                    onClick = onPreview,
                    style = MutterButtonStyle.Ghost,
                )

                AccessSection(
                    state = state,
                    label = accessLabel,
                    onMakePermanent = viewModel::makeSentLetterPermanent,
                )

                // 보내기 전 선택 — 고르기 전에는 발급 폼을 보이지 않는다.
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
                    // 영구를 골랐지만 아직 링크를 만들지 않았다 — 편지권은 아래에서 링크를 만들 때 쓰인다.
                    if (state.showsPermanentPending) {
                        PermanentSendNotice(onChooseAgain = viewModel::chooseAgain)
                    }
                    // 첫 조회 전에는 발급 폼을 그리지 않는다 — 조회 뒤 선택 카드로 바뀌며 깜빡이지 않게(iOS hasLoaded).
                    if (state.hasLoaded) {
                        IssueCard(
                            state = state,
                            onUsePasswordChange = viewModel::setUsePassword,
                            onPasswordChange = viewModel::setPassword,
                            onUseRevealChange = viewModel::setUseReveal,
                            onRevealAtChange = viewModel::setRevealAt,
                            onIssue = viewModel::issue,
                        )
                    }
                }

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


                // 사본 — 새 서버 계약(duplicate-letter)이 있을 때만, 권한 조회 성공으로 판단한다(iOS canDuplicate).
                // 닫힌 뒤 처음 온 사람에게 새로 보내는 길.
                if (state.entitlements != null) {
                    MutterButton(
                        text = stringResource(R.string.delivery_duplicate),
                        onClick = viewModel::duplicate,
                        style = MutterButtonStyle.Secondary,
                        isLoading = state.isDuplicating,
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
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                Text(text = stringResource(R.string.delivery_password_title), style = MutterText.bodyMediumBold, color = Ink)
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
                    label = { Text(stringResource(R.string.delivery_password)) },
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
                Text(text = stringResource(R.string.delivery_reveal), style = MutterText.bodyMediumBold, color = Ink)
                Switch(
                    checked = state.useReveal,
                    onCheckedChange = onUseRevealChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                )
            }

            // iOS DatePicker(L10n.deliveryRevealAt, in: Date()...) compact 대응 — 값 버튼 → 날짜 → 시각을 차례로 고른다.
            if (state.useReveal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.delivery_reveal_at), style = MutterText.bodyMedium, color = Ink)
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            text = state.revealAt.toRevealDisplay(),
                            style = MutterText.bodySmall,
                            color = GoldDeep,
                        )
                    }
                }
            }

            // 무료 기간이 적용되는 편지 — 받는 분 기준 열람 기간 안내(일수는 서버 값). iOS처럼 발급 버튼 바로 위.
            val notice = state.freeDaysNotice
            val graceDays = state.entitlements?.graceDays
            if (notice != null && graceDays != null) {
                Text(
                    text = stringResource(R.string.delivery_free_days_notice, notice, graceDays),
                    style = MutterText.caption,
                    color = InkSoft,
                )
            }

            MutterButton(
                text = stringResource(R.string.delivery_issue),
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
            // DatePicker는 "그 날짜의 UTC 자정" 밀리초를 기대한다 — 순간의 밀리초를 넣으면 로컬 00:00–08:59(KST)에 전날이 선택된다.
            initialSelectedDateMillis = state.revealAt.atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
                        // 날짜는 확인 즉시 반영(시각 유지) → 이어서 시각을 고른다. 시각을 취소해도 날짜는 바뀐 채로 남는다.
                        datePickerState.selectedDateMillis?.let { picked ->
                            val zone = ZoneId.systemDefault()
                            val pickedDate = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                            val keepTime = state.revealAt.atZone(zone).toLocalTime()
                            onRevealAtChange(pickedDate.atTime(keepTime).atZone(zone).toInstant().notInPast())
                        }
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) { Text(stringResource(R.string.delivery_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.delivery_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        // M3 1.3.x엔 TimePickerDialog가 없어 AlertDialog에 TimePicker를 담는다. 시각만 바꾼다(날짜는 그대로).
        val local = state.revealAt.atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val zone = ZoneId.systemDefault()
                        val date = state.revealAt.atZone(zone).toLocalDate()
                        onRevealAtChange(
                            date.atTime(timePickerState.hour, timePickerState.minute).atZone(zone).toInstant().notInPast(),
                        )
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.delivery_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.delivery_cancel)) }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

/** 예약 공개는 미래만 — 지금보다 이르면 지금(다음 분으로 올림). iOS DatePicker(in: Date()...)와 같은 하한. */
private fun Instant.notInPast(): Instant {
    val now = Instant.now()
    return if (isBefore(now)) now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES) else this
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
        // iOS issuedLink 대응 — 복사·공유(동봉 문구 포함) 아이콘.
        IconButton(onClick = onCopy) {
            MutterIcon(
                id = DsR.drawable.copy,
                contentDescription = stringResource(R.string.delivery_copy_link),
                size = 20.dp,
                tint = Gold,
            )
        }
        IconButton(onClick = onShare) {
            MutterIcon(
                id = DsR.drawable.share,
                contentDescription = stringResource(R.string.delivery_share),
                size = 20.dp,
                tint = Gold,
            )
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
        Text(text = stringResource(R.string.delivery_issued), style = MutterText.bodyMediumBold, color = InkSoft)
        val tagPassword = stringResource(R.string.delivery_tag_password)
        val tagReveal = stringResource(R.string.delivery_tag_reveal)
        val revokedText = stringResource(R.string.delivery_revoked)
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
                        // 암호·예약 태그(B군) 뒤에 회수 표시를 한 번만(iOS는 토큰 아래 "무효화됨").
                        val tags = listOfNotNull(
                            tagPassword.takeIf { link.hasPassword },
                            tagReveal.takeIf { link.revealAt != null },
                        ).joinToString(" ")
                        val badgeText = when {
                            !link.revoked -> tags
                            tags.isEmpty() -> revokedText
                            else -> "$tags · $revokedText"
                        }
                        if (badgeText.isNotEmpty()) {
                            Text(text = badgeText, style = MutterText.caption, color = InkFaint)
                        }
                    }

                    if (!link.revoked) {
                        // 링크 유실 대비 — 발급된 링크를 다시 복사·공유해 전달할 수 있게(iOS existingLinks).
                        IconButton(onClick = { onCopy(link.token) }) {
                            MutterIcon(
                                id = DsR.drawable.copy,
                                contentDescription = stringResource(R.string.delivery_copy_link),
                                size = 18.dp,
                                tint = Gold,
                            )
                        }
                        IconButton(onClick = { onShare(link) }) {
                            MutterIcon(
                                id = DsR.drawable.share,
                                contentDescription = stringResource(R.string.delivery_share),
                                size = 18.dp,
                                tint = Gold,
                            )
                        }
                        TextButton(onClick = { onRevoke(link.token) }) {
                            Text(stringResource(R.string.delivery_revoke), style = MutterText.captionBold, color = GoldDeep)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 상태 표시("n일 남음"·닫힘, B군) + 닫힌 편지의 삭제 안내 + "영구 편지로 만들기 · 편지권 1장"(이미 보낸 편지에만).
 * "영구"는 제목 줄 오른쪽, 무료 기간 안내는 발급 카드 안에 있다(iOS 위치).
 * 상태를 모르면(서버 반영 전) 아무것도 그리지 않는다.
 * 버튼 문구가 편지권 1장을 말하므로 확인 없이 진행한다(편지권이 없으면 결제 안내 시트).
 */
@Composable
private fun AccessSection(state: DeliveryUiState, label: LetterAccessLabel, onMakePermanent: () -> Unit) {
    if (state.access == null) return
    val labelText = when (label) {
        // 닫힌 편지는 글자 대신 자물쇠 + "{n}일 뒤 삭제"(아래 LockedStatusLabel)로 보인다. "영구"는 제목 줄에.
        LetterAccessLabel.None, LetterAccessLabel.Permanent, is LetterAccessLabel.Closed -> null
        is LetterAccessLabel.DaysLeft -> stringResource(R.string.delivery_access_days_left, label.days)
    }
    val closed = label as? LetterAccessLabel.Closed
    if (labelText == null && closed == null && !state.canMakePermanent) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labelText?.let { Text(text = it, style = MutterText.bodyMediumBold, color = GoldDeep) }
        closed?.let {
            LockedStatusLabel(
                daysUntilDelete = it.daysUntilDelete,
                style = MutterText.bodyMediumBold,
                color = GoldDeep,
                iconSize = 14.dp,
            )
        }
        // 닫힌 편지 — 언제 삭제되는지를 "영구 편지로 만들기" 위에서 알린다.
        state.lockedDeleteAt?.let { deleteAt ->
            LockedNotice(deleteAt = deleteAt, showsTicketHint = state.entitlements?.showsTicketUi == true)
        }
        if (state.canMakePermanent) {
            MutterButton(
                text = stringResource(R.string.delivery_make_permanent),
                onClick = onMakePermanent,
                isLoading = state.isMakingPermanent,
            )
        }
    }
}

/** "이 편지는 닫혔어요. {M}월 {D}일에 삭제돼요." — 편지권 UI가 보이는 계정([showsTicketHint])에는 되살리는 방법도 한 줄. */
@Composable
private fun LockedNotice(deleteAt: Instant, showsTicketHint: Boolean) {
    val date = remember(deleteAt) { deleteAt.atZone(ZoneId.systemDefault()).toLocalDate() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.delivery_locked_notice, date.monthValue, date.dayOfMonth),
            style = MutterText.bodyMediumBold,
            color = Ink,
        )
        if (showsTicketHint) {
            Text(
                text = stringResource(R.string.delivery_locked_ticket_hint),
                style = MutterText.caption,
                color = InkSoft,
            )
        }
    }
}

/** 예약 공개 시각 — 기기 언어·시간대의 날짜(중간)+시각(짧게). */
private fun Instant.toRevealDisplay(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(this)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("delivery_link", text))
}
