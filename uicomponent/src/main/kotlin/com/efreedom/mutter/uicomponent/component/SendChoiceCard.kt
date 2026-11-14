package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R
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

private enum class SendChoiceOption { PERMANENT, FREE_DAYS }

/**
 * 보내기 전 선택 — "어떻게 보낼까요?" 아래에서 영구 편지 / {free_days}일 편지 중 하나를 고르고, 맨 아래 버튼으로 진행한다.
 * 계약 "보내기 전 선택(보낸 사람)". 링크 발급 화면과 보내기 시트가 같이 쓴다(iOS SendChoiceCard 대응).
 *
 * 고르는 것과 진행하는 것을 나눈 이유: 선택지를 누르자마자 편지권이 쓰이거나 확인 팝업이 뜨지 않게 하고,
 * 버튼 문구로 무슨 일이 일어나는지(편지권 사용·구매·무료)를 미리 알린다.
 *
 * @param hasTicket 영구 편지에 쓸 편지권이 있는지(관리자는 차감이 없어 true). false면 버튼이 구매로 이어진다.
 * @param onPermanent 영구 편지를 고르고 버튼을 눌렀다 — 편지권이 있으면 사용, 없으면 구매 화면(판단은 호출부).
 * @param onFreeDays {free_days}일 편지를 고르고 버튼을 눌렀다.
 */
@Composable
fun SendChoiceCard(
    tickets: Int,
    freeDays: Int,
    hasTicket: Boolean,
    isLoading: Boolean,
    onPermanent: () -> Unit,
    onFreeDays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 구매 화면에 다녀와도(컴포지션에서 빠졌다 돌아와도) 고른 것을 기억한다.
    var choice by rememberSaveable { mutableStateOf(SendChoiceOption.PERMANENT) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.send_choice_title), style = MutterText.title, color = Ink)

        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceOption(
                title = stringResource(R.string.send_choice_permanent_title),
                body = stringResource(R.string.send_choice_permanent_body),
                price = stringResource(R.string.send_choice_permanent_price),
                note = stringResource(R.string.send_choice_tickets_owned, tickets),
                selected = choice == SendChoiceOption.PERMANENT,
                enabled = !isLoading,
                onSelect = { choice = SendChoiceOption.PERMANENT },
            )
            ChoiceOption(
                title = stringResource(R.string.send_choice_free_title, freeDays),
                body = stringResource(R.string.send_choice_free_body, freeDays),
                price = stringResource(R.string.send_choice_free_price),
                note = null,
                selected = choice == SendChoiceOption.FREE_DAYS,
                enabled = !isLoading,
                onSelect = { choice = SendChoiceOption.FREE_DAYS },
            )
        }

        if (choice == SendChoiceOption.PERMANENT) {
            Text(
                text = stringResource(
                    if (hasTicket) R.string.send_choice_uses_ticket else R.string.send_choice_needs_ticket,
                ),
                style = MutterText.caption,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        MutterButton(
            text = when (choice) {
                SendChoiceOption.PERMANENT -> stringResource(
                    if (hasTicket) R.string.send_choice_permanent_button else R.string.send_choice_buy_button,
                )
                SendChoiceOption.FREE_DAYS -> stringResource(R.string.send_choice_free_button, freeDays)
            },
            onClick = {
                when (choice) {
                    SendChoiceOption.PERMANENT -> onPermanent()
                    SendChoiceOption.FREE_DAYS -> onFreeDays()
                }
            },
            isLoading = isLoading,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 선택지 한 줄 — 누르면 고르기만 한다. 선택된 줄은 강조 배경·테두리·채워진 라디오. */
@Composable
private fun ChoiceOption(
    title: String,
    body: String,
    price: String,
    note: String?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) GoldSoft else Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Gold else Hairline, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioMark(selected = selected)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MutterText.bodyLargeBold, color = Ink)
            Text(body, style = MutterText.caption, color = InkSoft)
            note?.let { Text(it, style = MutterText.captionBold, color = GoldDeep) }
        }
        Text(price, style = MutterText.bodyMediumBold, color = if (selected) GoldDeep else InkMid)
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(1.5.dp, if (selected) Gold else InkFaint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(12.dp).background(Gold, CircleShape))
        }
    }
}
