package dev.russell.audiolimit.util

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        totalMinutes > 0 -> "${m}m"
        else -> "<1m"
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "4:00 AM", or "Tue 4:00 AM" when [time] isn't today. */
fun formatUntil(time: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val clock = time.format(timeFormat)
    return if (time.toLocalDate() == now.toLocalDate()) clock
    else "${time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $clock"
}

/** Minutes since midnight as a localized clock time. */
fun formatMinuteOfDay(minute: Int): String =
    java.time.LocalTime.of(minute / 60 % 24, minute % 60).format(timeFormat)
