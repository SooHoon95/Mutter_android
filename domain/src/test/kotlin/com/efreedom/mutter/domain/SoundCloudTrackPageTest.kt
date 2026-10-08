package com.efreedom.mutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundCloudTrackPageTest {
    private fun track(raw: String?): String? = SoundCloudTrackPage.trackUrl(raw)

    @Test
    fun trackPage_returnsCanonicalUrl() {
        assertEquals("https://soundcloud.com/artist/some-song", track("https://soundcloud.com/artist/some-song"))
    }

    @Test
    fun mobileHostAndQuery_areNormalized() {
        assertEquals(
            "https://soundcloud.com/artist/some-song",
            track("https://m.soundcloud.com/artist/some-song?in=artist/sets/x&utm_source=a#t=1"),
        )
    }

    @Test
    fun trailingSlash_isAccepted() {
        assertEquals("https://soundcloud.com/artist/some-song", track("https://soundcloud.com/artist/some-song/"))
    }

    @Test
    fun profileAndTabs_areRejected() {
        assertNull(track("https://soundcloud.com/artist"))
        assertNull(track("https://soundcloud.com/artist/sets"))
        assertNull(track("https://soundcloud.com/artist/likes"))
        assertNull(track("https://soundcloud.com/artist/popular-tracks"))
    }

    @Test
    fun playlistAndPrivateShare_areRejected() {
        assertNull(track("https://soundcloud.com/artist/sets/my-playlist"))
        assertNull(track("https://soundcloud.com/artist/some-song/s-AbCdE"))
    }

    @Test
    fun reservedRootPages_areRejected() {
        assertNull(track("https://soundcloud.com/search/sounds?q=rain"))
        assertNull(track("https://soundcloud.com/discover/sets"))
        assertNull(track("https://m.soundcloud.com/you/likes"))
    }

    @Test
    fun otherHostsAndSchemes_areRejected() {
        assertNull(track("https://evil.com/artist/some-song"))
        assertNull(track("http://soundcloud.com/artist/some-song"))
        assertNull(track("https://on.soundcloud.com/abc"))
        assertNull(track(null))
    }
}
