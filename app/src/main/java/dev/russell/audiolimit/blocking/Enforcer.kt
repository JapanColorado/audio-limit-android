package dev.russell.audiolimit.blocking

import dev.russell.audiolimit.data.RuleRepository
import dev.russell.audiolimit.data.SettingsRepository
import dev.russell.audiolimit.data.UsageRepository
import dev.russell.audiolimit.domain.Evaluation
import dev.russell.audiolimit.domain.RuleEvaluator
import dev.russell.audiolimit.domain.UsageDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Keeps [evaluation] — which rules are blocking right now — current. It re-evaluates whenever rules,
 * settings or usage change, and on a ticker so block windows and the daily reset take effect on time.
 */
class Enforcer(
    private val rules: RuleRepository,
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _evaluation = MutableStateFlow(Evaluation.NONE)
    val evaluation: StateFlow<Evaluation> = _evaluation.asStateFlow()
    private val mutex = Mutex()

    fun start() {
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        scope.launch {
            merge(ticker, rules.observeAll().map { }, settings.flow.map { }, usage.changes().map { })
                .conflate()
                .collect { refresh() }
        }
    }

    /** Re-evaluates immediately and returns the fresh result. */
    suspend fun refresh(): Evaluation = mutex.withLock {
        val now = ZonedDateTime.now()
        val current = settings.current()
        val resetHour = current.resetHour
        val dayStart = UsageDay.startOf(now, resetHour)
        val nowMs = now.toInstant().toEpochMilli()
        RuleEvaluator.evaluate(
            rules = rules.getAll(),
            usageByPackage = usage.totals(dayStart.toInstant().toEpochMilli(), nowMs + 1),
            mediaPackages = usage.mediaPackages(),
            now = now,
            resetHour = resetHour,
            warnMillis = current.warnMinutes * 60_000L,
        ).also { _evaluation.value = it }
    }

    private companion object {
        const val TICK_MS = 15_000L
    }
}
