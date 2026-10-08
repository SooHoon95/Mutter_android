package com.efreedom.mutter.domain

import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterBlock
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterPhoto
import com.efreedom.mutter.domain.model.LetterWithStatus
import com.efreedom.mutter.domain.repository.LetterPhotoRepository
import com.efreedom.mutter.domain.repository.LetterRepository
import com.efreedom.mutter.domain.usecase.LetterUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 계약 §5 저장 흐름 — 생성 → 업로드 → update → 지워진 경로 삭제. */
class LetterSaveWithPhotosTest {

    private class FakeLetters : LetterRepository {
        val calls = mutableListOf<String>()
        var created: LetterDraft? = null
        var updated: LetterDraft? = null
        override suspend fun create(draft: LetterDraft): Letter {
            calls += "create"; created = draft
            return Letter("L1", draft.title, draft.blocks, draft.templateId, draft.cue)
        }
        override suspend fun update(id: String, draft: LetterDraft) { calls += "update:$id"; updated = draft }
        override suspend fun letter(id: String): Letter? = null
        override suspend fun myLetters(): List<Letter> = emptyList()
        override suspend fun myLettersWithStatus(): List<LetterWithStatus> = emptyList()
        override suspend fun delete(id: String) { calls += "delete:$id" }
    }

    private class FakePhotos(private val failUpload: Boolean = false) : LetterPhotoRepository {
        val calls = mutableListOf<String>()
        var deleted: Collection<String> = emptyList()
        override suspend fun upload(letterId: String, jpeg: ByteArray): String {
            if (failUpload) error("upload failed")
            calls += "upload"; return "u/$letterId/${jpeg.size}.jpg"
        }
        override suspend fun delete(paths: Collection<String>) { deleted = paths }
        override suspend fun deleteAll(letterId: String) { calls += "deleteAll:$letterId" }
        override suspend fun signedUrls(token: String, password: String?) = emptyMap<String, String>()
        override suspend fun signedUrls(letterId: String) = emptyMap<String, String>()
    }

    private fun pending(id: String, size: Int) = LetterBlock.Photo(LetterPhoto(id, null, 10, 10, ByteArray(size)))

    @Test
    fun `새 편지는 생성 후 업로드하고 경로를 넣어 update한다`() = runBlocking {
        val letters = FakeLetters(); val photos = FakePhotos()
        var createdId: String? = null
        val draft = LetterDraft("t", listOf(LetterBlock.Text("a"), pending("p1", 3), LetterBlock.Text("")), "classic-serif")

        val saved = LetterUseCase(letters, photos).saveWithPhotos(null, draft, emptySet()) { createdId = it }

        assertEquals("L1", createdId)
        assertEquals(listOf("create", "update:L1"), letters.calls)
        // 생성 시에는 경로 없는 사진을 빼고 저장한다.
        assertTrue(letters.created!!.blocks.none { it is LetterBlock.Photo })
        val photo = (saved.blocks[1] as LetterBlock.Photo).photo
        assertEquals("u/L1/3.jpg", photo.path)
        assertEquals(null, photo.pendingJpeg)
        assertEquals(saved.blocks, letters.updated!!.blocks)
    }

    @Test
    fun `이전에 있던 경로가 사라지면 삭제한다`() = runBlocking {
        val letters = FakeLetters(); val photos = FakePhotos()
        val kept = LetterBlock.Photo(LetterPhoto("p1", "u/L1/keep.jpg", 1, 1))
        val draft = LetterDraft("t", listOf(LetterBlock.Text("a"), kept), "classic-serif")

        LetterUseCase(letters, photos).saveWithPhotos("L1", draft, setOf("u/L1/keep.jpg", "u/L1/gone.jpg"))

        assertEquals(listOf("update:L1"), letters.calls)
        assertEquals(setOf("u/L1/gone.jpg"), photos.deleted.toSet())
    }

    @Test
    fun `업로드가 실패하면 저장 실패로 던지고 update하지 않는다`() {
        val letters = FakeLetters(); val photos = FakePhotos(failUpload = true)
        val draft = LetterDraft("t", listOf(pending("p1", 1)), "classic-serif")
        val result = runCatching {
            runBlocking { LetterUseCase(letters, photos).saveWithPhotos("L1", draft, emptySet()) }
        }
        assertTrue(result.isFailure)
        assertTrue(letters.calls.isEmpty())
    }
}
