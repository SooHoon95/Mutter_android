package com.efreedom.mutter.data.model

import com.efreedom.mutter.data.support.Timestamps
import com.efreedom.mutter.domain.model.LetterAccessKind
import com.efreedom.mutter.domain.model.LetterAccessState
import com.efreedom.mutter.domain.model.LetterCover
import com.efreedom.mutter.domain.model.LetterKeepRequest
import com.efreedom.mutter.domain.model.LetterReader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// 영구 편지권 계약(docs/specs/permanent-letter-contract.md)의 RPC·Edge Function DTO.
// 파라미터 DTO에는 `= null` 기본값을 두지 않는다(encodeDefaults=false → 키 생략 → PGRST202).

/** get_letter_cover 파라미터. */
@Serializable
data class LetterTokenParam(
    @SerialName("p_token") val token: String,
)

// get_letter_readers · make_letter_permanent 파라미터는 기존 LetterIdParam(LetterDto.kt)을 쓴다.

/** keep_received_letter 파라미터. p_password는 암호가 없어도 null을 명시로 보낸다. */
@Serializable
data class KeepReceivedLetterParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String?,
)

/** request_keep_letter 파라미터. p_password는 암호가 없어도 null을 명시로 보낸다(기본값 금지). */
@Serializable
data class RequestKeepLetterParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String?,
)

/** get_letter_cover 반환(json 단일 객체). 봉인(`sealed: true`)이면 나머지 키가 없다. */
@Serializable
data class LetterCoverDto(
    val sealed: Boolean = true,
    @SerialName("template_id") val templateId: String? = null,
    val title: String? = null,
    @SerialName("sender_nickname") val senderNickname: String? = null,
    val state: String? = null,
    @SerialName("access_until") val accessUntil: String? = null,
    @SerialName("delete_at") val deleteAt: String? = null,
    @SerialName("login_required") val loginRequired: Boolean = false,
    // 서버 반영 전에는 키가 없다 → false.
    @SerialName("keep_requested") val keepRequested: Boolean = false,
) {
    fun toDomain(): LetterCover = LetterCover(
        sealed = sealed,
        templateId = templateId,
        title = title,
        senderNickname = senderNickname,
        kind = LetterAccessKind.from(state),
        accessUntil = Timestamps.parse(accessUntil),
        deleteAt = Timestamps.parse(deleteAt),
        loginRequired = loginRequired,
        keepRequested = keepRequested,
    )
}

/** get_letter_access_states 한 행. */
@Serializable
data class LetterAccessStateRow(
    @SerialName("letter_id") val letterId: String,
    val state: String,
    @SerialName("access_until") val accessUntil: String? = null,
    @SerialName("delete_at") val deleteAt: String? = null,
    @SerialName("kept_by_recipient") val keptByRecipient: Boolean = false,
    // 서버 반영 전에는 키가 없다 → 0.
    @SerialName("keep_request_count") val keepRequestCount: Int = 0,
) {
    fun toDomain(): LetterAccessState = LetterAccessState(
        letterId = letterId,
        kind = LetterAccessKind.from(state),
        accessUntil = Timestamps.parse(accessUntil),
        deleteAt = Timestamps.parse(deleteAt),
        keptByRecipient = keptByRecipient,
        keepRequestCount = keepRequestCount,
    )
}

/** get_letter_readers 한 행. */
@Serializable
data class LetterReaderRow(
    @SerialName("user_id") val userId: String,
    val nickname: String? = null,
    @SerialName("first_opened_at") val firstOpenedAt: String,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
    @SerialName("open_count") val openCount: Int = 0,
) {
    fun toDomain(): LetterReader {
        val first = Timestamps.parse(firstOpenedAt) ?: Instant.EPOCH
        return LetterReader(
            userId = userId,
            nickname = nickname,
            firstOpenedAt = first,
            lastOpenedAt = Timestamps.parse(lastOpenedAt) ?: first,
            openCount = openCount,
        )
    }
}

/** get_letter_keep_requests 한 행(요청한 순). */
@Serializable
data class LetterKeepRequestRow(
    @SerialName("user_id") val userId: String,
    val nickname: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
) {
    fun toDomain(): LetterKeepRequest = LetterKeepRequest(
        userId = userId,
        nickname = nickname,
        requestedAt = Timestamps.parse(requestedAt) ?: Instant.EPOCH,
    )
}

/** duplicate-letter 요청 본문. */
@Serializable
internal data class DuplicateLetterRequest(
    val letterId: String,
)

/** duplicate-letter `200 {"letterId": "<새 편지 uuid>"}`. */
@Serializable
internal data class DuplicateLetterResponse(
    val letterId: String,
)
