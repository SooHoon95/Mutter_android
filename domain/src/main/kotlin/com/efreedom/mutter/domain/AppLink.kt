package com.efreedom.mutter.domain

/**
 * 앱이 만들어 내보내는 웹 링크(편지 열람·연결 초대)의 기반 도메인 — 단일 진실 원천.
 * iOS `AppFoundation/Define/AppLink.swift` 대응.
 *
 * 현재 배포 도메인 = Vercel(live). `mutter.app`은 미연결(HTTP 000)이라 링크가 죽어 있었음(MU-6).
 * 이전에는 Compose·Delivery·Connections 세 모듈이 각자 상수를 두었고, 그중 Compose만
 * 죽은 `mutter.app`을 써서 전달 링크가 열리지 않던 결함이 있었다. 여기로 통합해 제거한다.
 */
object AppLink {
    const val BASE_URL: String = "https://letter-app-nine-kohl.vercel.app"

    /** 편지 열람 링크: `<base>/l/<token>` */
    fun letter(token: String): String = "$BASE_URL/l/$token"

    /** 연결 초대 링크(앱 발신): `<base>/connect/<token>?from=app` */
    fun connectInvite(token: String): String = "$BASE_URL/connect/$token?from=app"
}
