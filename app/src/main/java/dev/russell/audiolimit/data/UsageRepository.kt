package dev.russell.audiolimit.data

import dev.russell.audiolimit.data.db.UsageDao
import dev.russell.audiolimit.domain.Span
import dev.russell.audiolimit.domain.UsageMath
import kotlinx.coroutines.flow.Flow

class UsageRepository(private val dao: UsageDao) {
    suspend fun spans(from: Long, to: Long): List<Span> =
        dao.overlapping(from, to).map { Span(it.packageName, it.startMillis, it.endMillis) }

    suspend fun totals(from: Long, to: Long): Map<String, Long> = UsageMath.totals(spans(from, to), from, to)

    suspend fun mediaPackages(): Set<String> = dao.mediaPackages().toSet()

    fun changes(): Flow<Long?> = dao.observeLatestEnd()
}
