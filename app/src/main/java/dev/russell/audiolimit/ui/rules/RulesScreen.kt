package dev.russell.audiolimit.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.audiolimit.appGraph
import dev.russell.audiolimit.domain.RuleKind
import dev.russell.audiolimit.domain.RuleStatus
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.ui.components.describeDays
import dev.russell.audiolimit.util.formatDuration
import dev.russell.audiolimit.util.formatMinuteOfDay
import dev.russell.audiolimit.util.formatUntil

@Composable
fun RulesScreen(onOpen: (Long) -> Unit) {
    val graph = LocalContext.current.appGraph
    val evaluation by graph.enforcer.evaluation.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Rules", style = MaterialTheme.typography.headlineMedium) }
            if (evaluation.statuses.isEmpty()) item {
                Text(
                    "No rules yet. Add a daily limit for one app, a group of apps, or all audio — " +
                        "and optionally times of day when they're blocked outright.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(evaluation.statuses, key = { it.rule.id }) { status -> RuleCard(status) { onOpen(status.rule.id) } }
        }
        ExtendedFloatingActionButton(
            onClick = { onOpen(0) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("New rule") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun RuleCard(status: RuleStatus, onClick: () -> Unit) {
    val rule = status.rule
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (status.isBlocking) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "until ${formatUntil(status.blockedUntil!!)}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (rule.kind) {
                    RuleKind.TOTAL -> Text(
                        if (rule.packages.isEmpty()) "All audio" else "All audio, except",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Unit
                }
                rule.packages.take(MAX_ICONS).forEach {
                    Spacer(Modifier.size(4.dp))
                    AppIcon(it, Modifier.size(24.dp))
                }
                if (rule.packages.size > MAX_ICONS) Text(" +${rule.packages.size - MAX_ICONS}")
            }
            val limit = status.limitMillis
            if (rule.limitMinutes != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (limit != null) "${formatDuration(status.usedMillis)} of ${formatDuration(limit)} today"
                    else "No limit today (${describeDays(rule.limitDays)} only)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (limit != null && limit > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (status.usedMillis.toFloat() / limit).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            for (window in rule.windows) {
                Text(
                    "Blocked ${formatMinuteOfDay(window.startMinute)} – ${formatMinuteOfDay(window.endMinute)} · ${describeDays(window.days)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_ICONS = 6
