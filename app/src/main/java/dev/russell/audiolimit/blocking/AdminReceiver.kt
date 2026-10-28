package dev.russell.audiolimit.blocking

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.russell.audiolimit.R
import dev.russell.audiolimit.appGraph
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Holds no policies. Being an active admin just means Android refuses to uninstall the app until
 * the admin is deactivated. While a lock is active BlockAccessibilityService keeps the user off the
 * device admin pages, and as a backstop a deactivation request covers the confirmation with BlockActivity.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val enforcer = context.appGraph.enforcer
        // Settings waits up to 2 s for this answer; refreshing keeps a just-started process from missing a lock.
        val evaluation = runBlocking { withTimeoutOrNull(REFRESH_TIMEOUT_MS) { enforcer.refresh() } }
            ?: enforcer.evaluation.value
        if (evaluation.frozenUntil == null) return context.getString(R.string.device_admin_disable_warning)
        // Settings holds back app switches until its confirmation dialog is up, so cover it just after.
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed(
            { app.startActivity(BlockActivity.intent(app, admin = true)) },
            COVER_DELAY_MS,
        )
        return context.getString(R.string.device_admin_disable_locked)
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 1_000L
        const val COVER_DELAY_MS = 300L
    }
}
