package com.efreedom.mutter.domain

import java.net.URI

/**
 * 인앱 SoundCloud 탐색 중 "지금 보고 있는 페이지가 트랙 한 곡인가"를 판정한다.
 * iOS `Domain/Entity/SoundCloudTrackPage.swift` 대응.
 *
 * 페이지 DOM을 읽지 않고(약관상 스크래핑 금지) URL 경로 모양만으로 판단한다.
 * 트랙 페이지는 `/{user}/{track}` 2단 경로이며, 같은 2단이라도 프로필 하위 탭(`/{user}/sets` 등)은 제외한다.
 */
object SoundCloudTrackPage {
    /** 트랙 페이지면 쿼리·프래그먼트·모바일 호스트를 걷어낸 공개 트랙 URL을, 아니면 null. */
    fun trackUrl(url: String?): String? {
        if (url == null) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in allowedHosts) return null

        // rawPath — 퍼센트 인코딩을 유지해야 결과를 그대로 유효한 URL로 되돌릴 수 있다.
        val segments = (uri.rawPath ?: "").split("/").filter { it.isNotEmpty() }
        if (segments.size != 2) return null

        val (user, slug) = segments
        if (user.lowercase() in reservedRootPaths || slug.lowercase() in profileTabs) return null

        return "https://soundcloud.com/$user/$slug"
    }

    private val allowedHosts = setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com")

    /** 사용자명이 아닌 SoundCloud 자체 페이지(1단 경로). 2단으로 들어가도 트랙이 아니다. */
    private val reservedRootPaths = setOf(
        "discover", "search", "stream", "feed", "you", "upload", "charts", "pages", "settings",
        "notifications", "messages", "terms-of-use", "legal", "pro", "mobile", "imprint", "people",
        "jobs", "popular", "tags", "stations", "signin", "signup", "logout", "connect", "artists",
        "for-artists", "creators", "premium", "go", "next", "playlists", "communities", "home",
    )

    /** 프로필 하위 탭(`/{user}/{tab}`). 트랙 슬러그와 경로 모양이 같아 이름으로 거른다. */
    private val profileTabs = setOf(
        "sets", "likes", "tracks", "albums", "reposts", "followers", "following", "popular-tracks",
        "comments", "spotlight", "recommended", "playlists", "toptracks", "station",
    )
}
