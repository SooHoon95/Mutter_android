package com.efreedom.mutter.data.support

import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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

/** 사진 단락의 사진 정보. path는 `<ownerId>/<letterId>/<uuid>.jpg`(비공개 버킷 letter-photos). */
@Serializable
data class ParagraphPhotoDto(
    val path: String,
    val width: Int,
    val height: Int,
)

/**
 * jsonb paragraphs 배열의 한 단락. camelCase 유지(서버 jsonb 계약).
 * cue·photo는 선택 필드라 기본값 null — 구 형식(photo 없음)도 그대로 디코드되고,
 * encodeDefaults=false라 null이면 키 자체를 쓰지 않는다(웹·iOS가 보내는 모양과 같다).
 * text는 키가 빠진 행도 읽히도록 기본값 ""를 두되(iOS·웹과 동일), 사진 단락도 `"text": ""`를 보내야 하므로
 * @EncodeDefault로 기본값이어도 항상 쓴다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ParagraphDto(
    val id: String,
    val order: Int,
    @EncodeDefault val text: String = "",
    val cue: MusicCueDto? = null,
    val photo: ParagraphPhotoDto? = null,
)

/**
 * letters.paragraphs(jsonb) ↔ 도메인 blocks/cue 변환(iOS LetterContentCodec 이식).
 * jsonb 내부 필드는 서버와 camelCase 계약 — @SerialName 미사용.
 */
object LetterContentCodec {

    /**
     * paragraphs → blocks. order 정렬 후,
     * - photo가 있는 단락은 사진 블록(단락 id를 사진 id로 재사용 — 다시 저장해도 id가 안정적이다).
     * - 연속된 텍스트 단락은 "\n\n"으로 이어 텍스트 블록 하나로 합친다(편집기에서 한 칸으로 보이게).
     */
    fun blocks(from: List<ParagraphDto>): List<LetterBlock> {
        val result = mutableListOf<LetterBlock>()
        for (p in from.sortedBy { it.order }) {
            val photo = p.photo
            if (photo != null) {
                result += LetterBlock.Photo(
                    LetterPhoto(id = p.id, path = photo.path, width = photo.width, height = photo.height),
                )
                continue
            }
            // 빈 텍스트 단락(사진만 있는 편지의 큐 앵커 등)은 블록으로 만들지 않는다 — iOS 코덱과 동일.
            if (p.text.isBlank()) continue
            val last = result.lastOrNull()
            if (last is LetterBlock.Text) {
                result[result.lastIndex] = LetterBlock.Text(last.text + "\n\n" + p.text)
            } else {
                result += LetterBlock.Text(p.text)
            }
        }
        return result
    }

    /** paragraphs → body: 텍스트 단락만 order 정렬 후 "\n\n" 연결(사진 단락 제외). */
    fun body(from: List<ParagraphDto>): String =
        from.sortedBy { it.order }.filter { it.photo == null }.joinToString("\n\n") { it.text }

    /** paragraphs → MusicCue: 첫 번째 non-null cue → 도메인 MusicCue. */
    fun cue(from: List<ParagraphDto>): MusicCue? =
        from.sortedBy { it.order }.firstNotNullOfOrNull { it.cue }?.toDomain()

    /** body + MusicCue → paragraphs(사진 없는 구 호출부용). */
    fun paragraphs(body: String, cue: MusicCue?): List<ParagraphDto> =
        paragraphs(listOf(LetterBlock.Text(body)), cue)

    /**
     * blocks + MusicCue → paragraphs.
     * - 텍스트 블록은 "\n\n"(빈 줄) 기준으로 나눠 단락 여러 개로 만든다(웹 뷰어의 단락 단위 렌더와 맞춤).
     * - 사진 블록은 text="" + photo 단락 하나. 경로가 없는(업로드 전) 사진은 계약 위반이라 거부한다 —
     *   저장 흐름(LetterUseCase.saveWithPhotos)이 업로드 후에만 여기로 넘긴다.
     * - cue는 "첫 텍스트 단락"에만 붙인다. 텍스트 단락이 하나도 없으면 빈 텍스트 단락을 맨 앞에 둔다 —
     *   구 형식 body=""가 [""] 단락 하나였던 것과 같고, cue가 사라지지 않게 한다.
     */
    fun paragraphs(blocks: List<LetterBlock>, cue: MusicCue?): List<ParagraphDto> {
        val drafts = mutableListOf<Pair<String, ParagraphPhotoDto?>>()
        for (block in blocks) {
            when (block) {
                // 앞뒤 공백을 다듬고 빈 조각은 버린다(끝의 빈 줄이 빈 단락으로 쌓이지 않게 — iOS 코덱과 동일).
                is LetterBlock.Text -> block.text.split("\n\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { drafts += it to null }
                is LetterBlock.Photo -> {
                    val path = requireNotNull(block.photo.path) { "업로드 전 사진은 paragraphs로 저장할 수 없다" }
                    drafts += "" to ParagraphPhotoDto(path, block.photo.width, block.photo.height)
                }
            }
        }
        if (drafts.none { it.second == null }) drafts.add(0, "" to null)

        val photoIds = blocks.filterIsInstance<LetterBlock.Photo>().map { it.photo.id }.iterator()
        val cueDto = cue?.toDto()
        var cuePlaced = false
        return drafts.mapIndexed { index, (text, photo) ->
            val attachCue = photo == null && !cuePlaced
            if (attachCue) cuePlaced = true
            ParagraphDto(
                id = if (photo != null) photoIds.next() else UUID.randomUUID().toString(),
                order = index,
                text = text,
                cue = if (attachCue) cueDto else null,
                photo = photo,
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
