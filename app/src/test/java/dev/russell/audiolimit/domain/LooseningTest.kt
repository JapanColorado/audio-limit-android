package dev.russell.audiolimit.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY

class LooseningTest {
    private val window = BlockWindow(23 * 60, 7 * 60, DayOfWeek.entries.toSet())
    private val group = Rule(1, "G", RuleKind.GROUP, setOf("a", "b"), limitMinutes = 60, windows = listOf(window))

    @Test
    fun `tightening is allowed`() {
        assertThat(Loosening.reason(group, group.copy(limitMinutes = 30))).isNull()
        assertThat(Loosening.reason(group, group.copy(packages = setOf("a", "b", "c")))).isNull()
        assertThat(Loosening.reason(group, group.copy(windows = listOf(window, BlockWindow(0, 60, setOf(MONDAY)))))).isNull()
        assertThat(Loosening.reason(group, group.copy(name = "Renamed"))).isNull()
    }

    @Test
    fun `loosening is refused`() {
        assertThat(Loosening.reason(group, group.copy(limitMinutes = 90))).isNotNull()
        assertThat(Loosening.reason(group, group.copy(limitMinutes = null))).isNotNull()
        assertThat(Loosening.reason(group, group.copy(packages = setOf("a")))).isNotNull()
        assertThat(Loosening.reason(group, group.copy(windows = emptyList()))).isNotNull()
        assertThat(Loosening.reason(group, group.copy(limitDays = setOf(MONDAY)))).isNotNull()
        assertThat(Loosening.reason(group, group.copy(kind = RuleKind.TOTAL))).isNotNull()
    }

    @Test
    fun `total rule exclusions work in reverse`() {
        val total = Rule(1, "T", RuleKind.TOTAL, setOf("maps"), limitMinutes = 60)
        assertThat(Loosening.reason(total, total.copy(packages = emptySet()))).isNull()
        assertThat(Loosening.reason(total, total.copy(packages = setOf("maps", "spotify")))).isNotNull()
    }

    @Test
    fun `adding a limit to a window-only rule is tightening`() {
        val windowOnly = group.copy(limitMinutes = null)
        assertThat(Loosening.reason(windowOnly, windowOnly.copy(limitMinutes = 120))).isNull()
    }
}

class PermanenceTest {
    private val all = DayOfWeek.entries.toSet()

    @Test
    fun `two half-day windows every day cover the week`() {
        assertThat(Permanence.windowsCoverWholeWeek(listOf(BlockWindow(0, 720, all), BlockWindow(720, 0, all)))).isTrue()
    }

    @Test
    fun `an overnight window every day leaves gaps`() {
        assertThat(Permanence.windowsCoverWholeWeek(listOf(BlockWindow(23 * 60, 7 * 60, all)))).isFalse()
    }

    @Test
    fun `missing one day leaves a gap`() {
        val windows = listOf(BlockWindow(0, 720, all - MONDAY), BlockWindow(720, 0, all - MONDAY))
        assertThat(Permanence.windowsCoverWholeWeek(windows)).isFalse()
    }

    @Test
    fun `overnight windows wrap from Sunday into Monday`() {
        // Every day 12:00 -> next day 12:00 minus one minute, plus the missing minute each day.
        val windows = listOf(BlockWindow(720, 719, all), BlockWindow(719, 720, all))
        assertThat(Permanence.windowsCoverWholeWeek(windows)).isTrue()
    }
}
