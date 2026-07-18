package com.efreedom.mutter.data.service

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.domain.repository.SessionProvider
import io.github.jan.supabase.auth.auth
import javax.inject.Inject

/** 동기 캐시 세션 조회(iOS SessionProvider 이식). */
class SessionProviderImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : SessionProvider {
    override val currentUserId: String?
        get() = provider.client.auth.currentUserOrNull()?.id
}
