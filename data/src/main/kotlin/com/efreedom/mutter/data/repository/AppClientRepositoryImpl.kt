package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.ReportAppClientParams
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.repository.AppClientRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

/** report_app_client RPC. 프로필(profiles.app_*)에 최근 값 하나를 남긴다(대상은 서버가 auth.uid()로). */
class AppClientRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AppClientRepository {

    private val client get() = provider.client

    override suspend fun report(platform: String, appVersion: String, build: String) = wrap {
        client.postgrest.rpc(
            "report_app_client",
            ReportAppClientParams(platform = platform, appVersion = appVersion, build = build),
        )
        Unit
    }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
