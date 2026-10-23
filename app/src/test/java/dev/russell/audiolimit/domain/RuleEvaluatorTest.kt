package dev.russell.audiolimit.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEvaluatorTest {
    private val zone = ZoneId.of("UTC")
    private fun at(d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(LocalDateTime.of(2026, 10, d, h, mi), zone)
    private val min = 60_000L
    private val all = DayOfWeek.entries.toSet()

    // 2026-10-16 is a Friday.
    private val friNoon = at(16, 12)

    private fun eval(rules: List<Rule>, usage: Map<String, Long>, now: ZonedDateTime = friNoon, media: Set<String> = usage.keys) =
        RuleEvaluator.evaluate(rules, usage, media, now, resetHour = 4)

    @Test
    fun `app limit blocks once reached and holds until next reset`() {
        val rule = Rule(1, "Spotify", RuleKind.APP, setOf("spotify"), limitMinutes = 60)
        val under = eval(listOf(rule), mapOf("spotify" to 59 * min))
        assertThat(under.blockFor("spotify")).isNull()
        assertThat(under.statusOf(1)!!.remainingMillis).isEqualTo(min)

        val over = eval(listOf(rule), mapOf("spotify" to 60 * min))
        assertThat(over.blockFor("spotify")!!.blockedUntil).isEqualTo(at(17, 4))
        assertThat(over.blockFor("youtube")).isNull()
    }

    @Test
    fun `group shares one budget`() {
        val rule = Rule(1, "Podcasts", RuleKind.GROUP, setOf("a", "b"), limitMinutes = 60)
        val e = eval(listOf(rule), mapOf("a" to 40 * min, "b" to 20 * min, "c" to 500 * min))
        assertThat(e.statusOf(1)!!.usedMillis).isEqualTo(60 * min)
        assertThat(e.blockFor("a")).isNotNull()
        assertThat(e.blockFor("b")).isNotNull()
        assertThat(e.blockFor("c")).isNull()
    }

    @Test
    fun `total rule excludes listed packages and only screen-blocks media apps`() {
        val rule = Rule(1, "All", RuleKind.TOTAL, setOf("maps"), limitMinutes = 60)
        val e = eval(listOf(rule), mapOf("a" to 50 * min, "maps" to 100 * min, "b" to 10 * min))
        assertThat(e.statusOf(1)!!.usedMillis).isEqualTo(60 * min)
        assertThat(e.blockFor("maps")).isNull()
        assertThat(e.blockFor("never-played")).isNotNull()
        assertThat(e.screenBlockFor("never-played")).isNull()
        assertThat(e.screenBlockFor("a")).isNotNull()
    }

    @Test
    fun `limit days are judged by the weekday the usage day started`() {
        val rule = Rule(1, "Weekdays", RuleKind.APP, setOf("a"), limitMinutes = 10, limitDays = setOf(FRIDAY))
        // Sat 02:00 still belongs to Friday's usage day.
        assertThat(eval(listOf(rule), mapOf("a" to 20 * min), now = at(17, 2)).blockFor("a")).isNotNull()
        assertThat(eval(listOf(rule), mapOf("a" to 20 * min), now = at(17, 5)).blockFor("a")).isNull()
    }

    @Test
    fun `same-day window`() {
        val w = BlockWindow(9 * 60, 17 * 60, setOf(FRIDAY))
        assertThat(RuleEvaluator.windowEnd(w, friNoon)).isEqualTo(at(16, 17))
        assertThat(RuleEvaluator.windowEnd(w, at(16, 17))).isNull()
        assertThat(RuleEvaluator.windowEnd(w, at(15, 12))).isNull()
    }

    @Test
    fun `overnight window covers the evening and the next morning`() {
        val w = BlockWindow(23 * 60, 7 * 60, setOf(THURSDAY))
        assertThat(RuleEvaluator.windowEnd(w, at(15, 23, 30))).isEqualTo(at(16, 7))
        assertThat(RuleEvaluator.windowEnd(w, at(16, 6, 59))).isEqualTo(at(16, 7))
        assertThat(RuleEvaluator.windowEnd(w, at(16, 7))).isNull()
        // Friday evening isn't a start day.
        assertThat(RuleEvaluator.windowEnd(w, at(16, 23, 30))).isNull()
    }

    @Test
    fun `window-only rule blocks during the window`() {
        val rule = Rule(1, "Night", RuleKind.APP, setOf("a"), limitMinutes = null, windows = listOf(BlockWindow(11 * 60, 13 * 60, all)))
        val e = eval(listOf(rule), emptyMap())
        assertThat(e.blockFor("a")!!.blockedUntil).isEqualTo(at(16, 13))
        assertThat(e.anyLocked).isTrue()
    }

    @Test
    fun `longest block wins`() {
        val short = Rule(1, "Window", RuleKind.APP, setOf("a"), null, windows = listOf(BlockWindow(11 * 60, 13 * 60, all)))
        val long = Rule(2, "Limit", RuleKind.APP, setOf("a"), limitMinutes = 1)
        assertThat(eval(listOf(short, long), mapOf("a" to 5 * min)).blockFor("a")!!.rule.id).isEqualTo(2)
    }

    @Test
    fun `saturday not in mask means no limit`() {
        val rule = Rule(1, "x", RuleKind.APP, setOf("a"), limitMinutes = 1, limitDays = all - SATURDAY)
        assertThat(eval(listOf(rule), mapOf("a" to 5 * min), now = at(17, 12)).statusOf(1)!!.limitMillis).isNull()
    }

    @Test
    fun `rule freezes once inside the warning time and stays frozen until reset`() {
        val rule = Rule(1, "Spotify", RuleKind.APP, setOf("spotify"), limitMinutes = 60)
        fun status(used: Long, warn: Long = 5 * min) =
            RuleEvaluator.evaluate(listOf(rule), mapOf("spotify" to used), setOf("spotify"), friNoon, 4, warn).statusOf(1)!!

        assertThat(status(54 * min).isFrozen).isFalse()
        val warned = status(56 * min)
        assertThat(warned.isWarning).isTrue()
        assertThat(warned.isBlocking).isFalse()
        assertThat(warned.frozenUntil).isEqualTo(at(17, 4))
        assertThat(status(60 * min).frozenUntil).isEqualTo(at(17, 4))
        // Warnings off: no freeze until the limit itself is reached.
        assertThat(status(56 * min, warn = 0).isFrozen).isFalse()
    }
}
