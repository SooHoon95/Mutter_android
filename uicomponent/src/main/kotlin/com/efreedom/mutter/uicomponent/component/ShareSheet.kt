package com.efreedom.mutter.uicomponent.component

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * 시스템 공유 시트(`ACTION_SEND`, text/plain)로 문자열을 보낸다. iOS `ShareLink(item: String)` 대응.
 * EXTRA_SUBJECT는 넣지 않는다 — 카카오톡은 무시하고 메일 앱은 제목으로 써서 동봉 문구가 어색해진다.
 */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).apply {
        // Compose의 LocalContext는 Activity지만, 공용 유틸이라 Application 컨텍스트로 불려도 죽지 않게.
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
