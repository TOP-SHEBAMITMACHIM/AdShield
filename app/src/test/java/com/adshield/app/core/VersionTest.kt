package com.adshield.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun treatsDoubleDigitSegmentsAsNumbers() {
        assertTrue(Version.isNewer("1.10.0", "1.9.0"))
        assertFalse(Version.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun ignoresALeadingTagPrefix() {
        assertEquals(0, Version.compare("v1.3.0", "1.3.0"))
        assertTrue(Version.isNewer("V1.3.1", "v1.3.0"))
    }

    @Test
    fun aMissingSegmentCountsAsZero() {
        assertEquals(0, Version.compare("1.3", "1.3.0"))
        assertTrue(Version.isNewer("1.3.1", "1.3"))
    }

    @Test
    fun ignoresSuffixes() {
        assertTrue(Version.isNewer("1.3.0", "1.2.0"))
        assertEquals(0, Version.compare("1.3.0-beta", "1.3.0"))
    }

    @Test
    fun reportsNothingNewerForTheSameVersion() {
        assertFalse(Version.isNewer("1.3.0", "1.3.0"))
        assertEquals(0, Version.compare("1.3.0", "1.3.0"))
    }

    @Test
    fun handlesGarbageWithoutThrowing() {
        assertEquals(0, Version.compare("", ""))
        assertTrue(Version.isNewer("2.0.0", "not-a-version"))
        assertEquals(-1, Version.compare("1.0.0", "2.0.0"))
    }
}
