package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.DeletedLetterRow
import com.efreedom.mutter.data.model.LetterIdParam
import com.efreedom.mutter.data.model.LetterInsertDto
import com.efreedom.mutter.data.model.LetterRow
import com.efreedom.mutter.data.model.LetterUpdateDto
import com.efreedom.mutter.data.model.LetterWithStatusRow
import com.efreedom.mutter.data.support.LetterContentCodec
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.DeletedLetter
import com.efreedom.mutter.domain.model.Letter
import com.efreedom.mutter.domain.model.LetterDraft
import com.efreedom.mutter.domain.model.LetterWithStatus
import com.efreedom.mutter.domain.repository.LetterRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import javax.inject.Inject

/**
 * iOS LetterRepository 이식. letters 테이블 CRUD + get_my_letters_with_status RPC + 삭제함 RPC.
 * `letters`는 삭제되지 않은 행만 보이는 뷰라(실테이블은 letters_all) 삭제한 편지는 모든 질의에서 자동으로 빠진다.
 */
class LetterRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : LetterRepository {

    private val client get() = provider.client

    override suspend fun create(draft: LetterDraft): Letter = wrap {
        client.postgrest.from("letters")
            .insert(
                LetterInsertDto(
                    ownerId = uid(),
                    title = draft.title,
                    paragraphs = LetterContentCodec.paragraphs(draft.blocks, draft.cue),
                    templateId = draft.templateId,
                ),
            ) { select() }
            .decodeSingle<LetterRow>()
            .toDomain()
    }

    override suspend fun update(id: String, draft: LetterDraft) = wrap {
        client.postgrest.from("letters")
            .update(
                LetterUpdateDto(
                    title = draft.title,
                    paragraphs = LetterContentCodec.paragraphs(draft.blocks, draft.cue),
                    templateId = draft.templateId,
                    updatedAt = Instant.now().toString(),
                ),
            ) { filter { eq("id", id) } }
        Unit
    }

    override suspend fun letter(id: String): Letter? = wrap {
        client.postgrest.from("letters")
            .select {
                filter { eq("id", id) }
                limit(1)
            }
            .decodeList<LetterRow>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun myLetters(): List<Letter> = wrap {
        client.postgrest.from("letters")
            .select {
                filter { eq("owner_id", uid()) }
                order("updated_at", Order.DESCENDING)
            }
            .decodeList<LetterRow>()
            .map { it.toDomain() }
    }

    override suspend fun myLettersWithStatus(): List<LetterWithStatus> = wrap {
        client.postgrest.rpc("get_my_letters_with_status")
            .decodeList<LetterWithStatusRow>()
            .map { it.toDomain() }
    }

    // 뷰의 INSTEAD OF DELETE 트리거가 deleted_at만 찍는다(삭제함 30일 보관) — 클라이언트 요청은 그대로다.
    override suspend fun delete(id: String) = wrap {
        client.postgrest.from("letters")
            .delete { filter { eq("id", id) } }
        Unit
    }

    override suspend fun deletedLetters(): List<DeletedLetter> = wrap {
        client.postgrest.rpc("get_my_deleted_letters")
            .decodeList<DeletedLetterRow>()
            .map { it.toDomain() }
    }

    override suspend fun restore(id: String) = wrap {
        client.postgrest.rpc("restore_letter", LetterIdParam(letterId = id))
        Unit
    }

    override suspend fun purge(id: String) = wrap {
        client.postgrest.rpc("purge_letter", LetterIdParam(letterId = id))
        Unit
    }

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: throw MutterError.Unauthorized

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
