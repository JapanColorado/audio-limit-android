package dev.russell.audiolimit.blocking

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Holds no policies. Being an active admin just means Android refuses to uninstall the app until
 * the admin is deactivated, and BlockAccessibilityService guards that settings page while locked.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Audio Limit will no longer be protected from uninstall."
}
