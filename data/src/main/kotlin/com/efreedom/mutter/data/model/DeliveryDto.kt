package com.efreedom.mutter.data.model

import com.efreedom.mutter.data.support.LetterContentCodec
import com.efreedom.mutter.data.support.ParagraphDto
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * issue_link RPC 파라미터.
 *
 * nullable 인자에 `= null` 기본값을 두지 않는다. 전역 직렬화기가 `encodeDefaults=false`라
 * 기본값(null)과 같은 필드는 JSON에서 생략되는데, PostgREST는 "제공된 인자명 집합"으로 함수를
 * 매칭한다. issue_link의 p_password·p_expires_at는 서버에 default가 없어(마이그레이션 0024),
 * 생략하면 함수를 못 찾아 발급이 전부 실패한다 → 항상 명시 인코딩(iOS force-encode 대응).
 */
@Serializable
data class IssueLinkParams(
    @SerialName("p_letter_id") val letterId: String,
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String?,
    @SerialName("p_expires_at") val expiresAt: String?,
    @SerialName("p_reveal_at") val revealAt: String?,
)

/** get_letter_by_token RPC 파라미터. p_password는 서버 default가 없어 null도 명시 전송(위 설명 참고). */
@Serializable
data class OpenLinkParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String?,
)

/** issue_link RPC 반환 row. */
@Serializable
data class RpcDeliveryLinkRow(
    @SerialName("letter_id") val letterId: String,
    val token: String,
    @SerialName("has_password") val hasPassword: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("reveal_at") val revealAt: String? = null,
    val revoked: Boolean = false,
) {
    fun toDomain(): DeliveryLink = DeliveryLink(
        token = token,
        letterId = letterId,
        hasPassword = hasPassword,
        expiresAt = expiresAt?.let { Instant.parse(it) },
        revealAt = revealAt?.let { Instant.parse(it) },
        revoked = revoked,
    )
}

/** delivery_links 테이블 row. */
@Serializable
data class DeliveryLinkRow(
    @SerialName("letter_id") val letterId: String,
    val token: String,
    @SerialName("has_password") val hasPassword: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("reveal_at") val revealAt: String? = null,
    val revoked: Boolean = false,
) {
    fun toDomain(): DeliveryLink = DeliveryLink(
        token = token,
        letterId = letterId,
        hasPassword = hasPassword,
        expiresAt = expiresAt?.let { Instant.parse(it) },
        revealAt = revealAt?.let { Instant.parse(it) },
        revoked = revoked,
    )
}

/** get_letter_by_token RPC 반환 row. */
@Serializable
data class LetterPayloadDto(
    val id: String,
    val title: String,
    val paragraphs: List<ParagraphDto>,
    @SerialName("template_id") val templateId: String,
    @SerialName("audio_disabled") val audioDisabled: Boolean = false,
) {
    fun toDomain(): LetterPayload = LetterPayload(
        id = id,
        title = title,
        blocks = LetterContentCodec.blocks(paragraphs),
        templateId = templateId,
        cue = LetterContentCodec.cue(paragraphs),
        audioDisabled = audioDisabled,
    )
}
