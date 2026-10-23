package dev.russell.audiolimit.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

data class RuleStatus(
    val rule: Rule,
    val usedMillis: Long,
    /** Null when the rule has no limit, or the limit doesn't apply on today's weekday. */
    val limitMillis: Long?,
    /** End of the block window active right now, if any. */
    val activeWindowEnd: ZonedDateTime?,
    val nextReset: ZonedDateTime,
    /** How long before the limit the warning goes out; 0 = warnings off. */
    val warnMillis: Long = 0,
) {
    val limitReached get() = limitMillis != null && usedMillis >= limitMillis
    val isBlocking get() = limitReached || activeWindowEnd != null
    val remainingMillis: Long? get() = limitMillis?.let { (it - usedMillis).coerceAtLeast(0) }

    /** The limit is about to run out: the warning is due, and the rule is frozen like a lock. */
    val isWarning get() = warnMillis > 0 && (remainingMillis ?: 0) in 1..warnMillis

    /** When the block lifts. A reached limit holds until the next reset. */
    val blockedUntil: ZonedDateTime?
        get() = listOfNotNull(nextReset.takeIf { limitReached }, activeWindowEnd).maxOrNull()

    /** Blocking or warning, so the rule may only get stricter. */
    val isFrozen get() = isBlocking || isWarning

    /** When the rule may be loosened again. A warning holds until the next reset, since usage only grows. */
    val frozenUntil: ZonedDateTime?
        get() = listOfNotNull(blockedUntil, nextReset.takeIf { isWarning }).maxOrNull()
}

class Evaluation(
    val statuses: List<RuleStatus>,
    /** Every app that has ever played media. */
    val mediaPackages: Set<String>,
    val dayStart: ZonedDateTime,
    val nextReset: ZonedDateTime,
) {
    /** The block that should stop [pkg] playing (the longest-lasting one), or null. */
    fun blockFor(pkg: String): RuleStatus? =
        statuses.filter { it.isBlocking && it.rule.appliesTo(pkg) }.maxByOrNull { it.blockedUntil!! }

    /**
     * The block that should cover [pkg]'s screen. TOTAL rules only lock apps that have played media,
     * otherwise they would lock the dialer and everything else.
     */
    fun screenBlockFor(pkg: String): RuleStatus? =
        statuses
            .filter { it.isBlocking && it.rule.appliesTo(pkg) && (it.rule.kind != RuleKind.TOTAL || pkg in mediaPackages) }
            .maxByOrNull { it.blockedUntil!! }

    fun statusOf(ruleId: Long): RuleStatus? = statuses.find { it.rule.id == ruleId }

    val anyLocked get() = statuses.any { it.isBlocking }
    val lockedUntil: ZonedDateTime? get() = statuses.mapNotNull { it.blockedUntil }.maxOrNull()
    val frozenUntil: ZonedDateTime? get() = statuses.mapNotNull { it.frozenUntil }.maxOrNull()

    companion object {
        private val EPOCH = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        val NONE = Evaluation(emptyList(), emptySet(), EPOCH, EPOCH)
    }
}

object RuleEvaluator {
    fun evaluate(
        rules: List<Rule>,
        usageByPackage: Map<String, Long>,
        mediaPackages: Set<String>,
        now: ZonedDateTime,
        resetHour: Int,
        warnMillis: Long = 0,
    ): Evaluation {
        val dayStart = UsageDay.startOf(now, resetHour)
        val nextReset = UsageDay.nextReset(now, resetHour)
        val statuses = rules.map { rule ->
            RuleStatus(
                rule = rule,
                usedMillis = usageByPackage.filterKeys(rule::appliesTo).values.sum(),
                limitMillis = rule.limitMinutes
                    ?.takeIf { dayStart.dayOfWeek in rule.limitDays }
                    ?.let { it * 60_000L },
                activeWindowEnd = rule.windows.mapNotNull { windowEnd(it, now) }.maxOrNull(),
                nextReset = nextReset,
                warnMillis = warnMillis,
            )
        }
        return Evaluation(statuses, mediaPackages, dayStart, nextReset)
    }

    /** When [window] ends if it is active at [now], else null. */
    fun windowEnd(window: BlockWindow, now: ZonedDateTime): ZonedDateTime? {
        val minute = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        fun at(date: LocalDate, m: Int) = date.atStartOfDay().plusMinutes(m.toLong()).atZone(now.zone)
        return if (window.startMinute < window.endMinute) {
            if (now.dayOfWeek in window.days && minute >= window.startMinute && minute < window.endMinute) {
                at(today, window.endMinute)
            } else {
                null
            }
        } else when {
            // Overnight: either we're in the evening part that started today...
            now.dayOfWeek in window.days && minute >= window.startMinute -> at(today.plusDays(1), window.endMinute)
            // ...or the morning part of one that started yesterday.
            now.dayOfWeek.minus(1) in window.days && minute < window.endMinute -> at(today, window.endMinute)
            else -> null
        }
    }
}
