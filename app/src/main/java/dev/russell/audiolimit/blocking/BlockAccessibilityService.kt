package dev.russell.audiolimit.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dev.russell.audiolimit.R
import dev.russell.audiolimit.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Covers a locked app with [BlockActivity] whenever it comes to the foreground (or becomes locked
 * while in the foreground). While any lock is active it also bounces the user out of system pages
 * that mention this app (App info, accessibility, notification access, device admin, uninstall).
 */
class BlockAccessibilityService : AccessibilityService() {
    private val graph by lazy { appGraph }
    private var scope: CoroutineScope? = null
    private var foregroundPkg: String? = null
    private var lastBlockAt = 0L
    private var lastGuardCheckAt = 0L

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
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuardCheckAt < GUARD_THROTTLE_MS) return
        lastGuardCheckAt = now
        if (!graph.settings.flow.value.protectSettings || !graph.enforcer.evaluation.value.anyLocked) return
        val root = rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).isNullOrEmpty()) return
        showBlock(null)
    }

    private fun showBlock(pkg: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MS) return
        lastBlockAt = now
        // Cover rather than going home first: a launcher arriving after us would stop (and finish) the block.
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg),
        )
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
