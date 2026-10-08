package com.efreedom.mutter.domain.model

/** 편지 음악 큐. ref: soundcloud=트랙 URL / hosted=Storage 경로·트랙 id. */
enum class MusicSource(val raw: String) {
    SOUNDCLOUD("soundcloud"),
    HOSTED("hosted"),
}

data class MusicCue(
    val source: MusicSource,
    val ref: String,
    val startMs: Int? = null,
    val title: String? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
)

/**
 * 편지 본문 사진 1장(iOS LetterPhoto 대응).
 * - [path]: Storage 경로(`<ownerId>/<letterId>/<uuid>.jpg`). 아직 업로드 전이면 null.
 * - [pendingJpeg]: 업로드 대기 중인 JPEG 바이트(기기에서 줄인 결과). 저장 흐름이 업로드한 뒤 비운다.
 *
 * equals를 직접 정의하는 이유: data class의 ByteArray 비교는 참조 비교라 경고가 나고 의미도 모호하다.
 * 같은 사진인지는 id·path·크기와 대기 바이트의 "동일 인스턴스" 여부로 충분하다.
 */
class LetterPhoto(
    val id: String,
    val path: String?,
    val width: Int,
    val height: Int,
    val pendingJpeg: ByteArray? = null,
) {
    fun copy(
        id: String = this.id,
        path: String? = this.path,
        width: Int = this.width,
        height: Int = this.height,
        pendingJpeg: ByteArray? = this.pendingJpeg,
    ) = LetterPhoto(id, path, width, height, pendingJpeg)

    override fun equals(other: Any?): Boolean =
        other is LetterPhoto && id == other.id && path == other.path &&
            width == other.width && height == other.height && pendingJpeg === other.pendingJpeg

    override fun hashCode(): Int = listOf(id, path, width, height).hashCode()

    override fun toString(): String =
        "LetterPhoto(id=$id, path=$path, ${width}x$height, pending=${pendingJpeg?.size})"

    companion object {
        /** 편지당 최대 사진 수(세 플랫폼 공통 계약). */
        const val MAX_PER_LETTER = 5

        /** 업로드 전 긴 변 최대 픽셀. */
        const val MAX_LONG_SIDE = 2048

        /** 업로드 JPEG 품질(0~100). 계약의 0.8. */
        const val JPEG_QUALITY = 80
    }
}

/** 편지 본문 블록 — 텍스트 또는 사진. 순서가 곧 화면 순서다(iOS LetterBlock 대응). */
sealed interface LetterBlock {
    data class Text(val text: String) : LetterBlock
    data class Photo(val photo: LetterPhoto) : LetterBlock
}

/** blocks ↔ body 호환 헬퍼. 사진 이전 호출부(body만 아는 화면)를 깨지 않기 위해 둔다. */
object LetterBlocks {
    /** 텍스트 블록만 "\n\n"으로 잇는다(사진은 건너뜀) — 기존 body와 같은 모양. */
    fun body(blocks: List<LetterBlock>): String =
        blocks.filterIsInstance<LetterBlock.Text>().joinToString("\n\n") { it.text }

    fun fromBody(body: String): List<LetterBlock> = listOf(LetterBlock.Text(body))

    fun photos(blocks: List<LetterBlock>): List<LetterPhoto> =
        blocks.filterIsInstance<LetterBlock.Photo>().map { it.photo }

    /** 이미 업로드된 사진 경로 집합. 저장 시 "지워진 사진" 계산에 쓴다. */
    fun photoPaths(blocks: List<LetterBlock>): Set<String> =
        photos(blocks).mapNotNull { it.path }.toSet()
}

/** 편지 = 테마 본문(텍스트·사진 블록) + 음악 1곡(nil=무음). */
data class Letter(
    val id: String,
    val title: String,
    val blocks: List<LetterBlock>,
    val templateId: String,
    val cue: MusicCue? = null,
) {
    constructor(id: String, title: String, body: String, templateId: String, cue: MusicCue? = null) :
        this(id, title, LetterBlocks.fromBody(body), templateId, cue)

    val body: String get() = LetterBlocks.body(blocks)
}

/** 작성 입력(Compose). */
data class LetterDraft(
    val title: String,
    val blocks: List<LetterBlock>,
    val templateId: String,
    val cue: MusicCue? = null,
) {
    constructor(title: String, body: String, templateId: String, cue: MusicCue? = null) :
        this(title, LetterBlocks.fromBody(body), templateId, cue)

    val body: String get() = LetterBlocks.body(blocks)
}

/** 수신자 표시용(발신자 신원 없음). */
data class LetterPayload(
    val id: String,
    val title: String,
    val blocks: List<LetterBlock>,
    val templateId: String,
    val cue: MusicCue?,
    val audioDisabled: Boolean,
) {
    constructor(
        id: String,
        title: String,
        body: String,
        templateId: String,
        cue: MusicCue?,
        audioDisabled: Boolean,
    ) : this(id, title, LetterBlocks.fromBody(body), templateId, cue, audioDisabled)

    val body: String get() = LetterBlocks.body(blocks)
}

/** 저장 결과 — 업로드로 path가 채워진 블록을 돌려줘 다음 저장이 같은 사진을 다시 올리지 않게 한다. */
data class SavedLetter(
    val id: String,
    val blocks: List<LetterBlock>,
)

/** 파생 read-model: 전달 링크 존재 OR 타인 inbox 존재 → isSent. */
data class LetterWithStatus(
    val letter: Letter,
    val isSent: Boolean,
)
