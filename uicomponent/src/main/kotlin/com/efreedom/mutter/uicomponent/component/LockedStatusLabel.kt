package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R

/**
 * 닫힌 편지의 상태 표시 — 자물쇠 + `{n}일 뒤 삭제`(당일 `오늘 삭제`, 삭제 시각을 모르면 `닫힘`) 한 줄.
 * 보낸·받은 편지 목록과 보낸 편지 상세가 같이 쓴다(iOS LetterAccessBadge의 잠김 표시 대응). 계약 "삭제 안내".
 *
 * 좁은 목록 줄에서 접히지 않게 "닫힘" 글자는 자물쇠가 대신한다 — 읽어 주기 문구에는 "닫힘, {n}일 뒤 삭제"처럼 "닫힘"을 넣는다.
 *
 * @param daysUntilDelete 삭제까지 남은 일수(0 = 오늘). null이면 삭제 시각을 모른다.
 */
@Composable
fun LockedStatusLabel(
    daysUntilDelete: Int?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp,
) {
    val closed = stringResource(R.string.access_closed)
    val text = when (daysUntilDelete) {
        null -> closed
        0 -> stringResource(R.string.access_closed_delete_today)
        else -> stringResource(R.string.access_closed_delete_in, daysUntilDelete)
    }
    val spoken = if (daysUntilDelete == null) closed else stringResource(R.string.access_closed_spoken, text)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MutterIcon(id = R.drawable.lock, contentDescription = null, size = iconSize, tint = color)
        Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
    }
}
