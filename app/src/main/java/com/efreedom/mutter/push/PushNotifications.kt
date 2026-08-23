package com.efreedom.mutter.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.efreedom.mutter.MainActivity
import com.efreedom.mutter.R

/**
 * 푸시 알림 표시 유틸.
 *
 * 서버(send-push Edge Function)는 FCM HTTP v1로 `notification`(title/body) + `data`를 함께 보낸다.
 * 안드로이드는 `notification`이 있는 메시지를 **백그라운드에서만** 시스템이 자동 표시하고,
 * 포그라운드에서는 앱의 onMessageReceived로만 전달한다 → 그때 이 유틸로 직접 띄운다.
 */
object PushNotifications {

    /** 채널 ID. 매니페스트의 default_notification_channel_id와 같아야 한다(백그라운드 자동 표시 경로). */
    const val CHANNEL_ID = "mutter_default"

    /** 서버 트리거(마이그레이션 0029)가 data.type으로 보내는 값. */
    private const val TYPE_LETTER_RECEIVED = "letter_received"

    /** 앱 시작 시 1회. 채널이 없으면 Android 8+ 에서 알림이 조용히 버려진다. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.push_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * 알림 하나를 표시한다. 권한(POST_NOTIFICATIONS)이 없으면 조용히 무시된다 —
     * 알림은 부가 기능이므로 실패가 앱 동작에 영향을 주지 않아야 한다.
     */
    fun show(context: Context, title: String?, body: String?, data: Map<String, String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_push)
            .setContentTitle(title ?: context.getString(R.string.app_name))
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, data))
            .build()

        // 같은 편지의 알림이 쌓이지 않도록 토큰을 태그로 쓴다(없으면 type).
        val tag = data["token"] ?: data["type"] ?: "mutter"
        runCatching { NotificationManagerCompat.from(context).notify(tag, 0, notification) }
    }

    /**
     * 탭했을 때 열 화면.
     *
     * 편지 도착 알림은 `data.token`이 오므로 기존 딥링크 경로(`mutter://l/<token>`)를 그대로 재사용한다
     * — MainActivity가 Deeplink.parse로 해석해 뷰어를 띄운다. 별도 라우팅 코드가 필요 없다.
     * 그 외(열람 알림 letter_id, 연결 수락 user_id)는 대응 화면이 없어 앱만 연다.
     */
    private fun contentIntent(context: Context, data: Map<String, String>): PendingIntent {
        val token = data["token"]
        val intent = if (data["type"] == TYPE_LETTER_RECEIVED && !token.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("mutter://l/$token"))
                .setPackage(context.packageName)
        } else {
            Intent(context, MainActivity::class.java)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
