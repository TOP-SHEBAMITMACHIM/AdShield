package com.adshield.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetTest {

    private val client4 = byteArrayOf(10, 111, 222.toByte(), 1)
    private val fakeDns4 = byteArrayOf(10, 111, 222.toByte(), 3)

    @Test
    fun udpV4RoundTripKeepsAddressesPortsAndPayload() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val packet = Net.buildUdpV4(fakeDns4, client4, 53, 44444, payload)

        val parsed = Net.parseUdpV4(packet, packet.size)
        assertNotNull(parsed)
        parsed!!

        assertArrayEquals(fakeDns4, parsed.src)
        assertArrayEquals(client4, parsed.dst)
        assertEquals(53, parsed.srcPort)
        assertEquals(44444, parsed.dstPort)
        assertEquals(payload.size, parsed.payloadLength)
        assertArrayEquals(
            payload,
            packet.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength)
        )
    }

    @Test
    fun udpV4SetsValidIpAndUdpChecksums() {
        val packet = Net.buildUdpV4(fakeDns4, client4, 53, 44444, byteArrayOf(9, 9, 9, 9))

        assertTrue("IPv4 header checksum must verify", ipv4HeaderChecksumValid(packet))
        assertTrue("UDP checksum must verify", udpChecksumValid(packet, 20, 8 + 4))
    }

    @Test
    fun udpV6RoundTripAndChecksum() {
        val client6 = ipv6Bytes("fd00:1:2:3::99")
        val packet = Net.buildUdpV6(ResolverIps.FAKE6, client6, 53, 33333, byteArrayOf(7, 7, 7))

        val parsed = Net.parseUdpV6(packet, packet.size)
        assertNotNull(parsed)
        parsed!!

        assertArrayEquals(ResolverIps.FAKE6, parsed.src)
        assertArrayEquals(client6, parsed.dst)
        assertEquals(53, parsed.srcPort)
        assertEquals(33333, parsed.dstPort)
        assertTrue(udpChecksumValidV6(packet, 40, 8 + 3))
    }

    @Test
    fun parseIgnoresNonUdpAndTruncatedPackets() {
        val syn = syntheticTcpSyn()
        val reset = Net.buildTcpResetV4(Net.parseTcpV4(syn, syn.size)!!)
        assertNull("TCP is not UDP", Net.parseUdpV4(reset, reset.size))
        assertNull(Net.parseUdpV4(ByteArray(4), 4))
        assertNull(Net.parseUdpV6(ByteArray(10), 10))
    }

    @Test
    fun tcpResetSwapsEndpointsAndIsWellFormed() {
        val syn = syntheticTcpSyn()
        val parsed = Net.parseTcpV4(syn, syn.size)
        assertNotNull(parsed)
        parsed!!
        assertTrue(parsed.syn)
        assertEquals(443, parsed.dstPort)

        val reset = Net.buildTcpResetV4(parsed)
        val resetParsed = Net.parseTcpV4(reset, reset.size)
        assertNotNull(resetParsed)
        resetParsed!!

        // answer comes from the server side towards the client
        assertArrayEquals(parsed.dst, resetParsed.src)
        assertArrayEquals(parsed.src, resetParsed.dst)
        assertEquals(443, resetParsed.srcPort)
        assertEquals(parsed.srcPort, resetParsed.dstPort)
        assertFalse("RST is not a SYN", resetParsed.syn)
        assertTrue("RST must carry the ACK flag", resetParsed.ackFlag)
        assertEquals("reset sequence starts the exchange over", 0L, resetParsed.seq)
        assertEquals("reset must acknowledge the SYN", parsed.seq + 1L, resetParsed.ack)
        assertTrue("IPv4 checksum of the reset must verify", ipv4HeaderChecksumValid(reset))
    }

    @Test
    fun fragmentOffsetPacketsAreIgnored() {
        val packet = Net.buildUdpV4(fakeDns4, client4, 53, 5000, byteArrayOf(1, 2, 3))
        Net.put16(packet, 6, 0x2000 or 0x0010) // fragmented follow-up
        assertNull(Net.parseUdpV4(packet, packet.size))
    }

    // ------------------------------------------------------------- helpers

    private fun syntheticTcpSyn(): ByteArray {
        val packet = ByteArray(40)
        packet[0] = 0x45
        Net.put16(packet, 2, 40)
        Net.put16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = Net.PROTO_TCP.toByte()
        System.arraycopy(client4, 0, packet, 12, 4)
        System.arraycopy(fakeDns4, 0, packet, 16, 4)
        val tcp = 20
        Net.put16(packet, tcp, 51234)      // source port
        Net.put16(packet, tcp + 2, 443)    // destination port
        Net.put32(packet, tcp + 4, 1000L)  // sequence
        packet[tcp + 12] = 0x50            // data offset
        packet[tcp + 13] = 0x02            // SYN
        Net.put16(packet, tcp + 14, 8192)
        return packet
    }

    private fun ipv4HeaderChecksumValid(packet: ByteArray): Boolean {
        var sum = 0
        var index = 0
        while (index < 20) {
            sum += Net.u16(packet, index)
            index += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum == 0xFFFF
    }

    private fun udpChecksumValid(packet: ByteArray, udpOffset: Int, udpLength: Int): Boolean {
        val pseudo = ByteArray(12 + udpLength)
        System.arraycopy(packet, 12, pseudo, 0, 8) // src + dst
        pseudo[9] = Net.PROTO_UDP.toByte()
        Net.put16(pseudo, 10, udpLength)
        System.arraycopy(packet, udpOffset, pseudo, 12, udpLength)
        return foldsToAllOnes(pseudo)
    }

    private fun udpChecksumValidV6(packet: ByteArray, udpOffset: Int, udpLength: Int): Boolean {
        val pseudo = ByteArray(40 + udpLength)
        System.arraycopy(packet, 8, pseudo, 0, 32) // src + dst
        Net.put32(pseudo, 32, udpLength.toLong())
        pseudo[39] = Net.PROTO_UDP.toByte()
        System.arraycopy(packet, udpOffset, pseudo, 40, udpLength)
        return foldsToAllOnes(pseudo)
    }

    private fun foldsToAllOnes(data: ByteArray): Boolean {
        var sum = 0
        var index = 0
        while (index + 1 < data.size) {
            sum += Net.u16(data, index)
            index += 2
        }
        if (index < data.size) sum += (data[index].toInt() and 0xFF) shl 8
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum == 0xFFFF
    }

    private fun ipv6Bytes(text: String): ByteArray =
        java.net.InetAddress.getByName(text).address
}
