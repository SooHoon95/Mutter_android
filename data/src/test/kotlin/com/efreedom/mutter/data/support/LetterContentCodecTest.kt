package com.efreedom.mutter.data.support

import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.MusicCue
import com.efreedom.mutter.domain.model.MusicSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 계약 §7 — 연속 텍스트 병합, 사진 위치 보존, 큐 위치, 구 형식 호환. */
class LetterContentCodecTest {

    // supabase-kt 기본 직렬화기와 같은 설정(ignoreUnknownKeys=true, encodeDefaults=false).
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(ParagraphDto.serializer())

    private val cue = MusicCue(source = MusicSource.SOUNDCLOUD, ref = "https://soundcloud.com/a/b", title = "곡")
    private fun photo(id: String, path: String) = LetterPhoto(id = id, path = path, width = 1536, height = 2048)

    private fun roundTrip(blocks: List<LetterBlock>, cue: MusicCue?): List<ParagraphDto> {
        val encoded = json.encodeToString(listSerializer, LetterContentCodec.paragraphs(blocks, cue))
        return json.decodeFromString(listSerializer, encoded)
    }

    @Test
    fun `연속 텍스트 단락은 하나의 텍스트 블록으로 합친다`() {
        val paragraphs = listOf(
            ParagraphDto(id = "1", order = 0, text = "첫 문단"),
            ParagraphDto(id = "2", order = 1, text = "둘째 문단"),
            ParagraphDto(id = "3", order = 2, text = "", photo = ParagraphPhotoDto("u/l/p.jpg", 10, 20)),
            ParagraphDto(id = "4", order = 3, text = "셋째"),
            ParagraphDto(id = "5", order = 4, text = "넷째"),
        )
        val blocks = LetterContentCodec.blocks(paragraphs)
        assertEquals(3, blocks.size)
        assertEquals(LetterBlock.Text("첫 문단\n\n둘째 문단"), blocks[0])
        assertEquals(LetterBlock.Text("셋째\n\n넷째"), blocks[2])
    }

    @Test
    fun `저장 시 텍스트 블록은 빈 줄 기준으로 단락을 나눈다`() {
        val paragraphs = LetterContentCodec.paragraphs(listOf(LetterBlock.Text("가\n나\n\n다")), null)
        assertEquals(listOf("가\n나", "다"), paragraphs.map { it.text })
        assertEquals(listOf(0, 1), paragraphs.map { it.order })
    }

    @Test
    fun `사진 위치와 크기·id가 왕복 후에도 보존된다`() {
        val blocks = listOf(
            LetterBlock.Text("시작\n\n이어서"),
            LetterBlock.Photo(photo("p1", "owner/letter/a.jpg")),
            LetterBlock.Text("중간"),
            LetterBlock.Photo(photo("p2", "owner/letter/b.jpg")),
            LetterBlock.Photo(photo("p3", "owner/letter/c.jpg")),
            LetterBlock.Text("끝"),
        )
        val decoded = LetterContentCodec.blocks(roundTrip(blocks, cue))
        assertEquals(blocks, decoded)
    }

    @Test
    fun `사진 단락은 text 빈 문자열과 photo를 갖고 cue는 없다`() {
        val paragraphs = LetterContentCodec.paragraphs(
            listOf(LetterBlock.Photo(photo("p1", "o/l/a.jpg")), LetterBlock.Text("본문")),
            cue,
        )
        val encoded = json.parseToJsonElement(json.encodeToString(listSerializer, paragraphs)).jsonArray
        val photoParagraph = encoded.first { "photo" in it.jsonObject }.jsonObject
        assertEquals("\"\"", photoParagraph["text"].toString())
        assertFalse("cue" in photoParagraph)
        assertEquals("p1", photoParagraph["id"].toString().trim('"'))
        // 사진 없는 텍스트 단락에는 photo 키가 아예 없다(null을 쓰지 않음).
        assertTrue(encoded.filter { "photo" !in it.jsonObject }.isNotEmpty())
    }

    @Test
    fun `큐는 첫 텍스트 단락에 붙는다 - 사진이 맨 앞이어도`() {
        val paragraphs = LetterContentCodec.paragraphs(
            listOf(
                LetterBlock.Photo(photo("p1", "o/l/a.jpg")),
                LetterBlock.Text("첫\n\n둘"),
            ),
            cue,
        )
        assertNull(paragraphs[0].cue)
        assertNotNull(paragraphs[0].photo)
        assertNotNull(paragraphs[1].cue)
        assertNull(paragraphs[2].cue)
        assertEquals(cue, LetterContentCodec.cue(paragraphs))
    }

    @Test
    fun `텍스트가 없어도 빈 텍스트 단락을 앞에 둬 큐를 잃지 않는다`() {
        val paragraphs = LetterContentCodec.paragraphs(listOf(LetterBlock.Photo(photo("p1", "o/l/a.jpg"))), cue)
        assertEquals(2, paragraphs.size)
        assertEquals("", paragraphs[0].text)
        assertNotNull(paragraphs[0].cue)
        assertNotNull(paragraphs[1].photo)
    }

    @Test
    fun `구 형식 payload는 photo 없이도 디코드되고 body가 기존과 같다`() {
        val legacy = """
            [
              {"id":"b","order":1,"text":"둘째"},
              {"id":"a","order":0,"text":"첫째","cue":{"sourceType":"hosted","ref":"x","startMs":0},"futureField":true}
            ]
        """.trimIndent()
        val paragraphs = json.decodeFromString(listSerializer, legacy)
        val blocks = LetterContentCodec.blocks(paragraphs)
        assertEquals(listOf(LetterBlock.Text("첫째\n\n둘째")), blocks)
        assertEquals("첫째\n\n둘째", LetterContentCodec.body(paragraphs))
        assertEquals(MusicSource.HOSTED, LetterContentCodec.cue(paragraphs)?.source)
    }

    @Test
    fun `body 기반 구 호출부 인코딩은 예전과 같은 모양이다`() {
        val paragraphs = LetterContentCodec.paragraphs("가\n\n나", cue)
        assertEquals(listOf("가", "나"), paragraphs.map { it.text })
        assertNotNull(paragraphs[0].cue)
        assertNull(paragraphs[1].cue)
        assertTrue(paragraphs.all { it.photo == null })
    }

    @Test
    fun `사진만 있는 편지의 빈 큐 앵커 단락은 블록이 되지 않는다 - iOS와 동일`() {
        val decoded = roundTrip(listOf(LetterBlock.Photo(photo("p1", "o/l/a.jpg"))), cue)
        assertEquals(listOf(LetterBlock.Photo(photo("p1", "o/l/a.jpg"))), LetterContentCodec.blocks(decoded))
        assertNotNull(LetterContentCodec.cue(decoded))
    }

    @Test
    fun `끝의 빈 줄은 빈 단락으로 저장되지 않는다`() {
        val paragraphs = LetterContentCodec.paragraphs(listOf(LetterBlock.Text("안녕\n\n")), null)
        assertEquals(listOf("안녕"), paragraphs.map { it.text })
    }

    @Test
    fun `text 키가 빠진 단락도 디코드되고 인코딩 시 text는 항상 쓴다`() {
        val raw = """[{"id":"p","order":0,"photo":{"path":"o/l/a.jpg","width":10,"height":20}}]"""
        val paragraphs = json.decodeFromString(listSerializer, raw)
        assertEquals("", paragraphs[0].text)
        val encoded = json.encodeToString(listSerializer, paragraphs)
        assertTrue(encoded.contains("\"text\":\"\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `업로드 전 사진은 인코딩을 거부한다`() {
        LetterContentCodec.paragraphs(
            listOf(LetterBlock.Photo(LetterPhoto(id = "p", path = null, width = 1, height = 1))),
            null,
        )
    }
}
