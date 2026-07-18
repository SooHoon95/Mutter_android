package com.efreedom.mutter.feature.connections

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 수락 완료한 초대 토큰을 로컬에 기억한다. iOS AcceptedInviteStore(enum) 대응.
 *
 * 초대 토큰은 서버에서 단일 사용이라 첫 수락으로 소비된다.
 * 이후 같은 링크를 다시 열면 "만료" 오인 표시를 방지하기 위해
 * 이 스토어가 수락 완료 여부를 기억해 재오픈 시 연결 완료 상태를 바로 보여준다.
 */
@Singleton
class AcceptedInviteStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun contains(token: String): Boolean =
        prefs.getStringSet(KEY, emptySet())?.contains(token) == true

    fun markAccepted(token: String) {
        val updated = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        updated.add(token)
        prefs.edit { putStringSet(KEY, updated) }
    }

    companion object {
        private const val PREFS_NAME = "mutter_accepted_invites"
        private const val KEY = "mutter.accepted_invite_tokens"
    }
}
