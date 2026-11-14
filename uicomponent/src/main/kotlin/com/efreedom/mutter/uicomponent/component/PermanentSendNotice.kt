package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.GoldSoft
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.MutterText

/**
 * 보내기 전 선택에서 영구 편지를 고른 뒤, 발급 폼·연결 상대 목록 위에 두는 안내(iOS PermanentSendNotice 대응).
 * 편지권은 아직 쓰이지 않았고 실제로 보낼 때 쓰인다는 것을 알리고, 마음을 바꿀 수 있게 "다시 고르기"를 준다.
 */
@Composable
fun PermanentSendNotice(
    onChooseAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GoldSoft, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.send_choice_permanent_chosen), style = MutterText.bodyMediumBold, color = Ink)
            Text(stringResource(R.string.send_choice_permanent_hint), style = MutterText.caption, color = InkSoft)
        }
        TextButton(onClick = onChooseAgain) {
            Text(stringResource(R.string.send_choice_choose_again), style = MutterText.captionBold, color = GoldDeep)
        }
    }
}
