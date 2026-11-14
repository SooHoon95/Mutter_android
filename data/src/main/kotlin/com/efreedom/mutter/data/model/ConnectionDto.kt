package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.ConnectInvite
import com.efreedom.mutter.domain.model.Connection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 여러 RPC에서 공유하는 단일 토큰 파라미터
 * (Connection, Inbox, Receipt, Delivery 등).
 */
@Serializable
data class TokenParam(
    @SerialName("p_token") val token: String,
)

/** get_connect_invite RPC 반환 row. */
@Serializable
data class ConnectInviteRow(
    @SerialName("inviter_id") val inviterId: String,
    @SerialName("inviter_nickname") val inviterNickname: String? = null,
    @SerialName("is_self") val isSelf: Boolean,
    @SerialName("already_connected") val alreadyConnected: Boolean,
    @SerialName("viewer_has_connection") val viewerHasConnection: Boolean? = null,
    @SerialName("inviter_has_connection") val inviterHasConnection: Boolean? = null,
) {
    fun toDomain(): ConnectInvite = ConnectInvite(
        inviterId = inviterId,
        inviterNickname = inviterNickname,
        isSelf = isSelf,
        alreadyConnected = alreadyConnected,
        viewerHasConnection = viewerHasConnection ?: false,
        inviterHasConnection = inviterHasConnection ?: false,
    )
}

/** get_my_connections RPC 반환 row. */
@Serializable
data class ConnectionRow(
    @SerialName("user_id") val userId: String,
    val nickname: String? = null,
    @SerialName("connected_at") val connectedAt: String,
) {
    fun toDomain(): Connection = Connection(
        userId = userId,
        nickname = nickname,
        connectedAt = Instant.parse(connectedAt),
    )
}

/** disconnect_connection RPC 파라미터. */
@Serializable
data class DisconnectParams(
    @SerialName("p_other_user") val otherUser: String,
)

/** send_to_connection RPC 파라미터. */
@Serializable
data class SendToConnectionParams(
    @SerialName("p_letter_id") val letterId: String,
    @SerialName("p_recipient") val recipient: String,
    @SerialName("p_token") val token: String,
    /**
     * 영구 편지로 보낸다 — 서버가 발송과 편지권 1장 사용을 한 트랜잭션으로 처리한다.
     * **true일 때만 전송된다**(기본값 false + `encodeDefaults=false`) — 이 인자가 없는 예전 서버에서도 일반 발송이 되게.
     */
    @SerialName("p_permanent") val permanent: Boolean = false,
)
