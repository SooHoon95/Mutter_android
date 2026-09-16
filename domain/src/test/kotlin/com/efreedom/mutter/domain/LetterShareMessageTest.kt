package com.efreedom.mutter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LetterShareMessageTest {
    private val url = "https://example.com/l/abc123"
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val zone = ZoneId.of("Asia/Seoul")
    private val locale = Locale.KOREA

    @Test
    fun plain_bodyThenBlankLineThenUrl() {
        val text = LetterShareMessage.text(url, hasPassword = false, revealAt = null, now = now, zone = zone, locale = locale)
        assertTrue(text.startsWith(LetterShareMessage.BODY))
        assertTrue(text.endsWith("\n\n$url"))
        assertFalse(text.contains(LetterShareMessage.PASSWORD_HINT))
    }

    @Test
    fun password_addsHintBeforeUrl() {
        val text = LetterShareMessage.text(url, hasPassword = true, revealAt = null, now = now, zone = zone, locale = locale)
        assertTrue(text.contains("\n${LetterShareMessage.PASSWORD_HINT}\n\n$url"))
    }

    @Test
    fun futureReveal_addsRevealLine() {
        val revealAt = now.plusSeconds(3600)
        val expected = LetterShareMessage.revealLine(LetterShareMessage.formatRevealAt(revealAt, zone, locale))
        val text = LetterShareMessage.text(url, hasPassword = false, revealAt = revealAt, now = now, zone = zone, locale = locale)
        assertTrue(text.contains("\n$expected\n\n$url"))
    }

    @Test
    fun pastReveal_isIgnored() {
        val plain = LetterShareMessage.text(url, hasPassword = false, revealAt = null, now = now, zone = zone, locale = locale)
        val past = LetterShareMessage.text(url, hasPassword = false, revealAt = now.minusSeconds(60), now = now, zone = zone, locale = locale)
        assertEquals(plain, past)
    }

    @Test
    fun passwordAndReveal_keepOrder() {
        val revealAt = now.plusSeconds(86_400)
        val reveal = LetterShareMessage.revealLine(LetterShareMessage.formatRevealAt(revealAt, zone, locale))
        val text = LetterShareMessage.text(url, hasPassword = true, revealAt = revealAt, now = now, zone = zone, locale = locale)
        assertEquals(listOf(LetterShareMessage.BODY, LetterShareMessage.PASSWORD_HINT, reveal, "", url).joinToString("\n"), text)
    }
}
