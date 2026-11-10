package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.DeletedInboxRow
import com.efreedom.mutter.data.model.InboxRow
import com.efreedom.mutter.data.model.LetterIdsParam
import com.efreedom.mutter.data.model.TokenParam
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.model.DeletedInboxItem
import com.efreedom.mutter.domain.model.InboxItem
import com.efreedom.mutter.domain.repository.InboxRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/**
 * iOS InboxRepository 이식. save_to_inbox / get_my_inbox RPC + 받은 편지 삭제함 RPC.
 * `inbox`는 삭제되지 않은 행만 보이는 뷰라(실테이블은 inbox_all) 삭제한 편지는 받은 편지함 질의에서 자동으로 빠진다.
 */
class InboxRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : InboxRepository {

    private val client get() = provider.client

    override suspend fun save(token: String) = wrap {
        client.postgrest.rpc("save_to_inbox", TokenParam(token = token))
        Unit
    }

    override suspend fun myInbox(): List<InboxItem> = wrap {
        client.postgrest.rpc("get_my_inbox")
            .decodeList<InboxRow>()
            .map { it.toDomain() }
    }

    // 삭제는 RPC 없이 inbox 뷰에 직접 delete — 뷰 트리거가 삭제함 보관으로 바꾸고, RLS(inbox_self_rw)가 본인 행으로 한정한다.
    override suspend fun remove(letterIds: List<String>) = wrap {
        if (letterIds.isEmpty()) return@wrap
        client.postgrest.from("inbox")
            .delete {
                filter {
                    if (letterIds.size == 1) eq("letter_id", letterIds.first()) else isIn("letter_id", letterIds)
                }
            }
        Unit
    }

    override suspend fun deletedItems(): List<DeletedInboxItem> = wrap {
        client.postgrest.rpc("get_my_deleted_inbox")
            .decodeList<DeletedInboxRow>()
            .map { it.toDomain() }
    }

    override suspend fun restore(letterIds: List<String>): Int = wrap {
        client.postgrest.rpc("restore_inbox_items", LetterIdsParam(letterIds = letterIds)).decodeAs<Int>()
    }

    override suspend fun purge(letterIds: List<String>): Int = wrap {
        client.postgrest.rpc("purge_inbox_items", LetterIdsParam(letterIds = letterIds)).decodeAs<Int>()
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
