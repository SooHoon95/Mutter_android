package com.efreedom.mutter.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 사용자가 로그인 창을 닫음 — 에러 표시 없이 조용히 무시하기 위한 신호. */
class OauthCancelledException : Exception()

/**
 * 소셜 SDK 플로우 — provider SDK로 id_token을 획득한다(iOS SignInProvider 대응).
 * 획득한 id_token은 [AuthViewModel]이 `signInSocial`로 Edge Function에 넘겨 세션으로 교환한다.
 * Activity Context가 필요하므로 호출 시점에 전달받는다(보관하지 않음 — 누수 방지).
 */
object OauthClient {

    /** Google — Credential Manager로 Google id_token 획득(audience=Web 클라이언트 ID). */
    suspend fun googleIdToken(context: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleIdTokenCredential.createFrom(credential.data).idToken
            }
            throw IllegalStateException("예상치 못한 자격 증명 형식이에요.")
        } catch (e: GetCredentialCancellationException) {
            throw OauthCancelledException()
        }
    }

    /** Kakao — 카카오톡/카카오계정 로그인 → OIDC id_token(콘솔 OpenID Connect 활성 필요). */
    suspend fun kakaoIdToken(context: Context): String {
        fun Throwable.isCancel(): Boolean =
            this is ClientError && this.reason == ClientErrorCause.Cancelled

        val token = suspendCancellableCoroutine { cont ->
            val handler: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                when {
                    error?.isCancel() == true -> cont.resumeWithException(OauthCancelledException())
                    error != null -> cont.resumeWithException(error)
                    token != null -> cont.resume(token)
                    else -> cont.resumeWithException(IllegalStateException("카카오 로그인에 실패했어요."))
                }
            }
            if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                    // 카카오톡 로그인 중 취소가 아닌 오류면 카카오계정 로그인으로 폴백.
                    if (error != null && !error.isCancel()) {
                        UserApiClient.instance.loginWithKakaoAccount(context, callback = handler)
                    } else {
                        handler(token, error)
                    }
                }
            } else {
                UserApiClient.instance.loginWithKakaoAccount(context, callback = handler)
            }
        }
        if (token.idToken != null && (token.idToken?.isNotEmpty() == true)) {
            return token.idToken!!
        }
        // 취소는 조용히, 그 외는 실패 메시지.
        throw IllegalStateException("카카오 id_token을 받지 못했어요(콘솔 OpenID Connect 확인).")
    }
}
