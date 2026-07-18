package com.efreedom.mutter.data.support

import java.security.SecureRandom
import java.util.Base64

/** 열람/초대 링크용 토큰 생성(iOS TokenGenerator 이식). base64url, 패딩 제거. */
object TokenGenerator {
    fun make(byteCount: Int = 24): String {
        val bytes = ByteArray(byteCount.coerceAtLeast(16))
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
