package dev.russell.audiolimit.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(val packageName: String, val label: String)

/** Labels and icons for installed apps, cached in memory. */
class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    /** The app's label, or its package name if it's not installed. */
    fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun icon(pkg: String): Bitmap? = icons[pkg] ?: try {
        pm.getApplicationIcon(pkg).toBitmap(width = ICON_PX, height = ICON_PX).also { icons[pkg] = it }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Apps with a launcher entry, excluding this one, sorted by label. */
    fun launchableApps(): List<AppEntry> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { AppEntry(it, label(it)) }
            .sortedBy { it.label.lowercase() }

    private companion object {
        const val ICON_PX = 96
    }
}
