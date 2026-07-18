package com.efreedom.mutter.data.model

import com.efreedom.mutter.data.support.LetterContentCodec
import com.efreedom.mutter.data.support.ParagraphDto
import com.efreedom.mutter.domain.model.DeliveryLink
import com.efreedom.mutter.domain.model.LetterPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** issue_link RPC 파라미터. */
@Serializable
data class IssueLinkParams(
    @SerialName("p_letter_id") val letterId: String,
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String? = null,
    @SerialName("p_expires_at") val expiresAt: String? = null,
    @SerialName("p_reveal_at") val revealAt: String? = null,
)

/** get_letter_by_token RPC 파라미터. */
@Serializable
data class OpenLinkParams(
    @SerialName("p_token") val token: String,
    @SerialName("p_password") val password: String? = null,
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
        body = LetterContentCodec.body(paragraphs),
        templateId = templateId,
        cue = LetterContentCodec.cue(paragraphs),
        audioDisabled = audioDisabled,
    )
}
