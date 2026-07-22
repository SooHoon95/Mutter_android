package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.model.AppConfigRow
import com.efreedom.mutter.domain.repository.AppUpdateRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject

/**
 * iOS AppUpdateRepository 대응. iOS는 iTunes 조회, Android는 Supabase `app_config` 원격 설정을 쓴다.
 * (마이그레이션 0031_app_config — 공개 읽기 anon 허용, 쓰기는 service role 전용.)
 * 플랫폼 행의 min_version을 읽는다. 행 없음·네트워크 오류·미배포는 모두 null로 삼켜 fail-open.
 */
class AppUpdateRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AppUpdateRepository {

    private val client get() = provider.client

    override suspend fun minSupportedVersion(platform: String): String? =
        runCatching {
            client.postgrest.from("app_config")
                .select(Columns.list("platform", "min_version")) {
                    filter { eq("platform", platform) }
                    limit(1)
                }
                .decodeList<AppConfigRow>()
                .firstOrNull()
                ?.minVersion
        }.getOrNull()
}
