package com.adshield.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.adshield.app.core.UpdateInfo
import com.adshield.app.core.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Keeps the app itself up to date from the project's GitHub releases.
 *
 * The app is distributed as an APK outside any store, so the same place that publishes it is the
 * only place that can be asked for a newer build. Two things are deliberate here: the download
 * is checked against the SHA-256 digest GitHub reports for the asset whenever that digest is
 * available, and installing is left to the system package installer rather than done silently.
 */
class UpdateManager(context: Context) {

    private val appContext = context.applicationContext

    fun installedVersion(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { UNKNOWN_VERSION }

    /** Returns the newest release, or null when this build is already the newest one. */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = parseRelease(fetch(LATEST_RELEASE_URL))
                ?: error("no release published yet")
            if (Version.isNewer(release.version, installedVersion())) release else null
        }
    }

    /** Downloads the release APK into the cache and verifies its digest. */
    suspend fun download(release: UpdateInfo, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = release.apkUrl ?: error("release has no apk")
                val directory = File(appContext.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
                val target = File(directory, "AdShield-${release.version}.apk")
                val actual = downloadTo(url, target) { progress ->
                    onProgress(progress.coerceIn(0f, 1f))
                }
                val expected = release.digest
                    ?.removePrefix("sha256:")
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                if (expected != null && expected != actual) {
                    target.delete()
                    error("digest mismatch")
                }
                target
            }
        }

    /** Android 8 and later only let an app install packages when the user allowed it. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    fun installPermissionIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

    /** Hands the downloaded file to the package installer. */
    fun install(file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }.isSuccess

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Streams the APK to disk and returns the SHA-256 of what was actually written. */
    private fun downloadTo(url: String, target: File, onProgress: (Float) -> Unit): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        written += count
                        if (total > 0L) onProgress(written.toFloat() / total.toFloat())
                    }
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/TOP-SHEBAMITMACHIM/AdShield/releases/latest"

        private const val USER_AGENT = "AdShield/1.3 (Android)"
        private const val UNKNOWN_VERSION = "0.0.0"
        private const val CACHE_DIRECTORY = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val DOWNLOAD_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 64 * 1024

        /** Reads the fields the app needs out of a GitHub release payload. */
        internal fun parseRelease(body: String): UpdateInfo? {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val tag = root.optString("tag_name").trim()
            if (tag.isEmpty()) return null

            var apkUrl: String? = null
            var digest: String? = null
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    digest = asset.optString("digest").takeIf { it.isNotBlank() }
                    break
                }
            }

            return UpdateInfo(
                tag = tag,
                version = tag.removePrefix("v").removePrefix("V"),
                notes = root.optString("body").trim(),
                apkUrl = apkUrl,
                digest = digest,
                publishedAt = parseTime(root.optString("published_at"))
            )
        }

        private fun parseTime(value: String): Long {
            if (value.isBlank()) return 0L
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }
}
