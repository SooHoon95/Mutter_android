package com.efreedom.mutter.data.support

import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import kotlinx.serialization.Serializable
import java.util.UUID

/** jsonb paragraphs 배열 내 음악 큐. camelCase 유지(서버 jsonb 계약). */
@Serializable
data class MusicCueDto(
    val sourceType: String,
    val ref: String,
    val startMs: Int? = null,
    val title: String? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
)

/** jsonb paragraphs 배열의 한 단락. camelCase 유지(서버 jsonb 계약). */
@Serializable
data class ParagraphDto(
    val id: String,
    val order: Int,
    val text: String,
    val cue: MusicCueDto? = null,
)

/**
 * letters.paragraphs(jsonb) ↔ 도메인 body/cue 변환(iOS LetterContentCodec 이식).
 * jsonb 내부 필드는 서버와 camelCase 계약 — @SerialName 미사용.
 */
object LetterContentCodec {

    /** paragraphs → body: order 정렬 후 "\n\n" 연결. */
    fun body(from: List<ParagraphDto>): String =
        from.sortedBy { it.order }.joinToString("\n\n") { it.text }

    /** paragraphs → MusicCue: 첫 번째 non-null cue → 도메인 MusicCue. */
    fun cue(from: List<ParagraphDto>): MusicCue? =
        from.sortedBy { it.order }.firstNotNullOfOrNull { it.cue }?.toDomain()

    /** body + MusicCue → paragraphs: "\n\n" 분리, cue는 paragraph[0]에만 첨부. */
    fun paragraphs(body: String, cue: MusicCue?): List<ParagraphDto> {
        val parts = body.split("\n\n")
        val cueDto = cue?.toDto()
        return parts.mapIndexed { index, text ->
            ParagraphDto(
                id = UUID.randomUUID().toString(),
                order = index,
                text = text,
                cue = if (index == 0) cueDto else null,
            )
        }
    }

    private fun MusicCueDto.toDomain(): MusicCue = MusicCue(
        source = when (sourceType) {
            "hosted" -> MusicSource.HOSTED
            else -> MusicSource.SOUNDCLOUD
        },
        ref = ref,
        startMs = startMs,
        title = title,
        author = author,
        sourceUrl = sourceUrl,
    )

    private fun MusicCue.toDto(): MusicCueDto = MusicCueDto(
        sourceType = source.raw,
        ref = ref,
        startMs = startMs,
        title = title,
        author = author,
        sourceUrl = sourceUrl,
    )
}
