package com.efreedom.mutter.feature.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.theme.Danger
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.GoldDeep
import com.efreedom.mutter.uicomponent.theme.MutterText
import java.time.Instant
import java.time.ZoneId

/**
 * 받은 사람의 간직하기 상태 — 편지 끝 안내([ViewerKeepFooter])와 노출 조건이 같이 쓴다.
 * [isTokenViewer] 링크(토큰)로 열었는지, [isSignedIn] 로그인 상태.
 * [isRecipient] 로그인·소유자 아닌 기간 제한 편지 — [canKeep](`tickets > 0 || paid_enabled || is_admin`)과 함께 버튼 노출을 정한다.
 * [hasTicket] 지금 쓸 편지권이 있는지 — 있으면 글자 버튼을 누른 자리에서 한 번 더 묻고, 없으면 바로 [onKeep](결제 안내 시트).
 * [graceDays] 닫힌 뒤 삭제까지의 일수 — 안내 문장 뒤에 삭제 문장을 잇는다. 권한을 못 받았으면 null(그 문장만 뺀다).
 */
internal data class KeepOffer(
    val isTokenViewer: Boolean,
    val isSignedIn: Boolean,
    val isRecipient: Boolean,
    val canKeep: Boolean,
    val hasTicket: Boolean,
    val isKept: Boolean,
    val isKeeping: Boolean,
    val error: String?,
    val warnDays: Int,
    val graceDays: Int?,
    val onKeep: () -> Unit,
)

/**
 * 편지 맨 끝(본문·사진 뒤) 간직 안내 — 읽기를 끊지 않도록 스크롤 마지막에 조용히 둔다. iOS `ViewerKeepFooter` 이식.
 * ① [openUntil] "이 편지는 {M}월 {D}일까지 열려요" + "계속 간직하기", ② [closingSoonDays] 닫힘 예고 "이 편지는 {n}일 뒤 닫혀요" + "간직하기".
 * 간직한 뒤에는 "간직한 편지예요" 한 줄만. 버튼은 한 개만 둔다 — 닫힘 예고가 있으면 그쪽에 붙인다.
 * 버튼이 붙는 줄 끝에 "닫힌 뒤 {n}일이 지나면 삭제돼요."를 잇는다 — 처음 받은 사람이 규칙을 아는 유일한 자리다(계약 "삭제 안내").
 *
 * 편지권이 있으면 글자 버튼을 누른 자리에서 한 번 더 묻는다(팝업 없이 같은 줄이 확인으로 바뀐다) —
 * 작은 글자 버튼 한 번에 편지권이 쓰이지 않게. 편지권이 없으면 누르는 즉시 결제 안내 시트가 뜬다.
 * 확인 중 상태는 `remember` — iOS `@State`처럼 화면이 다시 만들어지면 풀린다(편지권은 쓰이지 않는다).
 */
@Composable
internal fun ViewerKeepFooter(
    openUntil: Instant?,
    closingSoonDays: Int?,
    keepOffer: KeepOffer,
    foreground: Color,
) {
    val muted = foreground.copy(alpha = 0.6f)
    var isConfirming by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 24.dp, end = 24.dp),
    ) {
        if (keepOffer.isKept) {
            FooterText(stringResource(R.string.viewer_kept), muted)
        } else if (isConfirming) {
            KeepConfirmation(keepOffer = keepOffer, muted = muted, onCancel = { isConfirming = false })
        } else {
            val onButton = { if (keepOffer.hasTicket) isConfirming = true else keepOffer.onKeep() }
            if (openUntil != null) {
                val date = remember(openUntil) { openUntil.atZone(ZoneId.systemDefault()).toLocalDate() }
                val until = stringResource(R.string.viewer_letter_end_until, date.monthValue, date.dayOfMonth)
                if (closingSoonDays == null) {
                    KeepLine(
                        text = withPurgeNotice(until, keepOffer.graceDays),
                        button = stringResource(R.string.viewer_letter_end_keep),
                        keepOffer = keepOffer,
                        muted = muted,
                        onClick = onButton,
                    )
                } else {
                    KeepLine(text = until, button = null, keepOffer = keepOffer, muted = muted, onClick = onButton)
                }
            }
            if (closingSoonDays != null) {
                val closing = if (closingSoonDays == 0) {
                    stringResource(R.string.viewer_closing_today)
                } else {
                    stringResource(R.string.viewer_closing_in_days, closingSoonDays)
                }
                KeepLine(
                    text = withPurgeNotice(closing, keepOffer.graceDays),
                    button = stringResource(R.string.viewer_closing_keep),
                    keepOffer = keepOffer,
                    muted = muted,
                    onClick = onButton,
                )
            }
        }

        keepOffer.error?.let { FooterText(it, Danger) }
    }
}

/** 안내 한 줄 + (간직할 수 있으면) 글자 버튼. 글자 버튼은 접근성 터치 영역 때문에 `TextButton`. */
@Composable
private fun KeepLine(text: String, button: String?, keepOffer: KeepOffer, muted: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FooterText(text, muted)
        if (button != null && keepOffer.isRecipient && keepOffer.canKeep) {
            TextButton(onClick = onClick) {
                Text(text = button, style = MutterText.captionBold, color = GoldDeep)
            }
        }
    }
}

/** 같은 자리의 확인 — "편지권 1장을 사용해요…" + 취소 / 간직하기. 계약 "편지권이 있을 때의 확인". */
@Composable
private fun KeepConfirmation(keepOffer: KeepOffer, muted: Color, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FooterText(stringResource(R.string.viewer_keep_confirm_body), muted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onCancel, enabled = !keepOffer.isKeeping) {
                Text(text = stringResource(R.string.viewer_cancel), style = MutterText.caption, color = muted)
            }
            if (keepOffer.isKeeping) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 12.dp).size(16.dp),
                    strokeWidth = 2.dp,
                    color = Gold,
                )
            } else {
                TextButton(onClick = keepOffer.onKeep) {
                    Text(text = stringResource(R.string.viewer_keep_confirm), style = MutterText.captionBold, color = GoldDeep)
                }
            }
        }
    }
}

@Composable
private fun FooterText(text: String, color: Color) {
    Text(text = text, style = MutterText.caption, color = color, textAlign = TextAlign.Center)
}

/** 안내 문장 뒤에 "닫힌 뒤 {n}일이 지나면 삭제돼요."를 잇는다. 일수를 모르면 그대로. */
@Composable
private fun withPurgeNotice(text: String, graceDays: Int?): String =
    if (graceDays == null) text else stringResource(R.string.viewer_with_purge_notice, text, graceDays)
