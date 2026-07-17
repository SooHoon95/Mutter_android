package com.efreedom.mutter.domain.model

import java.time.Instant

/** 연결된 상대(독점 1:1 연결). */
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
    /** 둘 다 미연결일 때만 수락 가능(독점 1:1 불변식). */
    val canAccept: Boolean
        get() = !isSelf && !alreadyConnected && !viewerHasConnection && !inviterHasConnection
}

/** 주고받은 상대(스레드 목록의 한 항목). */
data class Counterpart(
    val userId: String,
    val nickname: String?,
    val exchangeCount: Int,
) {
    val id: String get() = userId
}
