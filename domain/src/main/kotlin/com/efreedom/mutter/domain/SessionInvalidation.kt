package com.efreedom.mutter.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 세션 만료(401 / JWT 무효 / refresh token 소멸) 전역 신호 — iOS `SessionInvalidation`(NotificationCenter) 대응.
 *
 * 데이터 계층([com.efreedom.mutter.data] 에러 매퍼)이 만료를 감지하면 [notifyUnauthorized]를 쏘고,
 * 앱 루트(AppViewModel)가 [events]를 구독해 자동 로그아웃 → 로그인 화면으로 되돌린다.
 * 이 신호가 없으면 세션이 도중에 죽어도 사용자는 깨진 화면에 갇혀 수동 로그아웃 외에 복구 수단이 없다.
 */
object SessionInvalidation {
    // extraBufferCapacity=1 → 구독자가 없을 때 tryEmit도 성공(놓치지 않음).
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events

    fun notifyUnauthorized() {
        _events.tryEmit(Unit)
    }
}
