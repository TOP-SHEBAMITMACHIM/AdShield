package com.adshield.app.filter

/**
 * Hostnames of well known encrypted DNS providers. Blocking them stops apps and browsers from
 * silently switching to a resolver that ignores the blocklists.
 */
object DohHosts {
    val DOMAINS: Set<String> = setOf(
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "dns.google",
        "dns.google.com",
        "doh.opendns.com",
        "dns.quad9.net",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "dns.nextdns.io",
        "nextdns.io",
        "doh.cleanbrowsing.org",
        "dns.sb",
        "doh.dns.sb",
        "doh.pub",
        "dns.pub",
        "dot.pub",
        "dns.alidns.com",
        "doh.controld.com",
        "controld.com",
        "doh.mullvad.net",
        "dns.ffmuc.net",
        "doh.libredns.gr",
        "dns.dnscrypt.ca",
        "doh.tiar.app",
        "dns.tiar.app"
    )
}
