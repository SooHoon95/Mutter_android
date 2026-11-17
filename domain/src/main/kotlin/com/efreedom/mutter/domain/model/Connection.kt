package com.efreedom.mutter.domain.model

import java.time.Instant

/** 연결된 상대(N:N). */
data class Connection(
    val userId: String,
    val nickname: String?,
    val connectedAt: Instant,
) {
    val id: String get() = userId
}

/** 연결 초대 미리보기 — 초대 링크를 열었을 때의 상태(수락 가능 여부 판단). */
data class ConnectInvite(
    val inviterId: String,
    val inviterNickname: String?,
    val isSelf: Boolean,
    val alreadyConnected: Boolean,
    val viewerHasConnection: Boolean,
    val inviterHasConnection: Boolean,
) {
    /**
     * 자기 초대가 아니고 이 상대와 아직 연결되지 않았으면 수락 가능.
     * N:N — 서버 `0027_connections_n_to_n.sql`은 같은 상대와의 중복만 막는다(웹 `Connect.tsx`도 같은 조건).
     * 그래서 [viewerHasConnection]·[inviterHasConnection](연결이 하나라도 있으면 true)은 수락을 막지 않는다.
     */
    val canAccept: Boolean
        get() = !isSelf && !alreadyConnected
}

/** 주고받은 상대(스레드 목록의 한 항목). */
data class Counterpart(
    val userId: String,
    val nickname: String?,
    val exchangeCount: Int,
    /** 상대가 탈퇴했다 — 받은 편지는 남지만 편지를 보낼 수는 없다. */
    val isGone: Boolean = false,
) {
    val id: String get() = userId
}
