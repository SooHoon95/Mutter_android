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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

/**
 * 여러 개 중 하나를 고르는 줄(라디오) — 보내기 전 선택과 결제 안내의 상품 선택이 함께 쓴다(iOS MutterChoiceRow 대응).
 * 누르면 고르기만 한다. 실제 진행은 화면 맨 아래 버튼이 맡는다.
 * 선택된 줄은 강조 배경·테두리·채워진 라디오. 여러 줄을 묶는 쪽이 `Modifier.selectableGroup()`을 붙인다.
 */
@Composable
fun MutterChoiceRow(
    selected: Boolean,
    title: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    footnote: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
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
            detail?.let { Text(it, style = MutterText.caption, color = InkSoft) }
            footnote?.let { Text(it, style = MutterText.captionBold, color = GoldDeep) }
        }
        trailing?.let {
            Text(it, style = MutterText.bodyMediumBold, color = if (selected) GoldDeep else InkMid)
        }
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
