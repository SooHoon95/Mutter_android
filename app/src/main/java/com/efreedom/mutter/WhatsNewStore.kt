package com.efreedom.mutter

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 새 소식 안내(편지권)를 봤는지 기기에 기억한다 — 값 하나 `whatsNewTickets`: 없음 → `pending` → `seen`.
 * 사양 `Mutter/docs/specs/2026-11-15-whats-new.md` "언제 뜨나". 판정은 [AppViewModel]이 한다.
 */
@Singleton
class WhatsNewStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    enum class Status(val raw: String) {
        /** 이전 버전을 쓰던 사람 — 유료 전환된 뒤 처음 앱을 열 때 보여 준다. */
        PENDING("pending"),

        /** 이미 봤거나, 새로 설치해서 보여 줄 필요가 없다. */
        SEEN("seen"),
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 저장된 값. 아직 정해진 적이 없으면 null(모르는 값도 null로 보아 다시 정한다). */
    var status: Status?
        get() = prefs.getString(KEY, null).let { raw -> Status.entries.firstOrNull { it.raw == raw } }
        set(value) = prefs.edit { putString(KEY, value?.raw) }

    companion object {
        private const val PREFS_NAME = "mutter_whats_new"
        private const val KEY = "whatsNewTickets"
    }
}
