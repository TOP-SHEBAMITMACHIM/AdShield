package com.adshield.app.vpn

import java.net.InetAddress

/**
 * The tunnel only routes its own DNS address plus the addresses of well known public resolvers.
 * Everything else keeps using the device network, so battery use stays low while apps still
 * cannot slip past the filter with a hardcoded resolver.
 */
object ResolverIps {

    const val FAKE_DNS4_TEXT = "10.111.222.3"
    const val FAKE_DNS6_TEXT = "fd00:1:2:3::3"

    val FAKE4: ByteArray = InetAddress.getByName(FAKE_DNS4_TEXT).address
    val FAKE6: ByteArray = InetAddress.getByName(FAKE_DNS6_TEXT).address

    private val IPV4_TEXTS = listOf(
        "1.1.1.1",
        "1.0.0.1",
        "8.8.8.8",
        "8.8.4.4",
        "9.9.9.9",
        "149.112.112.112",
        "208.67.222.222",
        "208.67.220.220",
        "94.140.14.14",
        "94.140.15.15",
        "76.76.2.0",
        "76.76.19.19",
        "64.6.64.6",
        "64.6.65.6",
        "156.154.70.1",
        "156.154.71.1",
        "84.200.69.80",
        "84.200.70.40",
        "185.228.168.9",
        "185.228.169.9",
        "77.88.8.8",
        "77.88.8.1",
        "4.2.2.1",
        "4.2.2.2",
        "45.90.28.0",
        "45.90.30.0"
    )

    private val IPV6_TEXTS = listOf(
        "2606:4700:4700::1111",
        "2606:4700:4700::1001",
        "2001:4860:4860::8888",
        "2001:4860:4860::8844",
        "2620:fe::fe",
        "2620:fe::9",
        "2620:119:35::35",
        "2620:119:53::53",
        "2a10:50c0::ad1:ff",
        "2a10:50c0::ad2:ff"
    )

    val IPV4: List<ByteArray> = IPV4_TEXTS.mapNotNull { address(it) }
    val IPV6: List<ByteArray> = IPV6_TEXTS.mapNotNull { address(it) }

    val IPV4_TEXTS_PUBLIC: List<String> = IPV4_TEXTS
    val IPV6_TEXTS_PUBLIC: List<String> = IPV6_TEXTS

    private fun address(text: String): ByteArray? = runCatching {
        val address = InetAddress.getByName(text).address
        if (address.size == 4 || address.size == 16) address else null
    }.getOrNull()

    fun isTunnelDnsV4(ip: ByteArray): Boolean = FAKE4.contentEquals(ip)

    fun isTunnelDnsV6(ip: ByteArray): Boolean = FAKE6.contentEquals(ip)

    fun isKnownResolverV4(ip: ByteArray): Boolean =
        isTunnelDnsV4(ip) || IPV4.any { it.contentEquals(ip) }

    fun isKnownResolverV6(ip: ByteArray): Boolean =
        isTunnelDnsV6(ip) || IPV6.any { it.contentEquals(ip) }
}
