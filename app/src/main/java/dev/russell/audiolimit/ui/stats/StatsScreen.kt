package dev.russell.audiolimit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.russell.audiolimit.AppGraph
import dev.russell.audiolimit.domain.UsageDay
import dev.russell.audiolimit.domain.UsageMath
import dev.russell.audiolimit.ui.appViewModel
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.ui.ticker
import dev.russell.audiolimit.util.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class AppTotal(val pkg: String, val label: String, val millis: Long)
data class DayStats(val title: String, val totalMillis: Long, val apps: List<AppTotal>)
data class StatsState(val week: List<AppTotal> = emptyList(), val days: List<DayStats> = emptyList())

class StatsViewModel(private val graph: AppGraph) : ViewModel() {
    val state: StateFlow<StatsState> = ticker(30_000).map { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    private suspend fun load(): StatsState {
        val now = ZonedDateTime.now()
        val today = UsageDay.startOf(now, graph.settings.current().resetHour)
        // Day i spans [today - i days, today - (i-1) days); today ends now.
        val starts = (0 until DAYS).map { today.minusDays(it.toLong()) }
        val nowMs = now.toInstant().toEpochMilli() + 1
        val spans = graph.usage.spans(starts.last().toInstant().toEpochMilli(), nowMs)
        fun totals(from: Long, to: Long) = UsageMath.totals(spans, from, to)
            .map { (pkg, ms) -> AppTotal(pkg, graph.apps.label(pkg), ms) }
            .sortedByDescending { it.millis }
        val days = starts.mapIndexed { i, start ->
            val end = if (i == 0) nowMs else starts[i - 1].toInstant().toEpochMilli()
            val apps = totals(start.toInstant().toEpochMilli(), end)
            DayStats(
                title = when (i) {
                    0 -> "Today"
                    1 -> "Yesterday"
                    else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                },
                totalMillis = apps.sumOf { it.millis },
                apps = apps,
            )
        }
        return StatsState(totals(starts.last().toInstant().toEpochMilli(), nowMs), days)
    }

    private companion object {
        const val DAYS = 7
    }
}

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("History", style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Last 7 days · ${formatDuration(state.week.sumOf { it.millis })}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.week.isEmpty()) Text("No audio recorded yet.")
                    val max = state.week.maxOfOrNull { it.millis } ?: 1
                    for (app in state.week) AppBar(app, max)
                }
            }
        }
        items(state.days, key = { it.title }) { day ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${day.title} · ${formatDuration(day.totalMillis)}", style = MaterialTheme.typography.titleMedium)
                    val max = day.apps.maxOfOrNull { it.millis } ?: 1
                    for (app in day.apps) AppBar(app, max)
                }
            }
        }
    }
}

@Composable
private fun AppBar(app: AppTotal, max: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.pkg, Modifier.size(28.dp))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatDuration(app.millis), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth((app.millis.toFloat() / max).coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}
