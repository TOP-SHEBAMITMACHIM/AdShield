package com.adshield.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelCandidatesTest {

    private fun entry(domain: String, blocked: Boolean = false, timeMs: Long = 0L) =
        EngineState.LogEntry(timeMs, domain, blocked)

    @Test
    fun keepsTheOrderOfTheNewestLookupFirst() {
        val offered = PanelCandidates.offered(
            listOf(entry("newest.com"), entry("middle.com"), entry("oldest.com")),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf("newest.com", "middle.com", "oldest.com"), offered)
    }

    @Test
    fun skipsLookupsThatWereBlocked() {
        val offered = PanelCandidates.offered(
            listOf(entry("already-blocked.com", blocked = true), entry("allowed.com")),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf("allowed.com"), offered)
    }

    @Test
    fun skipsWhitelistedHostsAndTheirSubdomains() {
        val offered = PanelCandidates.offered(
            listOf(entry("shop.example.com"), entry("example.com"), entry("tracker.io")),
            setOf("example.com"),
            emptySet()
        )

        assertEquals(listOf("tracker.io"), offered)
    }

    @Test
    fun skipsHostsThatAreAlreadyBlacklisted() {
        val offered = PanelCandidates.offered(
            listOf(entry("ads.tracker.io"), entry("other.io")),
            emptySet(),
            setOf("tracker.io")
        )

        assertEquals(listOf("other.io"), offered)
    }

    @Test
    fun skipsNamesThatCannotBeBlocked() {
        val offered = PanelCandidates.offered(
            listOf(
                entry("localhost"),
                entry("192.168.1.1"),
                entry("printer.local"),
                entry(""),
                entry("real-domain.com")
            ),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf("real-domain.com"), offered)
    }

    @Test
    fun removesDuplicatesAndKeepsTheFirstOccurrence() {
        val offered = PanelCandidates.offered(
            listOf(entry("ads.io"), entry("other.io"), entry("ads.io")),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf("ads.io", "other.io"), offered)
    }

    @Test
    fun respectsTheLimit() {
        val recent = (1..30).map { entry("host$it.com") }

        val offered = PanelCandidates.offered(recent, emptySet(), emptySet())

        assertEquals(PanelCandidates.MAX_OFFERED, offered.size)
        assertEquals("host1.com", offered.first())
    }

    @Test
    fun acceptsOnlyHostShapedNames() {
        assertTrue(PanelCandidates.isHost("ads.tracker.example.com"))
        assertTrue(PanelCandidates.isHost("xn--80ak6aa92e.com"))
        assertFalse(PanelCandidates.isHost("com"))
        assertFalse(PanelCandidates.isHost("10.0.0.1"))
        assertFalse(PanelCandidates.isHost("-broken.com"))
        assertFalse(PanelCandidates.isHost("spaces in name.com"))
    }
}
