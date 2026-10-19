package dev.russell.audiolimit.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.audiolimit.system.Permissions
import dev.russell.audiolimit.ui.appViewModel
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.util.formatDuration
import dev.russell.audiolimit.util.formatUntil

@Composable
fun HomeScreen(onAddRule: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Special access can change in Settings while we're away, so re-check on every resume.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val notificationAccess = remember(resumes) { Permissions.notificationAccess(context) }
    val accessibility = remember(resumes) { Permissions.accessibility(context) }
    val deviceAdmin = remember(resumes) { Permissions.deviceAdmin(context) }
    var postNotifications by remember(resumes) { mutableStateOf(Permissions.postNotifications(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postNotifications = it
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.headlineMedium)
            Text(
                buildString {
                    append(formatDuration(state.totalMillis)).append(" of audio")
                    state.dayStart?.let { append(" since ").append(formatUntil(it)) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!notificationAccess) item {
            SetupCard(
                "Turn on notification access",
                "Required. This is how Audio Limit sees which apps are playing media and pauses them. " +
                    "If the toggle is greyed out, open App info → ⋮ → Allow restricted settings first.",
                "Open settings",
            ) { context.startActivity(Permissions.notificationAccessIntent(context)) }
        }
        if (!accessibility) item {
            SetupCard(
                "Turn on the accessibility service",
                "Shows the lock screen when you open a locked app, and guards Audio Limit's settings pages while locked.",
                "Open settings",
            ) { context.startActivity(Permissions.accessibilityIntent()) }
        }
        if (!deviceAdmin) item {
            SetupCard(
                "Protect against uninstall",
                "Optional. As a device admin, Audio Limit can't be uninstalled while a lock is active.",
                "Activate",
            ) { context.startActivity(Permissions.deviceAdminIntent(context)) }
        }
        if (!postNotifications) item {
            SetupCard(
                "Allow notifications",
                "Optional. Get a heads-up 5 minutes before a limit, and a notice when audio is paused.",
                "Allow",
            ) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (state.locks.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Locked now", style = MaterialTheme.typography.titleMedium)
                    }
                    for (lock in state.locks) {
                        Text("${lock.rule.name} — until ${formatUntil(lock.blockedUntil!!)}")
                    }
                }
            }
        }
        if (state.rows.isEmpty()) item {
            Text(
                "Nothing has played yet today.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        items(state.rows, key = { it.pkg }) { row -> UsageRowItem(row, onAddRule) }
    }
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun UsageRowItem(row: UsageRow, onAddRule: (String) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(row.pkg)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (row.playing) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                val limit = row.tightest
                Text(
                    buildString {
                        append(formatDuration(row.usedMillis))
                        if (limit != null) append(" · ${formatDuration(limit.remainingMillis!!)} left (${limit.rule.name})")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (limit?.limitMillis != null && limit.limitMillis > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (limit.usedMillis.toFloat() / limit.limitMillis).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            if (row.block != null) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.error)
            } else if (row.tightest == null) {
                TextButton(onClick = { onAddRule(row.pkg) }) { Text("Limit") }
            }
        }
    }
}
