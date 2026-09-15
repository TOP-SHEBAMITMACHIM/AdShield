package com.adshield.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    @Test
    fun parsesHostsFormatLists() {
        val sample = """
            # comment line
            ! adblock style comment
            127.0.0.1 localhost
            0.0.0.0 ads.example.com
            0.0.0.0 tracking.net dot.com
            127.0.0.1 ads.example.com
            bare-domain.example
            ||adblock-style.example^
            https://url-style.example/path?query=1
            0.0.0.0 1.2.3.4
        """.trimIndent()

        val domains = BlocklistRepository.parseText(sample)

        assertTrue(domains.contains("ads.example.com"))
        assertTrue(domains.contains("tracking.net"))
        assertTrue(domains.contains("dot.com"))
        assertTrue(domains.contains("bare-domain.example"))
        assertTrue(domains.contains("adblock-style.example"))
        assertTrue(domains.contains("url-style.example"))
        assertEquals("comment headers must not become rules", 6, domains.size)
    }

    @Test
    fun skipsLocalhostAndUnusableEntries() {
        val sample = """
            localhost
            0.0.0.0 localhost.localdomain
            0.0.0.0 ip6-allnodes
            0.0.0.0 notadomain
            0.0.0.0 
            [Adblock Plus 2.0]
            example.com##.ad-banner
            0.0.0.0 wild*card.example
            -leading-dash.example
        """.trimIndent()

        val domains = BlocklistRepository.parseText(sample)
        assertTrue("no usable domains expected", domains.isEmpty())
        assertFalse(domains.contains("localhost"))
    }

    @Test
    fun lowercasesAndStripsTrailingDots() {
        val domains = BlocklistRepository.parseText("0.0.0.0 ADS.Example.COM.\n")
        assertTrue(domains.contains("ads.example.com"))
    }

    @Test
    fun parsesStevenBlackStyleRealWorldSample() {
        val sample = """
            # Title: StevenBlack/hosts
            # This hosts file is a merged collection of hosts from reputable sources
            127.0.0.1 localhost
            127.0.0.1 localhost.localdomain
            255.255.255.255 broadcasthost
            ::1 localhost
            0.0.0.0 0.0.0.0
            0.0.0.0 2mdn.net
            0.0.0.0 ads.mopub.com
            0.0.0.0 analytics.tiktok.com
            0.0.0.0 doubleclick.net
        """.trimIndent()

        val domains = BlocklistRepository.parseText(sample)
        assertEquals(setOf("2mdn.net", "ads.mopub.com", "analytics.tiktok.com", "doubleclick.net"), domains)
    }

    @Test
    fun normalisesUserEnteredDomains() {
        assertEquals("example.com", RulesStore.normalizeDomain("https://example.com/path?x=1"))
        assertEquals("example.com", RulesStore.normalizeDomain("WWW.Example.COM"))
        assertEquals("ads.example.com", RulesStore.normalizeDomain("*.ads.example.com"))
        assertEquals("example.com", RulesStore.normalizeDomain("example.com:8443"))

        assertNull(RulesStore.normalizeDomain(""))
        assertNull(RulesStore.normalizeDomain("not a domain"))
        assertNull(RulesStore.normalizeDomain("localhost"))
        assertNull(RulesStore.normalizeDomain("https://"))
    }

    @Test
    fun bundledStarterListIsUsable() {
        // Unit tests run with app/ as the working directory, so the asset is read from source.
        val file = java.io.File("src/main/assets/default_blocklist.txt")
        assertTrue("bundled blocklist must exist next to the module", file.isFile)

        val domains = BlocklistRepository.parseText(file.readText())
        assertTrue("starter list should contain doubleclick.net", domains.contains("doubleclick.net"))
        assertTrue("starter list should contain googlesyndication.com", domains.contains("googlesyndication.com"))
        assertTrue("starter list should be reasonably sized, was ${domains.size}", domains.size > 150)
        assertFalse("localhost must never be blocked", domains.contains("localhost"))
        assertFalse("no raw addresses in the rule set", domains.any { host -> host.all { it.isDigit() || it == '.' } })
    }
}
