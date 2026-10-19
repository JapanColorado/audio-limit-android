package dev.russell.audiolimit.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageDayTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), zone)

    @Test
    fun `after reset hour the day started today`() {
        assertThat(UsageDay.startOf(at(2026, 10, 16, 10), 4)).isEqualTo(at(2026, 10, 16, 4))
        assertThat(UsageDay.nextReset(at(2026, 10, 16, 10), 4)).isEqualTo(at(2026, 10, 17, 4))
    }

    @Test
    fun `before reset hour the day started yesterday`() {
        assertThat(UsageDay.startOf(at(2026, 10, 16, 2, 30), 4)).isEqualTo(at(2026, 10, 15, 4))
        assertThat(UsageDay.nextReset(at(2026, 10, 16, 2, 30), 4)).isEqualTo(at(2026, 10, 16, 4))
    }

    @Test
    fun `exactly at reset starts a new day`() {
        assertThat(UsageDay.startOf(at(2026, 10, 16, 4), 4)).isEqualTo(at(2026, 10, 16, 4))
    }

    @Test
    fun `midnight reset`() {
        assertThat(UsageDay.startOf(at(2026, 10, 16, 0, 1), 0)).isEqualTo(at(2026, 10, 16, 0))
    }
}
