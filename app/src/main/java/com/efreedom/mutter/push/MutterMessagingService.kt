package com.efreedom.mutter.push

import com.efreedom.mutter.domain.usecase.PushTokenUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM 수신 서비스.
 *
 * 서버는 `push_tokens`에 등록된 토큰으로만 발송하므로(send-push Edge Function),
 * 여기서 토큰을 올려주지 않으면 안드로이드는 아무 알림도 받지 못한다.
 */
@AndroidEntryPoint
class MutterMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenUseCase: PushTokenUseCase

    // 서비스 수명과 무관하게 등록을 마치도록 자체 스코프를 둔다. SupervisorJob으로 실패를 격리.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 토큰 갱신(설치 직후·앱 데이터 삭제·주기적 로테이션) 시 호출된다.
     *
     * upsert_push_token은 auth.uid()를 요구하므로 미로그인 상태면 AUTH_REQUIRED로 실패한다.
     * 그건 정상 흐름이다 — 로그인 시점에 [PushTokenRegistrar]가 현재 토큰을 다시 올린다.
     */
    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { pushTokenUseCase.register(token = token, deviceId = null) }
        }
    }

    /**
     * 포그라운드 수신(및 data-only 메시지) 처리.
     * 백그라운드에서 `notification` 메시지는 시스템이 자동 표시하므로 여기로 오지 않는다.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title == null && body == null) return
        PushNotifications.show(applicationContext, title, body, message.data)
    }
}
