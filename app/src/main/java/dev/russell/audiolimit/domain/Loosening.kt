package dev.russell.audiolimit.domain

/** While a rule is locked it may only get stricter. */
object Loosening {
    /** What [new] would relax relative to [old] (phrased to follow "you can't"), or null if it doesn't. */
    fun reason(old: Rule, new: Rule): String? = when {
        new.kind != old.kind -> "change the rule type"
        old.limitMinutes != null && (new.limitMinutes ?: Int.MAX_VALUE) > old.limitMinutes -> "raise or remove the limit"
        old.limitMinutes != null && !new.limitDays.containsAll(old.limitDays) -> "remove days from the limit"
        old.kind == RuleKind.TOTAL && !old.packages.containsAll(new.packages) -> "exclude more apps"
        old.kind != RuleKind.TOTAL && !new.packages.containsAll(old.packages) -> "remove apps"
        !new.windows.containsAll(old.windows) -> "remove or change a block window"
        else -> null
    }
}

/** Guards against rules that could never unlock (the only way out would be safe mode). */
object Permanence {
    /** True if [windows] together cover every minute of the week. */
    fun windowsCoverWholeWeek(windows: List<BlockWindow>): Boolean {
        val week = 7 * MINUTES_PER_DAY
        val covered = BooleanArray(week)
        for (w in windows) {
            val length = ((w.endMinute - w.startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY)
                .let { if (it == 0) MINUTES_PER_DAY else it }
            for (day in w.days) {
                val start = (day.value - 1) * MINUTES_PER_DAY + w.startMinute
                for (m in 0 until length) covered[(start + m) % week] = true
            }
        }
        return covered.all { it }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
