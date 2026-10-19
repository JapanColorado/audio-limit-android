package dev.russell.audiolimit.blocking

import dev.russell.audiolimit.data.RuleRepository
import dev.russell.audiolimit.data.SettingsRepository
import dev.russell.audiolimit.domain.Loosening
import dev.russell.audiolimit.domain.Permanence
import dev.russell.audiolimit.domain.Rule
import dev.russell.audiolimit.domain.RuleKind
import dev.russell.audiolimit.util.formatUntil

/**
 * The only path for user edits to rules and settings. While a lock is active, anything that would
 * loosen it is refused. Every method returns an error message to show, or null on success.
 */
class LockGuard(
    private val enforcer: Enforcer,
    private val rules: RuleRepository,
    private val settings: SettingsRepository,
) {
    suspend fun saveRule(rule: Rule): String? {
        validate(rule)?.let { return it }
        if (rule.id != 0L) {
            val old = rules.get(rule.id)
            val status = enforcer.refresh().statusOf(rule.id)
            if (old != null && status != null && status.isBlocking) {
                Loosening.reason(old, rule)?.let {
                    return "Locked until ${formatUntil(status.blockedUntil!!)} — you can't $it right now."
                }
            }
        }
        rules.save(rule)
        enforcer.refresh()
        return null
    }

    suspend fun deleteRule(id: Long): String? {
        enforcer.refresh().statusOf(id)?.takeIf { it.isBlocking }?.let {
            return "Locked until ${formatUntil(it.blockedUntil!!)} — you can't delete this rule right now."
        }
        rules.delete(id)
        enforcer.refresh()
        return null
    }

    suspend fun setResetHour(hour: Int): String? {
        anyLockMessage("change the reset time")?.let { return it }
        settings.setResetHour(hour)
        enforcer.refresh()
        return null
    }

    suspend fun setProtectSettings(enabled: Boolean): String? {
        if (!enabled) anyLockMessage("turn off settings protection")?.let { return it }
        settings.setProtectSettings(enabled)
        return null
    }

    /** Null if no lock is active; otherwise why [action] is refused. */
    suspend fun anyLockMessage(action: String): String? =
        enforcer.refresh().lockedUntil?.let { "A lock is active until ${formatUntil(it)} — you can't $action right now." }

    private fun validate(rule: Rule): String? = when {
        rule.name.isBlank() -> "Give the rule a name."
        rule.kind == RuleKind.APP && rule.packages.size != 1 -> "Pick one app."
        rule.kind == RuleKind.GROUP && rule.packages.isEmpty() -> "Pick at least one app."
        rule.limitMinutes == null && rule.windows.isEmpty() -> "Set a daily limit or add a block window."
        rule.limitMinutes != null && rule.limitDays.isEmpty() -> "Pick at least one day for the limit."
        rule.windows.any { it.days.isEmpty() } -> "Every block window needs at least one day."
        // The next three would make a lock that never lifts, leaving safe mode as the only way out.
        rule.limitMinutes != null && rule.limitMinutes < MIN_LIMIT_MINUTES ->
            "The daily limit must be at least $MIN_LIMIT_MINUTES minutes. Use a block window to block outright."
        rule.windows.any { it.startMinute == it.endMinute } -> "A block window needs different start and end times."
        Permanence.windowsCoverWholeWeek(rule.windows) ->
            "These block windows cover the whole week, so the rule would never unlock."
        else -> null
    }

    private companion object {
        const val MIN_LIMIT_MINUTES = 5
    }
}
