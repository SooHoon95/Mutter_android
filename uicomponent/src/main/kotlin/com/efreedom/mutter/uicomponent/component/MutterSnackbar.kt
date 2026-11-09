package com.efreedom.mutter.uicomponent.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.efreedom.mutter.uicomponent.theme.GoldLight
import com.efreedom.mutter.uicomponent.theme.Ink
import com.efreedom.mutter.uicomponent.theme.Ivory
import kotlinx.coroutines.withTimeoutOrNull

/** 앱 공통 하단 토스트 — 잉크 배경·아이보리 글자(프로필 저장 토스트와 같은 톤), 액션은 밝은 골드. */
@Composable
fun MutterSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = Ink,
            contentColor = Ivory,
            actionColor = GoldLight,
        )
    }
}

/**
 * "되돌리기" 스낵바를 [millis] 동안 띄우고, 사용자가 액션을 눌렀으면 true를 돌려준다.
 *
 * M3 SnackbarDuration은 Short(4초)·Long(10초)뿐이라 세 플랫폼 공통 5초를 맞추려고 Indefinite로 띄운 뒤
 * 타임아웃으로 끊는다. showSnackbar는 취소되면 스낵바를 닫으므로 시간 초과·호출 코루틴 취소(화면 이탈,
 * 다음 스낵바로 교체) 모두 스낵바가 남지 않는다. 취소는 예외로 전파되니 정리 작업은 호출부 finally에 둔다.
 */
suspend fun SnackbarHostState.showUndo(
    message: String,
    actionLabel: String = "되돌리기",
    millis: Long = 5_000,
): Boolean {
    val result = withTimeoutOrNull(millis) {
        showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Indefinite)
    }
    return result == SnackbarResult.ActionPerformed
}
