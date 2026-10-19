package dev.russell.audiolimit.domain

import java.time.DayOfWeek

enum class RuleKind { APP, GROUP, TOTAL }

/**
 * A limit on media playback.
 *
 * [packages] are the apps the rule covers for APP/GROUP, and the apps it *excludes* for TOTAL.
 * [limitMinutes] is a daily budget (null = schedule-only rule). It applies on [limitDays], judged by
 * the weekday the usage day started on. [windows] block the covered apps outright.
 */
data class Rule(
    val id: Long = 0,
    val name: String,
    val kind: RuleKind,
    val packages: Set<String>,
    val limitMinutes: Int?,
    val limitDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val windows: List<BlockWindow> = emptyList(),
) {
    fun appliesTo(pkg: String) = if (kind == RuleKind.TOTAL) pkg !in packages else pkg in packages
}

/**
 * A recurring block, in minutes since local midnight. `endMinute <= startMinute` means the window runs
 * past midnight. [days] are the days the window *starts* on. LockGuard rejects start == end.
 */
data class BlockWindow(val startMinute: Int, val endMinute: Int, val days: Set<DayOfWeek>)

/** Bit i set = DayOfWeek with value i+1 (Monday = bit 0). */
object DayMask {
    fun of(days: Set<DayOfWeek>) = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    fun days(mask: Int) = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}
