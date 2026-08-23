package com.efreedom.mutter.push

import android.util.Log
import com.efreedom.mutter.domain.usecase.PushTokenUseCase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 로그인/로그아웃 시점의 FCM 토큰 관리.
 *
 * [MutterMessagingService.onNewToken]은 토큰이 "바뀔 때"만 불린다. 이미 토큰을 가진 기기에서
 * 로그인하면 콜백이 오지 않으므로, 로그인 직후 현재 토큰을 직접 읽어 올려야 한다.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val pushTokenUseCase: PushTokenUseCase,
) {

    /**
     * 현재 토큰을 서버에 등록. 실패(미로그인·네트워크·FCM 미사용 기기)는 삼킨다 — 알림은 부가 기능이다.
     * 다만 조용히 삼키면 "알림이 안 온다"를 추적할 수 없으므로 실패 사유는 남긴다.
     */
    suspend fun register() {
        runCatching {
            val token = currentToken()
            if (token.isBlank()) {
                Log.w(TAG, "FCM 토큰이 비어 있어 등록을 건너뜀")
                return
            }
            pushTokenUseCase.register(token = token, deviceId = null)
            Log.i(TAG, "푸시 토큰 등록 완료 (${token.take(12)}…)")
        }.onFailure { Log.w(TAG, "푸시 토큰 등록 실패: ${it::class.simpleName}: ${it.message}") }
    }

    /**
     * 로그아웃 시 토큰 폐기.
     *
     * 서버에는 토큰 삭제 RPC가 없다(0028은 upsert만 제공). 대신 기기 토큰을 버리면 서버에 남은
     * 행은 UNREGISTERED가 되고, send-push가 발송 실패 시 그 행을 스스로 정리한다.
     * → 다른 계정으로 로그인한 뒤 이전 사용자의 알림을 받는 일이 없다.
     */
    suspend fun unregister() {
        runCatching {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener { cont.resume(Unit) }
            }
        }
    }

    private companion object {
        const val TAG = "MutterPush"
    }

    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it ?: "") }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
