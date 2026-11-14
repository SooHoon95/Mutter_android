package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText
import com.efreedom.mutter.uicomponent.theme.Surface

/**
 * 보내기 전 선택 — "영구 편지로 보내기"(강조·먼저) / "{free_days}일 편지로 보내기".
 * 계약 "보내기 전 선택(보낸 사람)". 링크 발급 화면과 보내기 시트가 같이 쓴다.
 *
 * 영구: [tickets] > 0이면 확인("편지권 1장을 사용해요") 뒤 [onPermanent], 0이면 바로 [onNeedTickets].
 */
@Composable
fun SendChoiceCard(
    tickets: Int,
    freeDays: Int,
    isMakingPermanent: Boolean,
    onPermanent: () -> Unit,
    onNeedTickets: () -> Unit,
    onFreeDays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.send_choice_title), style = MutterText.title, color = Ink)
        ChoiceOption(
            title = stringResource(R.string.send_choice_permanent_title),
            body = stringResource(R.string.send_choice_permanent_body),
            price = stringResource(R.string.send_choice_permanent_price),
            note = stringResource(R.string.send_choice_tickets_owned, tickets),
            emphasized = true,
            isLoading = isMakingPermanent,
            onClick = { if (tickets > 0) confirming = true else onNeedTickets() },
        )
        ChoiceOption(
            title = stringResource(R.string.send_choice_free_title, freeDays),
            body = stringResource(R.string.send_choice_free_body, freeDays),
            price = stringResource(R.string.send_choice_free_price),
            note = null,
            emphasized = false,
            isLoading = false,
            onClick = { if (!isMakingPermanent) onFreeDays() },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.send_choice_permanent_title)) },
            text = { Text(stringResource(R.string.send_choice_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onPermanent()
                }) { Text(stringResource(R.string.send_choice_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.send_choice_cancel)) }
            },
        )
    }
}

@Composable
private fun ChoiceOption(
    title: String,
    body: String,
    price: String,
    note: String?,
    emphasized: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (emphasized) GoldSoft else Surface)
            .border(if (emphasized) 1.5.dp else 1.dp, if (emphasized) Gold else Hairline, shape)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MutterText.bodyMediumBold, color = Ink)
            Text(body, style = MutterText.caption, color = InkSoft)
            note?.let { Text(it, style = MutterText.caption, color = GoldDeep) }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Gold)
        } else {
            Text(price, style = MutterText.captionBold, color = if (emphasized) GoldDeep else InkSoft)
        }
    }
}
