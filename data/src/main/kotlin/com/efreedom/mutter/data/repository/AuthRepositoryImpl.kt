package com.efreedom.mutter.data.repository

import com.efreedom.mutter.core.network.SupabaseProvider
import com.efreedom.mutter.data.support.SupabaseErrorMapper
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.Session
import com.efreedom.mutter.domain.model.SocialProvider
import com.efreedom.mutter.domain.repository.AuthRepository
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import javax.inject.Inject

/** iOS AuthRepository 이식. 이메일 OTP·비밀번호·Apple(IDToken)은 supabase-kt로,
 *  소셜(google/kakao)은 Edge Function 교환이 필요해 후속 배선(현재 명시적 미구현). */
class AuthRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : AuthRepository {

    private val auth get() = provider.client.auth

    override suspend fun requestCode(email: String) = wrap {
        auth.signInWith(OTP) {
            this.email = email
            createUser = true
        }
    }

    override suspend fun verifyCode(email: String, code: String): Session = wrap {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    override suspend fun signIn(email: String, password: String): Session = wrap {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    override suspend fun signInApple(idToken: String, nonce: String): Session = wrap {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Apple
            this.nonce = nonce
        }
        currentDomainSession() ?: throw MutterError.Unauthorized
    }

    override suspend fun signInSocial(provider: SocialProvider, token: String): Session {
        // TODO: iOS와 동일하게 Edge Function(google-login/kakao-login)로 토큰 교환 후 setSession.
        throw MutterError.Server("소셜 로그인은 준비 중이에요.")
    }

    override suspend fun signOut() = wrap { auth.signOut() }

    override suspend fun currentSession(): Session? =
        runCatching { currentDomainSession() }.getOrNull()

    private fun currentDomainSession(): Session? =
        auth.currentUserOrNull()?.let { Session(userId = it.id, email = it.email) }

    private inline fun <T> wrap(block: () -> T): T =
        try { block() } catch (e: Throwable) { throw SupabaseErrorMapper.map(e) }
}
