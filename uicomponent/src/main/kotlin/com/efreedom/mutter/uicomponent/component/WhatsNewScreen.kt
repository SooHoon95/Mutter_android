package com.efreedom.mutter.uicomponent.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.R
import com.efreedom.mutter.uicomponent.theme.Gold
import com.efreedom.mutter.uicomponent.theme.Hairline
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.InkFaint
import com.efreedom.mutter.uicomponent.theme.InkSoft
import com.efreedom.mutter.uicomponent.theme.Ivory
import com.efreedom.mutter.uicomponent.theme.MutterText
import kotlinx.coroutines.launch

/** 새 소식 한 장 — 실제 화면 캡처 + 제목 + 설명(문장마다 줄바꿈). */
private data class WhatsNewPage(@DrawableRes val image: Int, val title: String, val body: String)

/**
 * 새 소식 안내 — 편지권(영구 편지·{free_days}일 편지)이 생기며 달라진 점을 넘겨 보는 다섯 장으로 보여 준다.
 * 사양 `Mutter/docs/specs/2026-11-15-whats-new.md`. 업데이트한 사람에게 한 번 전체 화면으로 덮고, 프로필의 "새 소식"에서 다시 연다.
 *
 * 위에서 아래로: "건너뛰기"(마지막 장에서는 숨김) · 좌우로 넘기는 다섯 장 · 장 표시 점 · "다음"(마지막 장은 "시작하기").
 * "건너뛰기"·"시작하기"가 [onClose]를 부른다. 확인 팝업은 없다. 저장 값(봤는지)은 여기서 다루지 않는다 — 부르는 쪽 몫.
 *
 * @param freeDays 무료 편지가 열려 있는 일수(서버 값) — 제목·설명에 넣는다.
 * @param graceDays 닫힌 뒤 삭제까지의 일수(서버 값).
 */
@Composable
fun WhatsNewScreen(
    freeDays: Int,
    graceDays: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        WhatsNewPage(
            image = R.drawable.whatsnew_1,
            title = stringResource(R.string.whatsnew_1_title),
            body = stringResource(R.string.whatsnew_1_body),
        ),
        WhatsNewPage(
            image = R.drawable.whatsnew_2,
            title = stringResource(R.string.whatsnew_2_title),
            body = stringResource(R.string.whatsnew_2_body, freeDays),
        ),
        WhatsNewPage(
            image = R.drawable.whatsnew_3,
            title = stringResource(R.string.whatsnew_3_title, freeDays),
            body = stringResource(R.string.whatsnew_3_body, freeDays, graceDays),
        ),
        WhatsNewPage(
            image = R.drawable.whatsnew_4,
            title = stringResource(R.string.whatsnew_4_title),
            body = stringResource(R.string.whatsnew_4_body),
        ),
        WhatsNewPage(
            image = R.drawable.whatsnew_5,
            title = stringResource(R.string.whatsnew_5_title),
            body = stringResource(R.string.whatsnew_5_body),
        ),
    )
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            // 다른 화면 위를 덮을 때 빈 자리를 누른 것이 아래 화면으로 새지 않게 여기서 받는다.
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 마지막 장에서는 버튼만 숨기고 자리는 남긴다 — 장을 넘길 때 그림이 위아래로 움직이지 않게.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!isLast) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.whatsnew_skip), style = MutterText.bodyMedium, color = InkSoft)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            WhatsNewPageContent(pages[index])
        }

        PageDots(count = pages.size, current = pagerState.currentPage)

        MutterButton(
            text = stringResource(if (isLast) R.string.whatsnew_start else R.string.whatsnew_next),
            onClick = {
                if (isLast) {
                    onClose()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp)
                .widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun WhatsNewPageContent(page: WhatsNewPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 그림은 남는 높이 안에서 비율을 지킨 채 가장 크게 — 둥근 모서리와 테두리가 그림 가장자리에 붙도록 비율로 크기를 정한다.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val painter = painterResource(page.image)
            val shape = RoundedCornerShape(16.dp)
            Image(
                painter = painter,
                // 화면 낭독용 설명은 제목을 그대로 쓴다(사양).
                contentDescription = page.title,
                modifier = Modifier
                    .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height)
                    .clip(shape)
                    .border(1.dp, Hairline, shape),
            )
        }
        // 제목·설명 자리의 최소 높이를 맞춰 장마다 그림 크기가 크게 달라지지 않게 한다.
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .heightIn(min = 124.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = page.title, style = MutterText.title, color = Ink, textAlign = TextAlign.Center)
            Text(text = page.body, style = MutterText.bodyMedium, color = InkSoft, textAlign = TextAlign.Center)
        }
    }
}

/** 장 표시 점 — 현재 장은 길게. 꾸밈이라 화면 낭독에서는 뺀다. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(targetValue = if (selected) 20.dp else 6.dp, label = "whatsnew-dot")
            Spacer(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .background(if (selected) Gold else InkFaint.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}
