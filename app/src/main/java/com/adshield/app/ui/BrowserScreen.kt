package com.adshield.app.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adshield.app.R
import com.adshield.app.browser.AdBlockWebViewClient
import com.adshield.app.core.AppGraph

@Composable
fun BrowserScreen() {
    val context = LocalContext.current
    val prefs = AppGraph.settings
    val homeUrl = stringResource(R.string.browser_home)

    val urlText = remember { mutableStateOf(prefs.browserLastUrl.ifBlank { "" }) }
    val currentUrl = remember { mutableStateOf(urlText.value.ifBlank { homeUrl }) }
    val progress = remember { mutableStateOf(0) }
    val blockedCount = remember { mutableStateOf(0) }
    val menuOpen = remember { mutableStateOf(false) }
    val status = remember { mutableStateOf<String?>(null) }
    val strict = remember { mutableStateOf(prefs.browserStrict) }
    val blockThirdParty = remember { mutableStateOf(prefs.browserBlockThirdParty) }
    val javaScriptEnabled = remember { mutableStateOf(prefs.browserJavaScript) }
    val desktopMode = remember { mutableStateOf(prefs.browserDesktop) }
    val httpsOnly = remember { mutableStateOf(prefs.browserUpgradeHttps) }

    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return homeUrl
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return if (!trimmed.contains(".") || trimmed.contains(" ")) {
            "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        } else {
            "https://$trimmed"
        }
    }

    val defaultUserAgent = remember { WebSettings.getDefaultUserAgent(context) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = javaScriptEnabled.value
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.loadsImagesAutomatically = true
            settings.userAgentString = if (desktopMode.value) DESKTOP_UA else defaultUserAgent
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, !blockThirdParty.value)
            webViewClient = AdBlockWebViewClient(context) { blockedCount.value++ }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.value = newProgress
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false
            }
            setDownloadListener { url, _, _, _, _ ->
                runCatching {
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        Uri.parse(url).lastPathSegment ?: "download"
                    )
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                }
            }
        }
    }

    fun load(target: String) {
        val normalized = normalize(target)
        urlText.value = normalized
        currentUrl.value = normalized
        runCatching { webView.loadUrl(normalized) }
    }

    fun currentHost(): String =
        runCatching { Uri.parse(currentUrl.value).host ?: "" }.getOrDefault("").orEmpty()

    LaunchedEffect(Unit) {
        load(currentUrl.value)
    }

    DisposableEffect(Unit) {
        onDispose {
            prefs.browserLastUrl = currentUrl.value
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (progress.value in 1..99) {
            LinearProgressIndicator(
                progress = { progress.value / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (webView.canGoBack()) webView.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browser_back))
            }
            OutlinedTextField(
                value = urlText.value,
                onValueChange = { urlText.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.browser_url_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { load(urlText.value) }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { load(urlText.value) }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.browser_reload))
            }
            Box {
                IconButton(onClick = { menuOpen.value = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.browser_menu))
                }
                DropdownMenu(
                    expanded = menuOpen.value,
                    onDismissRequest = { menuOpen.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_whitelist_site)) },
                        onClick = {
                            menuOpen.value = false
                            val host = currentHost()
                            if (host.isNotEmpty()) {
                                AppGraph.rules.addWhitelist(host)
                                AppGraph.refreshFiltersAsync()
                                status.value = context.getString(R.string.browser_site_whitelisted, host)
                                load(currentUrl.value)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.browser_desktop_mode) + if (desktopMode.value) " ✓" else ""
                            )
                        },
                        onClick = {
                            menuOpen.value = false
                            desktopMode.value = !desktopMode.value
                            prefs.browserDesktop = desktopMode.value
                            webView.settings.userAgentString = if (desktopMode.value) DESKTOP_UA else defaultUserAgent
                            load(currentUrl.value)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.browser_strict_mode) + if (strict.value) " ✓" else ""
                            )
                        },
                        onClick = {
                            menuOpen.value = false
                            strict.value = !strict.value
                            prefs.browserStrict = strict.value
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.browser_block_third_party) +
                                    if (blockThirdParty.value) " ✓" else ""
                            )
                        },
                        onClick = {
                            menuOpen.value = false
                            blockThirdParty.value = !blockThirdParty.value
                            prefs.browserBlockThirdParty = blockThirdParty.value
                            CookieManager.getInstance().setAcceptThirdPartyCookies(
                                webView,
                                !blockThirdParty.value
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.browser_javascript) +
                                    if (javaScriptEnabled.value) " ✓" else ""
                            )
                        },
                        onClick = {
                            menuOpen.value = false
                            javaScriptEnabled.value = !javaScriptEnabled.value
                            prefs.browserJavaScript = javaScriptEnabled.value
                            webView.settings.javaScriptEnabled = javaScriptEnabled.value
                            load(currentUrl.value)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.browser_upgrade_https) +
                                    if (httpsOnly.value) " ✓" else ""
                            )
                        },
                        onClick = {
                            menuOpen.value = false
                            httpsOnly.value = !httpsOnly.value
                            prefs.browserUpgradeHttps = httpsOnly.value
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_clear_data)) },
                        onClick = {
                            menuOpen.value = false
                            runCatching {
                                CookieManager.getInstance().removeAllCookies(null)
                                WebStorage.getInstance().deleteAllData()
                                webView.clearCache(true)
                                webView.clearHistory()
                                webView.clearFormData()
                            }
                            status.value = context.getString(R.string.browser_clear_done)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_external)) },
                        onClick = {
                            menuOpen.value = false
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl.value))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.browser_blocked_count, blockedCount.value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            status.value?.let {
                Text(
                    // The whitelist confirmation carries a full hostname, which used to push the
                    // blocked counter off the narrow row instead of being shortened.
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
