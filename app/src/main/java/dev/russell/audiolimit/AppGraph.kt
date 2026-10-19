package dev.russell.audiolimit

import android.content.Context
import dev.russell.audiolimit.blocking.Enforcer
import dev.russell.audiolimit.blocking.LockGuard
import dev.russell.audiolimit.data.AppCatalog
import dev.russell.audiolimit.data.RuleRepository
import dev.russell.audiolimit.data.SettingsRepository
import dev.russell.audiolimit.data.UsageRepository
import dev.russell.audiolimit.data.db.AppDatabase
import dev.russell.audiolimit.tracking.Notifier
import dev.russell.audiolimit.tracking.PlaybackTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: every long-lived object, built once in [AudioLimitApp]. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.build(context)
    val settings = SettingsRepository(context, scope)
    val rules = RuleRepository(db.ruleDao())
    val usage = UsageRepository(db.usageDao())
    val enforcer = Enforcer(rules, usage, settings, scope)
    val guard = LockGuard(enforcer, rules, settings)
    val tracker = PlaybackTracker(db.usageDao(), scope)
    val notifier = Notifier(context)
    val apps = AppCatalog(context)
}

val Context.appGraph: AppGraph get() = (applicationContext as AudioLimitApp).graph
