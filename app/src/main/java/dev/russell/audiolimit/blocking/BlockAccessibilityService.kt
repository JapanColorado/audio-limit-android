package dev.russell.audiolimit.blocking

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import dev.russell.audiolimit.R
import dev.russell.audiolimit.appGraph
import dev.russell.audiolimit.system.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Covers a locked app with [BlockActivity] whenever it comes to the foreground (or becomes locked
 * while in the foreground). While any lock is active it also bounces the user out of system pages
 * that mention this app (App info, accessibility, notification access, device admin, uninstall).
 * Uninstall protection guards the device admin pages on its own, for as long as any rule is frozen.
 */
class BlockAccessibilityService : AccessibilityService() {
    private val graph by lazy { appGraph }
    private var scope: CoroutineScope? = null
    private var foregroundPkg: String? = null
    private var lastBlockAt = 0L
    private var lastGuardCheckAt = 0L
    private var pendingGuardCheck: Job? = null

    override fun onServiceConnected() {
        scope = MainScope().also { s ->
            s.launch {
                graph.enforcer.evaluation.collect {
                    foregroundPkg?.let { pkg -> checkAppLock(pkg) }
                }
            }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPkg = pkg
            if (checkAppLock(pkg)) return
        }
        if (pkg in GUARDED_PACKAGES) checkSettingsGuard()
    }

    override fun onInterrupt() = Unit

    /** Returns true if [pkg] was locked and got covered. */
    private fun checkAppLock(pkg: String): Boolean {
        if (pkg == packageName || pkg in NEVER_BLOCK) return false
        graph.enforcer.evaluation.value.screenBlockFor(pkg) ?: return false
        showBlock(pkg)
        return true
    }

    private fun checkSettingsGuard() {
        val wait = lastGuardCheckAt + GUARD_THROTTLE_MS - SystemClock.elapsedRealtime()
        if (wait > 0) {
            // Check again once the throttle lifts, so content that loads mid-window isn't missed.
            if (pendingGuardCheck?.isActive != true) {
                pendingGuardCheck = scope?.launch {
                    delay(wait)
                    checkSettingsGuard()
                }
            }
            return
        }
        lastGuardCheckAt = SystemClock.elapsedRealtime()
        val evaluation = graph.enforcer.evaluation.value
        if (evaluation.frozenUntil == null) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in GUARDED_PACKAGES) return
        when {
            Permissions.deviceAdmin(this) && root.isDeviceAdminPage() -> showBlock(null, admin = true)
            graph.settings.flow.value.protectSettings && evaluation.anyLocked && root.mentions(R.string.app_name) ->
                showBlock(null)
        }
    }

    /** The admin's description (shown on its page and in the admin list) or one of its deactivation warnings. */
    private fun AccessibilityNodeInfo.isDeviceAdminPage() =
        mentions(R.string.device_admin_description) ||
            mentions(R.string.device_admin_disable_warning) ||
            mentions(R.string.device_admin_disable_locked)

    private fun AccessibilityNodeInfo.mentions(@StringRes text: Int) = !findAccessibilityNodeInfosByText(getString(text)).isNullOrEmpty()

    private fun showBlock(pkg: String?, admin: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return
        lastBlockAt = now
        startActivity(BlockActivity.intent(this, pkg, admin))
    }

    private companion object {
        const val BLOCK_DEBOUNCE_MS = 800L
        const val GUARD_THROTTLE_MS = 300L

        val GUARDED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        /** System surfaces that must stay usable even if a TOTAL rule somehow covers them. */
        val NEVER_BLOCK = GUARDED_PACKAGES + setOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.google.android.dialer",
            "com.android.phone",
        )
    }
}
