package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 당겨서 새로고침(PTR)이 통하는 전체 화면 컨테이너 — 빈 상태·로딩 상태용.
 *
 * `PullToRefreshBox`는 자식 스크롤 컨테이너가 소비하지 않고 넘겨주는 nested scroll 델타로
 * 당김을 감지한다. 그래서 빈 상태를 그냥 [Box]로 두면 스크롤 컨테이너가 없어 아무리 당겨도
 * 새로고침이 일어나지 않는다(목록이 있을 때만 되는 증상). iOS는 `ScrollView` + `.refreshable`
 * 이라 내용이 비어도 항상 당겨지므로, 그 동작에 맞춘다.
 *
 * 단일 아이템 [LazyColumn]으로 감싸 스크롤 델타를 흘리고, `fillParentMaxSize`로 뷰포트를 채워
 * 기존 중앙 정렬을 그대로 유지한다.
 */
@Composable
fun PullableFullSizeBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}
