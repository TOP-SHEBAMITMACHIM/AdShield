package com.adshield.app.vpn

import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal IP stack helpers. AdShield only needs to understand the packets that arrive on the
 * tunnel interface: DNS queries to intercept, and TCP connects to known resolvers that must be
 * reset so apps cannot talk to an unfiltered resolver.
 */
object Net {

    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    private val idCounter = AtomicInteger(1)

    data class UdpPacket(
        val src: ByteArray,
        val dst: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int
    )

    data class TcpPacket(
        val src: ByteArray,
        val dst: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val seq: Long,
        val ack: Long,
        val payloadLength: Int,
        val syn: Boolean,
        val ackFlag: Boolean
    )

    private class IpHeader(
        val protocol: Int,
        val headerLength: Int,
        val totalLength: Int,
        val fragmentOffset: Int,
        val src: ByteArray,
        val dst: ByteArray
    )

    // ---------------------------------------------------------------- parsing

    private fun parseIpv4(buffer: ByteArray, length: Int): IpHeader? {
        if (length < 20) return null
        if (((buffer[0].toInt() shr 4) and 0x0F) != 4) return null
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength) return null
        val declared = u16(buffer, 2)
        val total = if (declared in headerLength..length) declared else length
        return IpHeader(
            protocol = buffer[9].toInt() and 0xFF,
            headerLength = headerLength,
            totalLength = total,
            fragmentOffset = u16(buffer, 6) and 0x1FFF,
            src = buffer.copyOfRange(12, 16),
            dst = buffer.copyOfRange(16, 20)
        )
    }

    fun parseUdpV4(buffer: ByteArray, length: Int): UdpPacket? {
        val ip = parseIpv4(buffer, length) ?: return null
        if (ip.protocol != PROTO_UDP) return null
        if (ip.fragmentOffset != 0) return null
        val offset = ip.headerLength
        if (ip.totalLength < offset + 8) return null
        val udpLength = u16(buffer, offset + 4)
        if (udpLength < 8) return null
        val payloadLength = minOf(udpLength - 8, ip.totalLength - offset - 8)
        if (payloadLength <= 0) return null
        return UdpPacket(
            src = ip.src,
            dst = ip.dst,
            srcPort = u16(buffer, offset),
            dstPort = u16(buffer, offset + 2),
            payloadOffset = offset + 8,
            payloadLength = payloadLength
        )
    }

    fun parseUdpV6(buffer: ByteArray, length: Int): UdpPacket? {
        if (length < 48) return null
        if (((buffer[0].toInt() shr 4) and 0x0F) != 6) return null
        if ((buffer[6].toInt() and 0xFF) != PROTO_UDP) return null
        val declared = u16(buffer, 4)
        val total = if (declared in 8..(length - 40)) 40 + declared else length
        if (total < 48) return null
        val offset = 40
        val udpLength = u16(buffer, offset + 4)
        if (udpLength < 8) return null
        val payloadLength = minOf(udpLength - 8, total - offset - 8)
        if (payloadLength <= 0) return null
        return UdpPacket(
            src = buffer.copyOfRange(8, 24),
            dst = buffer.copyOfRange(24, 40),
            srcPort = u16(buffer, offset),
            dstPort = u16(buffer, offset + 2),
            payloadOffset = offset + 8,
            payloadLength = payloadLength
        )
    }

    fun parseTcpV4(buffer: ByteArray, length: Int): TcpPacket? {
        val ip = parseIpv4(buffer, length) ?: return null
        if (ip.protocol != PROTO_TCP) return null
        if (ip.fragmentOffset != 0) return null
        val offset = ip.headerLength
        if (ip.totalLength < offset + 20) return null
        val dataOffset = ((buffer[offset + 12].toInt() shr 4) and 0x0F) * 4
        if (dataOffset < 20) return null
        val flags = buffer[offset + 13].toInt() and 0xFF
        val payloadLength = (ip.totalLength - offset - dataOffset).coerceAtLeast(0)
        return TcpPacket(
            src = ip.src,
            dst = ip.dst,
            srcPort = u16(buffer, offset),
            dstPort = u16(buffer, offset + 2),
            seq = u32(buffer, offset + 4),
            ack = u32(buffer, offset + 8),
            payloadLength = payloadLength,
            syn = (flags and 0x02) != 0,
            ackFlag = (flags and 0x10) != 0
        )
    }

    // ---------------------------------------------------------------- building

    fun buildUdpV4(
        src: ByteArray,
        dst: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray = wrapIpv4(PROTO_UDP, src, dst, udpSegmentV4(src, dst, srcPort, dstPort, payload))

    fun buildUdpV6(
        src: ByteArray,
        dst: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray = wrapIpv6(PROTO_UDP, src, dst, udpSegmentV6(src, dst, srcPort, dstPort, payload))

    /** Builds a RST+ACK straight back at the client, as if the remote host refused the port. */
    fun buildTcpResetV4(packet: TcpPacket): ByteArray {
        val seq = if (packet.ackFlag) packet.ack else 0L
        val ack = packet.seq + packet.payloadLength + if (packet.syn) 1L else 0L
        val segment = ByteArray(20)
        put16(segment, 0, packet.dstPort)
        put16(segment, 2, packet.srcPort)
        put32(segment, 4, seq and 0xFFFFFFFFL)
        put32(segment, 8, ack and 0xFFFFFFFFL)
        segment[12] = 0x50
        segment[13] = 0x14
        put16(segment, 14, 0)
        put16(segment, 16, 0)
        put16(segment, 18, 0)
        val pseudo = ByteArray(12 + segment.size)
        System.arraycopy(packet.dst, 0, pseudo, 0, 4)
        System.arraycopy(packet.src, 0, pseudo, 4, 4)
        pseudo[9] = PROTO_TCP.toByte()
        put16(pseudo, 10, segment.size)
        System.arraycopy(segment, 0, pseudo, 12, segment.size)
        val sum = fold(checksumSum(pseudo, 0, pseudo.size))
        put16(segment, 16, if (sum == 0) 0xFFFF else sum)
        return wrapIpv4(PROTO_TCP, packet.dst, packet.src, segment)
    }

    private fun udpSegmentV4(
        src: ByteArray,
        dst: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val segment = ByteArray(8 + payload.size)
        put16(segment, 0, srcPort)
        put16(segment, 2, dstPort)
        put16(segment, 4, segment.size)
        put16(segment, 6, 0)
        System.arraycopy(payload, 0, segment, 8, payload.size)

        val pseudo = ByteArray(12 + segment.size)
        System.arraycopy(src, 0, pseudo, 0, 4)
        System.arraycopy(dst, 0, pseudo, 4, 4)
        pseudo[8] = 0
        pseudo[9] = PROTO_UDP.toByte()
        put16(pseudo, 10, segment.size)
        System.arraycopy(segment, 0, pseudo, 12, segment.size)
        val sum = fold(checksumSum(pseudo, 0, pseudo.size))
        put16(segment, 6, if (sum == 0) 0xFFFF else sum)
        return segment
    }

    private fun udpSegmentV6(
        src: ByteArray,
        dst: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val segment = ByteArray(8 + payload.size)
        put16(segment, 0, srcPort)
        put16(segment, 2, dstPort)
        put16(segment, 4, segment.size)
        put16(segment, 6, 0)
        System.arraycopy(payload, 0, segment, 8, payload.size)

        val pseudo = ByteArray(40 + segment.size)
        System.arraycopy(src, 0, pseudo, 0, 16)
        System.arraycopy(dst, 0, pseudo, 16, 16)
        put32(pseudo, 32, segment.size.toLong())
        pseudo[39] = PROTO_UDP.toByte()
        System.arraycopy(segment, 0, pseudo, 40, segment.size)
        val sum = fold(checksumSum(pseudo, 0, pseudo.size))
        put16(segment, 6, if (sum == 0) 0xFFFF else sum)
        return segment
    }

    private fun wrapIpv4(protocol: Int, src: ByteArray, dst: ByteArray, payload: ByteArray): ByteArray {
        val packet = ByteArray(20 + payload.size)
        packet[0] = 0x45
        put16(packet, 2, packet.size)
        put16(packet, 4, idCounter.getAndIncrement() and 0xFFFF)
        put16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = protocol.toByte()
        System.arraycopy(src, 0, packet, 12, 4)
        System.arraycopy(dst, 0, packet, 16, 4)
        put16(packet, 10, checksum(packet, 0, 20))
        System.arraycopy(payload, 0, packet, 20, payload.size)
        return packet
    }

    private fun wrapIpv6(nextHeader: Int, src: ByteArray, dst: ByteArray, payload: ByteArray): ByteArray {
        val packet = ByteArray(40 + payload.size)
        packet[0] = 0x60
        put16(packet, 4, payload.size)
        packet[6] = nextHeader.toByte()
        packet[7] = 64
        System.arraycopy(src, 0, packet, 8, 16)
        System.arraycopy(dst, 0, packet, 24, 16)
        System.arraycopy(payload, 0, packet, 40, payload.size)
        return packet
    }

    // ---------------------------------------------------------------- primitives

    fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value shr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }

    fun u32(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)

    fun put32(data: ByteArray, offset: Int, value: Long) {
        data[offset] = ((value shr 24) and 0xFF).toByte()
        data[offset + 1] = ((value shr 16) and 0xFF).toByte()
        data[offset + 2] = ((value shr 8) and 0xFF).toByte()
        data[offset + 3] = (value and 0xFF).toByte()
    }

    private fun checksumSum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            index += 2
        }
        if (index < end) sum += (data[index].toInt() and 0xFF) shl 8
        return sum
    }

    private fun fold(sum: Int): Int {
        var value = sum
        while ((value shr 16) != 0) value = (value and 0xFFFF) + (value shr 16)
        return value.inv() and 0xFFFF
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int =
        fold(checksumSum(data, offset, length))

    fun ipEquals(a: ByteArray, b: ByteArray): Boolean = a.contentEquals(b)
}
