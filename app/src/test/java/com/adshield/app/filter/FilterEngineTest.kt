package com.adshield.app.filter

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    @After
    fun reset() {
        FilterEngine.update(emptySet(), emptySet(), emptySet())
    }

    @Test
    fun blocksExactHostAndSubdomains() {
        FilterEngine.update(setOf("doubleclick.net"), emptySet(), emptySet())

        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("doubleclick.net"))
        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("ad.doubleclick.net"))
        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("a.b.c.doubleclick.net"))
    }

    @Test
    fun doesNotBlockLookalikeDomains() {
        FilterEngine.update(setOf("doubleclick.net"), emptySet(), emptySet())

        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("notdoubleclick.net"))
        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("click.net"))
        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("doubleclick.net.example.com"))
    }

    @Test
    fun whitelistBeatsEverythingElse() {
        FilterEngine.update(setOf("example.com"), setOf("ads.example.com"), setOf("example.com"))

        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("ads.example.com"))
        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("other.example.com"))
        assertTrue(FilterEngine.isWhitelisted("ads.example.com"))
    }

    @Test
    fun blacklistAddsRulesOnTopOfLists() {
        FilterEngine.update(setOf("ads.example"), emptySet(), setOf("tracker.io"))

        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("x.tracker.io"))
        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("ads.example"))
        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("tracker.io.example.com"))
        assertEquals(2, FilterEngine.ruleCount())
    }

    @Test
    fun normalisesCaseTrailingDotAndWhitespace() {
        FilterEngine.update(setOf("ads.example.com"), emptySet(), emptySet())

        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("  ADS.Example.COM.  "))
    }

    @Test
    fun emptyRulesAllowEverything() {
        FilterEngine.update(emptySet(), emptySet(), emptySet())

        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("ads.doubleclick.net"))
        assertFalse(FilterEngine.isBlocked("anything.example"))
        assertEquals(0, FilterEngine.ruleCount())
    }

    @Test
    fun secondUpdateReplacesPreviousRules() {
        FilterEngine.update(setOf("first.com"), emptySet(), emptySet())
        FilterEngine.update(setOf("second.com"), emptySet(), emptySet())

        assertEquals(FilterEngine.Action.ALLOW, FilterEngine.decide("first.com"))
        assertEquals(FilterEngine.Action.BLOCK, FilterEngine.decide("second.com"))
    }
}
