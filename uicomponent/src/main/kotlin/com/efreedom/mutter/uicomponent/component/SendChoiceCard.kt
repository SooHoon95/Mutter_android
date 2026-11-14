package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText

private enum class SendChoiceOption { PERMANENT, FREE_DAYS }

/**
 * 보내기 전 선택 — "어떻게 보낼까요?" 아래에서 영구 편지 / {free_days}일 편지 중 하나를 고르고, 맨 아래 버튼으로 진행한다.
 * 계약 "보내기 전 선택(보낸 사람)". 링크 발급 화면과 보내기 시트가 같이 쓴다(iOS SendChoiceCard 대응).
 *
 * 고르는 것과 진행하는 것을 나눈 이유: 선택지를 누르자마자 무슨 일이 일어나거나 확인 팝업이 뜨지 않게 한다.
 * **여기서는 편지권을 쓰지 않는다** — 고른 것을 기억할 뿐이고, 편지권은 링크를 만들거나 보낼 때 쓰인다(버튼 위 안내).
 * 편지권이 없으면 버튼이 결제 안내 시트(구매)로 이어진다.
 *
 * @param hasTicket 영구 편지에 쓸 편지권이 있는지(관리자는 차감이 없어 true).
 * @param onPermanent 영구 편지를 고르고 버튼을 눌렀다 — 편지권이 있으면 그대로 진행, 없으면 결제 안내(판단은 호출부).
 * @param onFreeDays {free_days}일 편지를 고르고 버튼을 눌렀다.
 */
@Composable
fun SendChoiceCard(
    tickets: Int,
    freeDays: Int,
    hasTicket: Boolean,
    onPermanent: () -> Unit,
    onFreeDays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 결제 안내 시트가 떴다 내려가도(화면이 다시 그려져도) 고른 것을 기억한다.
    var choice by rememberSaveable { mutableStateOf(SendChoiceOption.PERMANENT) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.send_choice_title), style = MutterText.title, color = Ink)

        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MutterChoiceRow(
                selected = choice == SendChoiceOption.PERMANENT,
                title = stringResource(R.string.send_choice_permanent_title),
                detail = stringResource(R.string.send_choice_permanent_body),
                footnote = stringResource(R.string.send_choice_tickets_owned, tickets),
                trailing = stringResource(R.string.send_choice_permanent_price),
                onSelect = { choice = SendChoiceOption.PERMANENT },
            )
            MutterChoiceRow(
                selected = choice == SendChoiceOption.FREE_DAYS,
                title = stringResource(R.string.send_choice_free_title, freeDays),
                detail = stringResource(R.string.send_choice_free_body, freeDays),
                trailing = stringResource(R.string.send_choice_free_price),
                onSelect = { choice = SendChoiceOption.FREE_DAYS },
            )
        }

        if (choice == SendChoiceOption.PERMANENT && hasTicket) {
            Text(
                text = stringResource(R.string.send_choice_permanent_hint),
                style = MutterText.caption,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        MutterButton(
            text = when (choice) {
                SendChoiceOption.PERMANENT -> stringResource(R.string.send_choice_permanent_button)
                SendChoiceOption.FREE_DAYS -> stringResource(R.string.send_choice_free_button, freeDays)
            },
            onClick = {
                when (choice) {
                    SendChoiceOption.PERMANENT -> onPermanent()
                    SendChoiceOption.FREE_DAYS -> onFreeDays()
                }
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
