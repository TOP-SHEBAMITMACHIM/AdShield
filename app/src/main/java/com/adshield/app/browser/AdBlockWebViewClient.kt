package com.adshield.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.filter.FilterEngine
import java.io.ByteArrayInputStream

/**
 * Blocks requests to known ad and tracker hosts before they leave the device, refuses
 * third-party requests in aggressive mode, and injects the cosmetic filter on every page.
 */
class AdBlockWebViewClient(
    private val context: Context,
    private val onBlocked: (String) -> Unit
) : WebViewClient() {

    @Volatile private var pageHost: String = ""

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url ?: return false
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val settings = AppGraph.settings

        if (scheme == "http" && settings.browserUpgradeHttps) {
            val upgraded = url.toString().replaceFirst("http://", "https://")
            runCatching { view.loadUrl(upgraded) }
            return true
        }

        val host = url.host ?: return false
        if (FilterEngine.isBlocked(host)) {
            onBlocked(host)
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url ?: return null
        val scheme = url.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = url.host ?: return null
        val settings = AppGraph.settings

        if (FilterEngine.isBlocked(host)) {
            onBlocked(host)
            return blockedResponse(request.isForMainFrame)
        }

        val whitelisted = FilterEngine.isWhitelisted(host)
        if (!whitelisted && settings.browserStrict && looksLikeAd(url.toString())) {
            onBlocked(host)
            return blockedResponse(request.isForMainFrame)
        }
        if (!whitelisted && settings.browserBlockThirdParty && isThirdParty(host)) {
            onBlocked(host)
            return blockedResponse(request.isForMainFrame)
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        pageHost = hostOf(url)
        inject(view)
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (pageHost.isEmpty()) pageHost = hostOf(url)
        inject(view)
    }

    private fun inject(view: WebView) {
        runCatching { view.evaluateJavascript(CosmeticFilter.javascript(), null) }
    }

    private fun hostOf(url: String?): String =
        runCatching { Uri.parse(url ?: "").host ?: "" }.getOrDefault("")

    private fun isThirdParty(host: String): Boolean {
        val page = pageHost
        if (page.isEmpty()) return false
        if (host == page) return false
        if (host.endsWith(".$page")) return false
        if (page.endsWith(".$host")) return false
        return true
    }

    private fun looksLikeAd(url: String): Boolean {
        val lower = url.lowercase()
        return AD_PATTERNS.any { lower.contains(it) }
    }

    private fun blockedResponse(mainFrame: Boolean): WebResourceResponse {
        if (mainFrame) {
            val html = """
                <!doctype html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <style>body{font-family:sans-serif;background:#0e1729;color:#e8ecf6;padding:40px;text-align:center}</style>
                </head><body>
                <h2>${context.getString(R.string.browser_block_page_title)}</h2>
                <p>${context.getString(R.string.browser_block_page_body)}</p>
                </body></html>
            """.trimIndent()
            return WebResourceResponse(
                "text/html",
                "utf-8",
                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
        }
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private companion object {
        val AD_PATTERNS = listOf(
            "/pagead/",
            "/adserver",
            "/ads/",
            "adservice",
            "doubleclick",
            "googlesyndication",
            "/prebid",
            "/beacon",
            "adsbygoogle",
            "/banner",
            "/sponsor",
            "advertising",
            "/analytics.",
            "gtag/js",
            "/pixel.",
            "adnxs"
        )
    }
}
