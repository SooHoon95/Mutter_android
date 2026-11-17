@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.threads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.component.MutterImage
import com.efreedom.mutter.uicomponent.component.PullableFullSizeBox
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface as MutterSurface
import com.efreedom.mutter.domain.model.Counterpart
import com.efreedom.mutter.domain.model.LetterDirection
import com.efreedom.mutter.domain.model.ThreadLetter
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

/** iOS ThreadsView 대응. 주고받은 상대 목록 + 선택 상대와의 편지 흐름(ModalBottomSheet). */
@Composable
fun ThreadsScreen(
    onReply: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThreadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.counterparts.isNotEmpty(),
            onRefresh = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        ) {
        when {
            state.isLoading && state.counterparts.isEmpty() -> {
                CircularProgressIndicator(
                    color = Gold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.counterparts.isEmpty() -> {
                // 빈 상태 — 당겨서 새로고침이 되도록 스크롤 컨테이너로 감싼다(중앙 정렬은 컨테이너가 담당).
                PullableFullSizeBox {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MutterImage(id = DsR.drawable.empty_connections, modifier = Modifier.size(120.dp))
                        Text(text = stringResource(R.string.threads_empty_title), style = MutterText.bodyLarge, color = InkSoft)
                        Text(
                            text = stringResource(R.string.threads_empty_detail),
                            style = MutterText.caption,
                            color = InkFaint,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 16.dp,
                    ),
                ) {
                    items(state.counterparts, key = { it.id }) { counterpart ->
                        CounterpartRow(
                            counterpart = counterpart,
                            onClick = { viewModel.openThread(counterpart) },
                        )
                    }
                }
            }
        }
        }
    }

    // 스레드 시트
    if (state.selectedCounterpart != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeThread,
            sheetState = sheetState,
            containerColor = Ivory,
        ) {
            ThreadSheetContent(
                state = state,
                onRetry = { state.selectedCounterpart?.let { viewModel.openThread(it) } },
                onReply = { counterpart ->
                    viewModel.closeThread()
                    onReply(counterpart.userId)
                },
                onOpen = { token ->
                    viewModel.closeThread()
                    onOpen(token)
                },
            )
        }
    }
}

@Composable
private fun CounterpartRow(counterpart: Counterpart, onClick: () -> Unit) {
    Surface(
        color = MutterSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, Hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 아바타 원 — 이니셜
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = GoldSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (counterpart.nickname ?: "?").take(1),
                    style = MutterText.bodyLargeBold,
                    color = GoldDeep,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = counterpart.nickname ?: stringResource(R.string.threads_unnamed),
                    style = MutterText.bodyLargeBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.threads_exchange_count, counterpart.exchangeCount),
                    style = MutterText.caption,
                    color = InkFaint,
                )
            }

            MutterIcon(id = DsR.drawable.ic_chevron_right, contentDescription = null, tint = InkFaint, size = 16.dp)
        }
    }
}

@Composable
private fun ThreadSheetContent(
    state: ThreadsUiState,
    onRetry: () -> Unit,
    onReply: (Counterpart) -> Unit,
    onOpen: (String) -> Unit,
) {
    val counterpart = state.selectedCounterpart ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = GoldSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (counterpart.nickname ?: "?").take(1),
                    style = MutterText.captionBold,
                    color = GoldDeep,
                )
            }
            Text(
                text = counterpart.nickname ?: stringResource(R.string.threads_letter_fallback),
                style = MutterText.headline,
                color = Ink,
            )
        }

        Divider(color = Hairline)

        // 본문 — 로딩/에러/빈/목록
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 8.dp),
        ) {
            when {
                state.isThreadLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Gold)
                    }
                }

                state.threadLoadFailed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MutterIcon(id = DsR.drawable.warning, contentDescription = null, tint = Gold, size = 40.dp)
                        Text(
                            text = stringResource(R.string.threads_sheet_load_error),
                            style = MutterText.bodyMedium,
                            color = InkSoft,
                            textAlign = TextAlign.Center,
                        )
                        MutterButton(
                            text = stringResource(R.string.threads_retry),
                            onClick = onRetry,
                            style = MutterButtonStyle.Ghost,
                        )
                    }
                }

                state.thread.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.threads_sheet_empty),
                            style = MutterText.bodyMedium,
                            color = InkFaint,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 20.dp,
                            vertical = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.thread, key = { it.id }) { letter ->
                            ThreadBubble(letter = letter, onOpen = onOpen)
                        }
                    }
                }
            }
        }

        // 하단 편지 쓰기 바 — 탈퇴한 상대에게는 보낼 수 없어 숨긴다.
        if (!counterpart.isGone) {
            Divider(color = Hairline)
            val buttonTitle = counterpart.nickname?.let { stringResource(R.string.threads_write_to, it) }
                ?: stringResource(R.string.threads_write)
            MutterButton(
                text = buttonTitle,
                onClick = { onReply(counterpart) },
                leadingIconRes = DsR.drawable.compose,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ThreadBubble(letter: ThreadLetter, onOpen: (String) -> Unit) {
    val isSent = letter.direction == LetterDirection.SENT
    Row(modifier = Modifier.fillMaxWidth()) {
        if (isSent) Spacer(modifier = Modifier.weight(1f))

        Surface(
            color = if (isSent) GoldSoft else MutterSurface,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isSent) 12.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 12.dp,
            ),
            shadowElevation = 1.dp,
            // iOS는 받은 말풍선에만 헤어라인 테두리(strokeBorder hairline 0.5).
            border = if (!isSent) BorderStroke(0.5.dp, Hairline) else null,
            modifier = Modifier
                // 최대 폭 ~82%(나머지는 반대편 여백) — iOS Spacer(minLength:60) 대응(폰 폭 기준).
                .weight(0.82f, fill = false)
                .then(
                    letter.token?.takeIf { !isSent }?.let { token ->
                        Modifier.clickable { onOpen(token) }
                    } ?: Modifier,
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutterIcon(
                    id = if (isSent) DsR.drawable.envelope_open else DsR.drawable.envelope,
                    contentDescription = null,
                    tint = if (isSent) GoldDeep else Gold,
                    size = 16.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = letter.title.ifEmpty { stringResource(R.string.threads_letter_fallback) },
                        style = MutterText.bodyMediumBold,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val locale = LocalConfiguration.current.locales[0]
                    // 말풍선이 그려질 때(시트를 열 때) 한 번 계산한다 — 초 단위 실시간 갱신은 하지 않는다.
                    val elapsed = remember(letter.sentAt, locale) { letter.sentAt.toElapsedDisplayTime(locale) }
                    Text(
                        text = elapsed,
                        style = MutterText.caption,
                        color = InkFaint,
                    )
                }
            }
        }

        if (!isSent) Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * 보낸 뒤 지난 시간을 날짜가 아닌 "경과 시간"으로, 가장 큰 단위와 바로 아래 단위(0이면 생략) 두 개로 보여 준다
 * (iOS `Text(date, style: .relative)` 대응 — "2시간 23분", "3일 5시간", en "2 hours, 23 minutes").
 * 년·월·일은 달력(시스템 시간대)으로 센다: 월 경계(1월 31일 → 3월 1일)가 고정 초 나눗셈과 어긋나지 않게
 * [Period]로 꽉 찬 날짜 차를 구하고, 남은 시각 차(시·분·초)만 [Duration]으로 잰다. 몇 주·며칠이 지나도 날짜로 바뀌지 않는다.
 */
private fun Instant.toElapsedDisplayTime(locale: Locale): String {
    val zone = ZoneId.systemDefault()
    val sent = ZonedDateTime.ofInstant(this, zone)
    val now = ZonedDateTime.now(zone)
    if (!now.isAfter(sent)) return format(locale, Measure(0, MeasureUnit.SECOND))

    // 날짜 차. 지금의 시각이 보낸 시각보다 이르면 마지막 하루는 아직 꽉 차지 않았으므로 하루를 뺀다.
    var period = Period.between(sent.toLocalDate(), now.toLocalDate())
    if (sent.plus(period).isAfter(now)) {
        period = Period.between(sent.toLocalDate(), now.toLocalDate().minusDays(1))
    }
    val seconds = Duration.between(sent.plus(period), now).seconds
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
    if (first < 0) return format(locale, Measure(0, MeasureUnit.SECOND))
    val next = units.getOrNull(first + 1)?.takeIf { it.number.toLong() != 0L }
    return format(locale, *listOfNotNull(units[first], next).toTypedArray())
}

private fun format(locale: Locale, vararg measures: Measure): String =
    MeasureFormat.getInstance(ULocale.forLocale(locale), MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures)
