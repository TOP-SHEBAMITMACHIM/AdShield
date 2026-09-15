package com.adshield.app.vpn

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    @Test
    fun parsesStandardSingleQuestionQuery() {
        val query = buildQuery("ads.doubleclick.net")
        val parsed = DnsMessage.parseQuery(query)

        assertNotNull(parsed)
        parsed!!
        assertEquals(0x1234, parsed.id)
        assertEquals("ads.doubleclick.net", parsed.domain)
        assertEquals(DnsMessage.TYPE_A, parsed.qType)
        assertEquals(1, parsed.qClass)
        assertEquals(query.size, parsed.questionEnd)
    }

    @Test
    fun parsesLongDomainAndUppercaseName() {
        val domain = "very-long-ad-subdomain.tracking.example-network.co.uk"
        val parsed = DnsMessage.parseQuery(buildQuery(domain))
        assertNotNull(parsed)
        assertEquals(domain, parsed!!.domain)
    }

    @Test
    fun rejectsResponsesTruncatedAndEmptyQueries() {
        assertNull("QR bit set means it is a response", DnsMessage.parseQuery(buildQuery("a.com", flags = 0x8100)))
        assertNull("truncated header", DnsMessage.parseQuery(ByteArray(8)))

        val noQuestions = buildQuery("a.com").also {
            it[4] = 0.toByte()
            it[5] = 0.toByte()
        }
        assertNull("a query without questions is not filterable", DnsMessage.parseQuery(noQuestions))
    }

    @Test
    fun multiQuestionQueryStillFiltersItsFirstQuestion() {
        // Real resolvers never send these, but if one turns up it must not slip past the filter
        // unfiltered, so the first question is still evaluated.
        val query = buildQuery("ads.example.com").also {
            it[4] = 0.toByte()
            it[5] = 2.toByte()
        }

        val parsed = DnsMessage.parseQuery(query)
        assertNotNull(parsed)
        assertEquals("ads.example.com", parsed!!.domain)
    }

    @Test
    fun rejectsCompressionPointersInQuestion() {
        val query = buildQuery("ads.example.com")
        query[12] = 0xC0.toByte()
        assertNull(DnsMessage.parseQuery(query))
    }

    @Test
    fun nxdomainEchoesQuestionAndSetsRcode() {
        val query = buildQuery("tracker.example.net")
        val parsed = DnsMessage.parseQuery(query)!!
        val response = DnsMessage.nxdomain(query, parsed)

        assertEquals(parsed.id, Net.u16(response, 0))
        val flags = Net.u16(response, 2)
        assertTrue("QR must be set", flags and 0x8000 != 0)
        assertEquals("NXDOMAIN", 3, flags and 0x0F)
        assertEquals("question count preserved", 1, Net.u16(response, 4))
        assertEquals("no answers", 0, Net.u16(response, 6))
        assertEquals("no authority records", 0, Net.u16(response, 8))
        assertEquals("no additional records", 0, Net.u16(response, 10))
        assertEquals(parsed.questionEnd, response.size)
        assertArrayEquals(
            query.copyOfRange(12, parsed.questionEnd),
            response.copyOfRange(12, parsed.questionEnd)
        )
    }

    @Test
    fun servfailUsesRcodeTwo() {
        val query = buildQuery("example.com")
        val parsed = DnsMessage.parseQuery(query)!!
        val response = DnsMessage.servfail(query, parsed)

        assertEquals(2, Net.u16(response, 2) and 0x0F)
        assertEquals(1, Net.u16(response, 4))
    }

    @Test
    fun responseMatchingComparesTransactionId() {
        val query = buildQuery("example.com")
        val parsed = DnsMessage.parseQuery(query)!!
        val response = DnsMessage.nxdomain(query, parsed)

        assertTrue(DnsMessage.responseMatches(parsed.id, response))
        assertTrue(!DnsMessage.responseMatches(parsed.id + 1, response))
        assertTrue(!DnsMessage.responseMatches(parsed.id, ByteArray(4)))
    }

    private fun buildQuery(
        domain: String,
        id: Int = 0x1234,
        flags: Int = 0x0100,
        qType: Int = DnsMessage.TYPE_A
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write((value shr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        u16(id)
        u16(flags)
        u16(1)
        u16(0)
        u16(0)
        u16(0)
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        u16(qType)
        u16(1)
        return out.toByteArray()
    }
}
