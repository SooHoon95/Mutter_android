package com.efreedom.mutter.feature.profile

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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

/**
 * 편지권 화면 — 잔액·설명·구매(1장/5장)·내역. 프로필의 "편지권" 행과 편지권 부족(NO_TICKET)에서 들어온다.
 * 구매 버튼은 유료 전환된 일반 계정에만(관리자는 차감 없이 영구라 구매 UI가 없다).
 *
 * @param closesAfterPurchase 영구 편지를 보내려다 들어온 구매 화면이면 true —
 *   왜 왔는지 한 줄로 알리고, 내역은 숨기고, 구매가 끝나면 [onClose]로 스스로 닫혀 보내기로 돌아간다.
 */
@Composable
fun TicketsScreen(
    modifier: Modifier = Modifier,
    closesAfterPurchase: Boolean = false,
    onClose: () -> Unit = {},
    viewModel: TicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (closesAfterPurchase) {
        LaunchedEffect(state.purchaseCount) {
            if (state.purchaseCount > 0) onClose()
        }
    }

    if (state.notice != null) {
        LaunchedEffect(state.notice) {
            delay(2_500)
            viewModel.onNoticeShown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = stringResource(R.string.tickets_title), style = MutterText.titleLarge, color = Ink)

        state.entitlements?.let { ent ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (ent.tickets > 0) {
                        stringResource(R.string.tickets_balance, ent.tickets)
                    } else {
                        stringResource(R.string.tickets_none)
                    },
                    style = MutterText.display,
                    color = GoldDeep,
                )
                Text(text = stringResource(R.string.tickets_description), style = MutterText.bodyMedium, color = InkSoft)
            }

            if (closesAfterPurchase) {
                Text(
                    text = stringResource(DsR.string.ticket_needed_for_permanent),
                    style = MutterText.bodyMediumBold,
                    color = Ink,
                )
            }

            if (ent.canPurchase) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProductButton(
                        label = stringResource(R.string.tickets_buy_one),
                        product = state.products[TicketProduct.ONE],
                        isLoading = state.purchasingId == TicketProduct.ONE,
                        onClick = { viewModel.purchase(TicketProduct.ONE) },
                    )
                    ProductButton(
                        label = stringResource(R.string.tickets_buy_five),
                        product = state.products[TicketProduct.FIVE],
                        isLoading = state.purchasingId == TicketProduct.FIVE,
                        onClick = { viewModel.purchase(TicketProduct.FIVE) },
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

        // 스토어 상품을 못 받으면 버튼이 꺼진 채 이유가 안 보인다 — 살 수 없는 상태임을 알린다(다른 오류가 있으면 그쪽이 우선).
        val error = state.error
            ?: stringResource(DsR.string.ticket_purchase_unavailable).takeIf { state.purchaseUnavailable }
        error?.let { Text(text = it, style = MutterText.caption, color = Danger) }

        if (!closesAfterPurchase && state.history.isNotEmpty()) {
            HistorySection(state.history)
        }
    }
}

/** 상품 버튼 — 스토어에 상품이 없으면(미등록) 끈 채로 둔다. */
@Composable
private fun ProductButton(label: String, product: TicketProduct?, isLoading: Boolean, onClick: () -> Unit) {
    val text = product?.price?.takeIf { it.isNotBlank() }?.let { "$label · $it" } ?: label
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

@Composable
private fun HistoryRow(item: TicketHistoryItem) {
    val reason = stringResource(
        when (item.reason) {
            TicketReason.PURCHASE -> R.string.tickets_reason_purchase
            TicketReason.USE -> R.string.tickets_reason_use
            TicketReason.GRANT -> R.string.tickets_reason_grant
            TicketReason.REFUND -> R.string.tickets_reason_refund
            TicketReason.OTHER -> R.string.tickets_reason_other
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = reason, style = MutterText.bodyMediumBold, color = Ink)
            val sub = listOfNotNull(item.letterTitle?.takeIf { it.isNotBlank() }, HISTORY_DATE.format(item.createdAt))
            Text(text = sub.joinToString(" · "), style = MutterText.caption, color = InkFaint)
        }
        Text(
            text = if (item.delta > 0) "+${item.delta}" else "${item.delta}",
            style = MutterText.bodyMediumBold,
            color = if (item.delta > 0) GoldDeep else InkSoft,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}

private val HISTORY_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault())
