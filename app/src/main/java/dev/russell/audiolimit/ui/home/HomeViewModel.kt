package dev.russell.audiolimit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.audiolimit.AppGraph
import dev.russell.audiolimit.domain.RuleStatus
import dev.russell.audiolimit.domain.UsageDay
import dev.russell.audiolimit.ui.ticker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime

data class UsageRow(
    val pkg: String,
    val label: String,
    val usedMillis: Long,
    val playing: Boolean,
    /** The rule locking this app right now, if any. */
    val block: RuleStatus?,
    /** The limit with the least time left that covers this app, if any. */
    val tightest: RuleStatus?,
)

data class HomeState(
    val dayStart: ZonedDateTime? = null,
    val totalMillis: Long = 0,
    val rows: List<UsageRow> = emptyList(),
    val locks: List<RuleStatus> = emptyList(),
)

class HomeViewModel(private val graph: AppGraph) : ViewModel() {
    val state: StateFlow<HomeState> =
        combine(graph.enforcer.evaluation, graph.tracker.playing, ticker(REFRESH_MS)) { evaluation, playing, _ ->
            evaluation to playing
        }.map { (evaluation, playing) ->
            val now = ZonedDateTime.now()
            val dayStart = UsageDay.startOf(now, graph.settings.current().resetHour)
            val totals = graph.usage.totals(dayStart.toInstant().toEpochMilli(), now.toInstant().toEpochMilli() + 1)
            val rows = (totals.keys + playing).map { pkg ->
                UsageRow(
                    pkg = pkg,
                    label = graph.apps.label(pkg),
                    usedMillis = totals[pkg] ?: 0,
                    playing = pkg in playing,
                    block = evaluation.blockFor(pkg),
                    tightest = evaluation.statuses
                        .filter { it.rule.appliesTo(pkg) && it.remainingMillis != null }
                        .minByOrNull { it.remainingMillis!! },
                )
            }.sortedByDescending { it.usedMillis }
            HomeState(dayStart, totals.values.sum(), rows, evaluation.statuses.filter { it.isBlocking })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private companion object {
        const val REFRESH_MS = 5_000L
    }
}
