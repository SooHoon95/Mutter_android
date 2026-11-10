package com.efreedom.mutter.data.model

import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.domain.model.InboxItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** get_my_inbox RPC 반환 row. */
@Serializable
data class InboxRow(
    @SerialName("letter_id") val letterId: String,
    val token: String,
    val title: String,
    @SerialName("saved_at") val savedAt: String,
) {
    fun toDomain(): InboxItem = InboxItem(
        letterId = letterId,
        token = token,
        title = title,
        savedAt = Instant.parse(savedAt),
    )
}

/**
 * restore_inbox_items / purge_inbox_items RPC 파라미터(uuid[]). `= null`·빈 기본값을 두지 않는다 —
 * encodeDefaults=false라 키가 빠지면 PostgREST가 함수 시그니처를 못 찾는다(PGRST202).
 */
@Serializable
data class LetterIdsParam(
    @SerialName("p_letter_ids") val letterIds: List<String>,
)

/** get_my_deleted_inbox RPC row(받은 편지 삭제함). purge_at = deleted_at + 30일(서버 계산). */
@Serializable
data class DeletedInboxRow(
    @SerialName("letter_id") val letterId: String,
    val token: String,
    // 제목·닉네임은 left join 결과라 null일 수 있다 — 디코드 실패로 목록 전체가 깨지지 않게 nullable로 받는다.
    val title: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_nickname") val senderNickname: String? = null,
    @SerialName("deleted_at") val deletedAt: String,
    @SerialName("purge_at") val purgeAt: String,
) {
    fun toDomain(): DeletedInboxItem = DeletedInboxItem(
        letterId = letterId,
        token = token,
        title = title.orEmpty(),
        senderId = senderId,
        senderNickname = senderNickname,
        deletedAt = Instant.parse(deletedAt),
        purgeAt = Instant.parse(purgeAt),
    )
}
