package dev.russell.audiolimit.system

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.russell.audiolimit.blocking.AdminReceiver
import dev.russell.audiolimit.blocking.BlockAccessibilityService
import dev.russell.audiolimit.tracking.MediaListenerService

/** Status of, and settings intents for, the special access the app depends on. */
object Permissions {
    fun notificationAccess(context: Context) =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun accessibility(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = ComponentName(context, BlockAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun deviceAdmin(context: Context) =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

    fun postNotifications(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun adminComponent(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun notificationAccessIntent(context: Context) =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, MediaListenerService::class.java).flattenToString(),
        )

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun deviceAdminIntent(context: Context) =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "While a lock is active, Audio Limit can't be uninstalled without deactivating this first.",
            )

    /** App info, where sideloaded apps can "Allow restricted settings". */
    fun appInfoIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
