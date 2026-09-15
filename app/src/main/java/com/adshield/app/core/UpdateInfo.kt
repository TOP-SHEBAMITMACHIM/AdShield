package com.adshield.app.core

/**
 * A published release, reduced to what the app needs to offer an update.
 *
 * [digest] is the `sha256:…` string GitHub reports for the asset. It is used to verify the
 * downloaded file, because an app that installs its own updates should not trust the bytes just
 * because they arrived over TLS.
 */
data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val digest: String?,
    val publishedAt: Long
)
