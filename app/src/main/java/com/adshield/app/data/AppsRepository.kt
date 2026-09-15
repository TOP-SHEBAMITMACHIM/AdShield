package com.adshield.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installed applications, used for the per-app exclusion list. */
class AppsRepository(private val context: Context) {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val uid: Int,
        val system: Boolean
    )

    suspend fun load(selfPackage: String): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val entries = ArrayList<AppEntry>(packages.size)
        for (pkg in packages) {
            if (pkg.packageName == selfPackage) continue
            val info: ApplicationInfo = pkg.applicationInfo ?: continue
            val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(pkg.packageName)
            entries += AppEntry(pkg.packageName, label, info.uid, system)
        }
        entries.sortBy { it.label.lowercase() }
        entries
    }

    fun uidFor(packageName: String): Int? = runCatching {
        context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).uid
    }.getOrNull()
}
