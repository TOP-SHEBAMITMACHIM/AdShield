package com.adshield.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedToastPolicyTest {

    private fun decide(
        enabled: Boolean = true,
        appVisible: Boolean = false,
        screenOn: Boolean = true,
        nowMs: Long = 10_000L,
        lastShownMs: Long = 0L,
        gapMs: Long = BlockedToastPolicy.MIN_GAP_MS
    ) = BlockedToastPolicy.shouldShow(
        enabled = enabled,
        appVisible = appVisible,
        screenOn = screenOn,
        nowMs = nowMs,
        lastShownMs = lastShownMs,
        gapMs = gapMs
    )

    @Test
    fun showsOnceTheGapHasPassed() {
        assertTrue(decide())
    }

    @Test
    fun staysQuietWhileSwitchedOff() {
        assertFalse(decide(enabled = false))
    }

    @Test
    fun staysQuietWhileOurOwnScreenIsOpen() {
        assertFalse(decide(appVisible = true))
    }

    @Test
    fun staysQuietWhileTheScreenIsOff() {
        assertFalse(decide(screenOn = false))
    }

    @Test
    fun holdsBackMessagesInsideTheGap() {
        assertFalse(decide(nowMs = 4_000L, lastShownMs = 1L))
        assertTrue(decide(nowMs = 4_001L, lastShownMs = 1L))
    }

    @Test
    fun honoursACustomGap() {
        assertFalse(decide(nowMs = 9_000L, lastShownMs = 0L, gapMs = 10_000L))
        assertTrue(decide(nowMs = 10_000L, lastShownMs = 0L, gapMs = 10_000L))
    }

    @Test
    fun longDomainLosesSubdomainLabelsFirst() {
        assertEquals(
            "tracker.example.com",
            BlockedToastPolicy.shorten("ads.tracker.example.com", maxLength = 19)
        )
    }

    @Test
    fun shortDomainIsLeftAlone() {
        assertEquals("ads.example.com", BlockedToastPolicy.shorten("  ADS.Example.COM.  "))
    }

    @Test
    fun unsplittableDomainKeepsItsTail() {
        val shortened = BlockedToastPolicy.shorten("a".repeat(60) + ".com", maxLength = 12)

        assertTrue(shortened.length <= 12)
        assertTrue(shortened.endsWith(".com"))
        assertTrue(shortened.startsWith("…"))
    }

    @Test
    fun emptyDomainShortensToNothing() {
        assertEquals("", BlockedToastPolicy.shorten("   "))
        assertEquals("", BlockedToastPolicy.shorten("ads.example.com", maxLength = 1))
    }
}
