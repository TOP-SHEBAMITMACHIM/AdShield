package com.adshield.app.vpn

import android.content.Context
import android.net.ConnectivityManager
import com.adshield.app.core.AppGraph
import com.adshield.app.data.SettingsStore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Forwards allowed lookups to the configured resolver. Encrypted DNS uses RFC 8484
 * (DNS over HTTPS, wire format) and falls back to plain UDP if the endpoint is unreachable.
 */
class DnsUpstream(private val protectSocket: (DatagramSocket) -> Unit) {

    fun resolve(query: ByteArray): ByteArray? {
        val mode = AppGraph.settings.dnsMode
        return if (mode == SettingsStore.DNS_DOH_CLOUDFLARE ||
            mode == SettingsStore.DNS_DOH_GOOGLE ||
            mode == SettingsStore.DNS_DOH_ADGUARD ||
            mode == SettingsStore.DNS_DOH_CUSTOM
        ) {
            dohResolve(query) ?: udpResolve(query)
        } else {
            udpResolve(query)
        }
    }

    private fun udpCandidates(): List<String> = when (AppGraph.settings.dnsMode) {
        SettingsStore.DNS_SYSTEM -> (systemDns() + FALLBACK).distinct()
        SettingsStore.DNS_CLOUDFLARE -> listOf("1.1.1.1", "1.0.0.1")
        SettingsStore.DNS_GOOGLE -> listOf("8.8.8.8", "8.8.4.4")
        SettingsStore.DNS_QUAD9 -> listOf("9.9.9.9", "149.112.112.112")
        SettingsStore.DNS_ADGUARD -> listOf("94.140.14.14", "94.140.15.15")
        SettingsStore.DNS_CUSTOM -> {
            val custom = AppGraph.settings.customUdp
            if (custom.isBlank()) FALLBACK else listOf(custom) + FALLBACK
        }
        else -> FALLBACK
    }

    private fun udpResolve(query: ByteArray): ByteArray? {
        for (host in udpCandidates()) {
            val answer = singleUdpQuery(host, query)
            if (answer != null) return answer
        }
        return null
    }

    private fun singleUdpQuery(host: String, query: ByteArray): ByteArray? {
        return try {
            val socket = DatagramSocket()
            try {
                protectSocket(socket)
                socket.soTimeout = UDP_TIMEOUT_MS
                val address = InetAddress.getByName(host)
                socket.connect(InetSocketAddress(address, DNS_PORT))
                socket.send(DatagramPacket(query, query.size))
                val buffer = ByteArray(MAX_DNS_UDP)
                val packet = DatagramPacket(buffer, buffer.size)
                var answer: ByteArray? = null
                var attempt = 0
                while (attempt < 2 && answer == null) {
                    attempt++
                    socket.receive(packet)
                    val length = packet.length
                    if (length >= 12) {
                        val candidate = buffer.copyOf(length)
                        if (DnsMessage.responseMatches(Net.u16(query, 0), candidate)) {
                            answer = candidate
                        }
                    }
                }
                answer
            } finally {
                runCatching { socket.close() }
            }
        } catch (throwable: Throwable) {
            null
        }
    }

    private fun dohUrl(): String = when (AppGraph.settings.dnsMode) {
        SettingsStore.DNS_DOH_GOOGLE -> "https://dns.google/dns-query"
        SettingsStore.DNS_DOH_ADGUARD -> "https://dns.adguard-dns.com/dns-query"
        SettingsStore.DNS_DOH_CUSTOM -> AppGraph.settings.customDoh.ifBlank { SettingsStore.DEFAULT_DOH }
        else -> SettingsStore.DEFAULT_DOH
    }

    private fun dohResolve(query: ByteArray): ByteArray? {
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(dohUrl()).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = DOH_TIMEOUT_MS
                readTimeout = DOH_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/dns-message")
                setRequestProperty("Accept", "application/dns-message")
            }
            connection.outputStream.use { it.write(query) }
            if (connection.responseCode !in 200..299) return null
            val response = connection.inputStream.use { it.readBytes() }
            if (response.size >= 12 && DnsMessage.responseMatches(Net.u16(query, 0), response)) response else null
        } catch (throwable: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun systemDns(): List<String> {
        val context = AppGraph.app
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return emptyList()
        val network = manager.activeNetwork ?: return emptyList()
        val properties = manager.getLinkProperties(network) ?: return emptyList()
        return properties.dnsServers
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
    }

    private companion object {
        const val DNS_PORT = 53
        const val UDP_TIMEOUT_MS = 2_500
        const val DOH_TIMEOUT_MS = 5_000
        const val MAX_DNS_UDP = 8_192
        val FALLBACK = listOf("1.1.1.1", "8.8.8.8")
    }
}
