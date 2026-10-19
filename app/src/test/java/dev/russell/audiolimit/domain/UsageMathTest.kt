package dev.russell.audiolimit.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsageMathTest {
    @Test
    fun `clips spans to the range and sums per package`() {
        val spans = listOf(
            Span("a", 0, 100), // straddles the start: 50 counted
            Span("a", 120, 150), // inside: 30
            Span("b", 180, 300), // straddles the end: 20
            Span("c", 300, 400), // outside
        )
        assertThat(UsageMath.totals(spans, 50, 200)).containsExactly("a", 80L, "b", 20L)
    }

    @Test
    fun `backwards span from a clock change counts zero`() {
        assertThat(UsageMath.totals(listOf(Span("a", 100, 50)), 0, 200)).isEmpty()
    }
}
