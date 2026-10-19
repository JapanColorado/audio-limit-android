package dev.russell.audiolimit.domain

/** A stretch of playback by one app, in epoch millis. */
data class Span(val packageName: String, val start: Long, val end: Long)

object UsageMath {
    fun overlap(span: Span, from: Long, to: Long): Long =
        (minOf(span.end, to) - maxOf(span.start, from)).coerceAtLeast(0)

    /** Playback per package inside [from, to), clipping spans that straddle either edge. */
    fun totals(spans: List<Span>, from: Long, to: Long): Map<String, Long> =
        spans.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { overlap(it, from, to) } }
            .filterValues { it > 0 }
}
