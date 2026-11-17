@file:OptIn(ExperimentalMaterial3Api::class)

package com.efreedom.mutter.feature.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.domain.model.TicketHistoryItem
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.model.TicketReason
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 편지권 화면 — 잔액·설명·구매(1장/5장)·내역. 프로필의 "편지권" 행에서 들어온다.
 * 편지권이 필요한데 없을 때 뜨는 결제 안내 시트(`:feature:ticket`)와는 다른 화면이다.
 * 구매 버튼은 유료 전환된 일반 계정에만(관리자는 차감 없이 영구라 구매 UI가 없다).
 */
@Composable
fun TicketsScreen(
    modifier: Modifier = Modifier,
    viewModel: TicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.notice != null) {
        LaunchedEffect(state.notice) {
            delay(2_500)
            viewModel.onNoticeShown()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = stringResource(R.string.tickets_screen_title), style = MutterText.titleLarge, color = Ink)

            state.entitlements?.let { ent ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (ent.tickets > 0) {
                            stringResource(R.string.tickets_screen_balance, ent.tickets)
                        } else {
                            stringResource(R.string.tickets_none)
                        },
                        style = MutterText.display,
                        color = GoldDeep,
                    )
                    Text(text = stringResource(R.string.tickets_description), style = MutterText.bodyMedium, color = InkSoft)
                }

                if (ent.canPurchase) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProductButton(
                            count = 1,
                            product = state.products[TicketProduct.ONE],
                            isLoading = state.purchasingId == TicketProduct.ONE,
                            onClick = { viewModel.purchase(TicketProduct.ONE) },
                        )
                        ProductButton(
                            count = 5,
                            product = state.products[TicketProduct.FIVE],
                            isLoading = state.purchasingId == TicketProduct.FIVE,
                            onClick = { viewModel.purchase(TicketProduct.FIVE) },
                        )
                        // 지급 시점과 환불 불가 안내 — 구매 버튼이 보이는 동안 함께 보인다.
                        Text(
                            text = stringResource(R.string.tickets_purchase_notice),
                            style = MutterText.caption,
                            color = InkFaint,
                        )
                    }
                }
            }

            state.notice?.let { notice ->
                Text(
                    text = stringResource(
                        when (notice) {
                            TicketNotice.GRANTED -> R.string.tickets_granted
                            TicketNotice.PENDING -> R.string.tickets_pending
                        },
                    ),
                    style = MutterText.bodyMediumBold,
                    color = GoldDeep,
                )
            }

            // 스토어 상품을 못 받았거나 서버가 결제 대상이 아니라고 하면(403 not_tester) 살 수 없는 상태임을 알린다
            // (다른 오류가 있으면 그쪽이 우선).
            val error = state.error
                ?: stringResource(DsR.string.ticket_purchase_unavailable).takeIf { state.purchaseUnavailable }
                ?: stringResource(R.string.ticket_account_mismatch).takeIf { state.purchaseAccountMismatch }
            error?.let { Text(text = it, style = MutterText.caption, color = Danger) }

            HistorySection(state.history)
        }
    }
}

/** 상품 버튼(장수 + 가격) — 스토어에 상품이 없으면(미등록) 끈 채로 둔다. */
@Composable
private fun ProductButton(count: Int, product: TicketProduct?, isLoading: Boolean, onClick: () -> Unit) {
    val price = product?.price?.takeIf { it.isNotBlank() }
    val text = if (price != null) {
        pluralStringResource(R.plurals.tickets_product_price, count, count, price)
    } else {
        pluralStringResource(R.plurals.tickets_product, count, count)
    }
    MutterButton(
        text = text,
        onClick = onClick,
        enabled = product != null,
        isLoading = isLoading,
        style = MutterButtonStyle.Secondary,
    )
}

@Composable
private fun HistorySection(history: List<TicketHistoryItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.tickets_history), style = MutterText.bodyMediumBold, color = InkSoft)
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.tickets_history_empty),
                style = MutterText.bodyMedium,
                color = InkFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(width = 0.5.dp, color = Hairline, shape = RoundedCornerShape(16.dp)),
            ) {
                history.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Hairline, thickness = 0.5.dp)
                    HistoryRow(item)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: TicketHistoryItem) {
    val reason = stringResource(
        when (item.reason) {
            TicketReason.PURCHASE -> R.string.tickets_reason_purchase
            TicketReason.USE -> R.string.tickets_reason_use
            TicketReason.GRANT -> R.string.tickets_reason_grant
            TicketReason.REFUND -> R.string.tickets_reason_refund
            // 사유를 모르면 "편지권"(iOS `.unknown → ticket.title`).
            TicketReason.OTHER -> R.string.tickets_screen_title
        },
    )
    // 굵은 줄 "사유 · 편지 제목"(제목 없으면 사유만), 아래 줄 날짜(로캘 medium — ko "2026. 11. 15.") — iOS TicketHistoryRowView.
    val title = listOfNotNull(reason, item.letterTitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MutterText.bodyMediumBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = dateFormatter.format(item.createdAt), style = MutterText.caption, color = InkSoft)
        }
        Text(
            text = if (item.delta > 0) "+${item.delta}" else "${item.delta}",
            style = MutterText.bodyMediumBold,
            color = if (item.delta > 0) GoldDeep else InkSoft,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}
