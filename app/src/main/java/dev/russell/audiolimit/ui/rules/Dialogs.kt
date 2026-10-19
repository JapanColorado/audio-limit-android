package dev.russell.audiolimit.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.russell.audiolimit.data.AppEntry
import dev.russell.audiolimit.domain.BlockWindow
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.ui.components.DayChips
import java.time.DayOfWeek

/** Full-screen app chooser. Apps that have played audio are listed first. */
@Composable
fun AppPickerDialog(
    apps: List<AppEntry>,
    mediaPackages: Set<String>,
    label: (String) -> String,
    initial: Set<String>,
    single: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    // Include media apps with no launcher entry, and anything already selected.
    val all = remember(apps, mediaPackages) {
        val known = apps.map { it.packageName }.toSet()
        (apps + (mediaPackages + initial - known).map { AppEntry(it, label(it)) })
            .sortedWith(compareBy<AppEntry> { it.packageName !in mediaPackages }.thenBy { it.label.lowercase() })
    }
    val shown = all.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (single) "Choose an app" else "Choose apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!single) TextButton(onClick = { onConfirm(selected) }) { Text("Done") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps.isEmpty()) Text("Loading apps…", Modifier.padding(16.dp))
                LazyColumn {
                    items(shown, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (single) onConfirm(setOf(app.packageName))
                                else selected = if (checked) selected - app.packageName else selected + app.packageName
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label)
                                if (app.packageName in mediaPackages) {
                                    Text(
                                        "Has played audio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (single) RadioButton(selected = checked, onClick = null)
                            else Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowDialog(onDismiss: () -> Unit, onConfirm: (BlockWindow) -> Unit) {
    val start = rememberTimePickerState(initialHour = 23, initialMinute = 0)
    val end = rememberTimePickerState(initialHour = 7, initialMinute = 0)
    var days by remember { mutableStateOf(DayOfWeek.entries.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block window") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From")
                TimeInput(start)
                Text("To (earlier than From = runs past midnight)")
                TimeInput(end)
                Text("Starting on")
                DayChips(days, onChange = { days = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty() && (start.hour != end.hour || start.minute != end.minute),
                onClick = { onConfirm(BlockWindow(start.hour * 60 + start.minute, end.hour * 60 + end.minute, days)) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
