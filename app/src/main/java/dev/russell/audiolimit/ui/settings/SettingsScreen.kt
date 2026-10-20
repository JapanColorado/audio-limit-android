package dev.russell.audiolimit.ui.settings

import android.app.admin.DevicePolicyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.audiolimit.appGraph
import dev.russell.audiolimit.system.Permissions
import dev.russell.audiolimit.util.formatMinuteOfDay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val graph = context.appGraph
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickingHour by remember { mutableStateOf(false) }
    var pickingWarn by remember { mutableStateOf(false) }

    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val notificationAccess = remember(resumes) { Permissions.notificationAccess(context) }
    val accessibility = remember(resumes) { Permissions.accessibility(context) }
    var deviceAdmin by remember(resumes) { mutableStateOf(Permissions.deviceAdmin(context)) }

    fun guarded(block: suspend () -> String?) {
        scope.launch { block()?.let { snackbar.showSnackbar(it) } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
            ListItem(
                headlineContent = { Text("Daily reset") },
                supportingContent = { Text("Usage counts toward the day that started at ${formatMinuteOfDay(settings.resetHour * 60)}") },
                trailingContent = { Text(formatMinuteOfDay(settings.resetHour * 60)) },
                modifier = Modifier.clickable { pickingHour = true },
            )
            ListItem(
                headlineContent = { Text("Warn before limit") },
                supportingContent = { Text("Notification when a limit is about to run out while its apps are playing") },
                trailingContent = { Text(describeWarn(settings.warnMinutes)) },
                modifier = Modifier.clickable { pickingWarn = true },
            )
            ListItem(
                headlineContent = { Text("Protect settings while locked") },
                supportingContent = {
                    Text("Leave any system page that mentions Audio Limit (App info, accessibility, notification access, uninstall).")
                },
                trailingContent = {
                    Switch(settings.protectSettings, onCheckedChange = { on -> guarded { graph.guard.setProtectSettings(on) } })
                },
            )
            ListItem(
                headlineContent = { Text("Uninstall protection") },
                supportingContent = { Text("Device admin: must be deactivated before the app can be uninstalled.") },
                trailingContent = {
                    Switch(deviceAdmin, onCheckedChange = { on ->
                        if (on) {
                            context.startActivity(Permissions.deviceAdminIntent(context))
                        } else guarded {
                            graph.guard.anyLockMessage("turn off uninstall protection") ?: run {
                                context.getSystemService(DevicePolicyManager::class.java)
                                    .removeActiveAdmin(Permissions.adminComponent(context))
                                deviceAdmin = false
                                null
                            }
                        }
                    })
                },
            )
            HorizontalDivider()
            Text("Access", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
            AccessRow("Notification access", "Detects playback and pauses blocked apps", notificationAccess) {
                context.startActivity(Permissions.notificationAccessIntent(context))
            }
            AccessRow("Accessibility service", "Lock screen and settings protection", accessibility) {
                context.startActivity(Permissions.accessibilityIntent())
            }
            ListItem(
                headlineContent = { Text("App info") },
                supportingContent = { Text("If a toggle above is greyed out: ⋮ → Allow restricted settings") },
                modifier = Modifier.clickable { context.startActivity(Permissions.appInfoIntent(context)) },
            )
            HorizontalDivider()
            Text("How it works", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
            Text(
                "• Playback is detected through media sessions, the same thing that powers the media controls " +
                    "in your notification shade. Audio without a media session (some games, some web pages) isn't seen.\n" +
                    "• An “All audio” limit adds up each app's time, so two apps playing at once count twice.\n" +
                    "• While a rule is locked you can tighten it but not loosen or delete it. While any lock is active " +
                    "you can't change the reset time or turn off protections.\n" +
                    "• Escape hatch: rebooting into safe mode disables the accessibility service.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(Modifier.height(32.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (pickingWarn) {
        AlertDialog(
            onDismissRequest = { pickingWarn = false },
            title = { Text("Warn before limit") },
            text = {
                Column {
                    for (minutes in WARN_CHOICES) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                pickingWarn = false
                                guarded { graph.guard.setWarnMinutes(minutes) }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.warnMinutes == minutes, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(describeWarn(minutes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingWarn = false }) { Text("Cancel") } },
        )
    }

    if (pickingHour) {
        AlertDialog(
            onDismissRequest = { pickingHour = false },
            title = { Text("Reset at") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    items(24) { hour ->
                        Text(
                            formatMinuteOfDay(hour * 60),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxSize().clickable {
                                pickingHour = false
                                guarded { graph.guard.setResetHour(hour) }
                            }.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingHour = false }) { Text("Cancel") } },
        )
    }
}

private val WARN_CHOICES = listOf(0, 1, 2, 5, 10, 15, 30)

private fun describeWarn(minutes: Int) = if (minutes == 0) "Off" else "$minutes min before"

@Composable
private fun AccessRow(title: String, purpose: String, granted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(purpose) },
        trailingContent = {
            Text(
                if (granted) "On" else "Off",
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
