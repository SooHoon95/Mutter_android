package com.efreedom.mutter.data.model

import com.efreedom.mutter.data.support.LetterContentCodec
import com.efreedom.mutter.data.support.ParagraphDto
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterWithStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** letters 테이블 INSERT 입력. */
@Serializable
data class LetterInsertDto(
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    val paragraphs: List<ParagraphDto>,
    @SerialName("template_id") val templateId: String,
)

/** letters 테이블 UPDATE 입력. */
@Serializable
data class LetterUpdateDto(
    val title: String,
    val paragraphs: List<ParagraphDto>,
    @SerialName("template_id") val templateId: String,
    @SerialName("updated_at") val updatedAt: String,
)

/** letters 테이블 row. */
@Serializable
data class LetterRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    val paragraphs: List<ParagraphDto>,
    @SerialName("template_id") val templateId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toDomain(): Letter = Letter(
        id = id,
        title = title,
        body = LetterContentCodec.body(paragraphs),
        templateId = templateId,
        cue = LetterContentCodec.cue(paragraphs),
    )
}

/** get_my_letters_with_status RPC row. */
@Serializable
data class LetterWithStatusRow(
    // RPC get_my_letters_with_status는 컬럼명 'id'를 반환한다(마이그레이션 0025 returns table(id …)).
    // 'letter_id'로 디코드하면 MissingFieldException으로 홈 목록 전체가 실패한다.
    @SerialName("id") val letterId: String,
    val title: String,
    @SerialName("template_id") val templateId: String,
    val paragraphs: List<ParagraphDto>,
    @SerialName("is_sent") val isSent: Boolean,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toDomain(): LetterWithStatus = LetterWithStatus(
        letter = Letter(
            id = letterId,
            title = title,
            body = LetterContentCodec.body(paragraphs),
            templateId = templateId,
            cue = LetterContentCodec.cue(paragraphs),
        ),
        isSent = isSent,
    )
}
