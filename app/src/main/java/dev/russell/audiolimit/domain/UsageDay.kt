package dev.russell.audiolimit.domain

import java.time.ZonedDateTime

/** A usage "day" runs from resetHour:00 local time to the same time the next day. */
object UsageDay {
    fun startOf(now: ZonedDateTime, resetHour: Int): ZonedDateTime {
        val todayReset = now.toLocalDate().atTime(resetHour, 0).atZone(now.zone)
        return if (now.isBefore(todayReset)) {
            now.toLocalDate().minusDays(1).atTime(resetHour, 0).atZone(now.zone)
        } else {
            todayReset
        }
    }

    fun nextReset(now: ZonedDateTime, resetHour: Int): ZonedDateTime =
        startOf(now, resetHour).toLocalDate().plusDays(1).atTime(resetHour, 0).atZone(now.zone)
}
