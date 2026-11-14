package com.efreedom.mutter.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efreedom.mutter.core.navigation.TicketCheckoutRoute
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.uicomponent.R as DsR
import com.efreedom.mutter.uicomponent.component.MutterButton
import com.efreedom.mutter.uicomponent.component.MutterButtonStyle
import com.efreedom.mutter.uicomponent.component.MutterChoiceRow
import com.efreedom.mutter.uicomponent.component.MutterIcon
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkMid
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface
import kotlinx.coroutines.launch

/** 고를 수 있는 상품(id → 편지권 장수). 기본 선택은 1장. */
private val CHECKOUT_PRODUCTS = listOf(TicketProduct.ONE to 1, TicketProduct.FIVE to 5)

/**
 * 결제 안내 시트 — 편지권이 필요한 동작을 눌렀는데 편지권이 없을 때 어디서나 같은 화면이 뜬다(iOS TicketCheckoutView 대응).
 * 계약 "결제 안내 시트(앱)". 보내는 사람(영구 편지로 보내기·만들기)과 받는 사람(간직하기)은 제목·설명·버튼 문구만 다르다.
 * 상품을 고르고 맨 아래 버튼을 누르면 구매 → 편지권 사용까지 이어서 하고 닫힌다. 높이는 내용만큼.
 * 아직 안 보낸 편지는 구매까지만 한다(편지권은 링크를 만들거나 보낼 때 쓰인다).
 *
 * @param onFinish 시트가 닫혔다. `true`면 하려던 일(편지권 확보 또는 사용)을 마쳤다 — 호출한 화면이 상태를 다시 받는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketCheckoutSheet(
    route: TicketCheckoutRoute,
    onFinish: (completed: Boolean) -> Unit,
    viewModel: TicketCheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isReceiver = route is TicketCheckoutRoute.KeepReceived

    LaunchedEffect(route) { viewModel.start(route) }

    // 구매·사용 도중에 쓸어내리거나 바깥을 눌러 닫으면 결과를 놓친다 — 그동안은 숨김을 막는다.
    val isWorking by rememberUpdatedState(state.isWorking)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !isWorking },
    )
    val scope = rememberCoroutineScope()
    // 닫힘은 한 번만 알린다(내려가는 도중 바깥을 눌러도 두 번 알리지 않게). 완료 여부는 그 시점의 상태로 판단한다.
    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            val completed = viewModel.state.value.isDone
            viewModel.close()
            onFinish(completed)
        }
    }
    // 버튼·완료로 닫을 때는 시트를 내린 뒤에 알린다(쓸어내려 닫을 때와 같은 움직임).
    val hideThenFinish: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { finish() }
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) hideThenFinish()
    }

    ModalBottomSheet(
        onDismissRequest = finish,
        sheetState = sheetState,
        containerColor = Surface,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.isWorking),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 글자 크기를 키워 내용이 화면보다 길어지면 안쪽이 스크롤된다.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        when (route) {
                            TicketCheckoutRoute.SendPermanent -> R.string.checkout_send_title
                            is TicketCheckoutRoute.MakePermanent -> R.string.checkout_make_title
                            is TicketCheckoutRoute.KeepReceived -> R.string.checkout_keep_title
                        },
                    ),
                    style = MutterText.title,
                    color = Ink,
                )
                Text(
                    text = stringResource(
                        if (isReceiver) R.string.checkout_keep_subtitle else R.string.checkout_send_subtitle,
                    ),
                    style = MutterText.bodyMedium,
                    color = InkSoft,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Point(stringResource(if (isReceiver) R.string.checkout_keep_point1 else R.string.checkout_send_point1))
                Point(stringResource(if (isReceiver) R.string.checkout_keep_point2 else R.string.checkout_send_point2))
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.checkout_balance, state.tickets, state.tickets),
                    style = MutterText.captionBold,
                    color = GoldDeep,
                )
                // 편지권이 이미 있으면(구매는 됐는데 사용이 실패한 경우 등) 상품을 다시 고르게 하지 않는다.
                if (!state.canUseNow) {
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CHECKOUT_PRODUCTS.forEach { (id, count) ->
                            MutterChoiceRow(
                                selected = state.selectedProductId == id,
                                title = pluralStringResource(R.plurals.checkout_product, count, count),
                                trailing = state.products[id]?.price?.takeIf { it.isNotBlank() },
                                enabled = !state.isWorking,
                                onSelect = { viewModel.selectProduct(id) },
                            )
                        }
                    }
                }
            }

            state.message?.let { message ->
                Text(
                    text = when (message) {
                        TicketCheckoutMessage.PurchaseUnavailable -> stringResource(R.string.checkout_unavailable)
                        TicketCheckoutMessage.Pending -> stringResource(R.string.checkout_pending)
                        is TicketCheckoutMessage.Error -> message.text
                    },
                    style = MutterText.caption,
                    color = Danger,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val busy = state.isWorking || state.isLoading
                MutterButton(
                    text = stringResource(
                        when (route) {
                            TicketCheckoutRoute.SendPermanent ->
                                if (state.canUseNow) R.string.checkout_send_use else R.string.checkout_send_buy
                            is TicketCheckoutRoute.MakePermanent ->
                                if (state.canUseNow) R.string.checkout_make_use else R.string.checkout_make_buy
                            is TicketCheckoutRoute.KeepReceived ->
                                if (state.canUseNow) R.string.checkout_keep_use else R.string.checkout_keep_buy
                        },
                    ),
                    onClick = viewModel::confirm,
                    // 불러오는 중·진행 중에는 로딩 표시만, 살 수 없을 때만 흐리게 끈다.
                    enabled = busy || state.canConfirm,
                    isLoading = busy,
                )
                MutterButton(
                    text = stringResource(R.string.checkout_cancel),
                    onClick = hideThenFinish,
                    style = MutterButtonStyle.Ghost,
                    enabled = !state.isWorking,
                )
            }
        }
    }
}

/** 요점 한 줄 — 체크 아이콘 + 문장. */
@Composable
private fun Point(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MutterIcon(id = DsR.drawable.ic_check, size = 14.dp, tint = Gold)
        Text(text = text, style = MutterText.bodyMedium, color = InkMid)
    }
}
