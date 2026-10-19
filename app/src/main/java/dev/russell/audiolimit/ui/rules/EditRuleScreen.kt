package dev.russell.audiolimit.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.audiolimit.domain.Rule
import dev.russell.audiolimit.domain.RuleKind
import dev.russell.audiolimit.ui.appViewModel
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.ui.components.DayChips
import dev.russell.audiolimit.ui.components.describeDays
import dev.russell.audiolimit.util.formatDuration
import dev.russell.audiolimit.util.formatMinuteOfDay
import dev.russell.audiolimit.util.formatUntil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleScreen(ruleId: Long, presetPackage: String?, onDone: () -> Unit) {
    val vm = appViewModel { EditRuleViewModel(it, ruleId, presetPackage) }
    val lockedUntil by vm.lockedUntil.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        val rule = vm.draft ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            lockedUntil?.let { until ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.size(12.dp))
                        Text("Locked until ${formatUntil(until)}. You can make this rule stricter, but not looser.")
                    }
                }
            }
            OutlinedTextField(
                value = rule.name,
                onValueChange = { name -> vm.update { it.copy(name = name) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KindSelector(rule.kind) { kind ->
                vm.update { it.copy(kind = kind, packages = if (kind == RuleKind.APP) it.packages.take(1).toSet() else it.packages) }
            }
            AppsSection(vm, rule)
            HorizontalDivider()
            LimitSection(rule, vm::update)
            HorizontalDivider()
            WindowsSection(rule, vm::update)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.save(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete rule?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(kind: RuleKind, onChange: (RuleKind) -> Unit) {
    val options = listOf(RuleKind.APP to "One app", RuleKind.GROUP to "Group", RuleKind.TOTAL to "All audio")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = kind == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsSection(vm: EditRuleViewModel, rule: Rule) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (rule.kind) {
                RuleKind.APP -> "App"
                RuleKind.GROUP -> "Apps in this group (they share one budget)"
                RuleKind.TOTAL -> "Excluded apps (never counted or blocked by this rule)"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (pkg in rule.packages.sortedBy { vm.label(it).lowercase() }) {
                InputChip(
                    selected = false,
                    onClick = { vm.update { it.copy(packages = it.packages - pkg) } },
                    label = { Text(vm.label(pkg)) },
                    avatar = { AppIcon(pkg, Modifier.size(20.dp)) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp)) },
                )
            }
            AssistChip(
                onClick = { picking = true },
                label = { Text(if (rule.kind == RuleKind.APP && rule.packages.isNotEmpty()) "Change" else "Choose apps") },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
            )
        }
    }
    if (picking) {
        AppPickerDialog(
            apps = vm.apps,
            mediaPackages = vm.mediaPackages,
            label = vm::label,
            initial = rule.packages,
            single = rule.kind == RuleKind.APP,
            onDismiss = { picking = false },
            onConfirm = { picked ->
                picking = false
                vm.update {
                    val name = if (it.name.isBlank() && it.kind == RuleKind.APP && picked.size == 1) vm.label(picked.first()) else it.name
                    it.copy(packages = picked, name = name)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitSection(rule: Rule, update: ((Rule) -> Rule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily limit", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Resets each day at the reset time in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = rule.limitMinutes != null,
                onCheckedChange = { on -> update { it.copy(limitMinutes = if (on) 60 else null) } },
            )
        }
        val minutes = rule.limitMinutes ?: return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { update { it.copy(limitMinutes = (minutes - STEP).coerceAtLeast(STEP)) } }) {
                Icon(Icons.Default.Remove, "Less")
            }
            Text(
                formatDuration(minutes * 60_000L),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { update { it.copy(limitMinutes = minutes + STEP) } }) {
                Icon(Icons.Default.Add, "More")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (preset in listOf(15, 30, 60, 90, 120, 180)) {
                OutlinedButton(onClick = { update { it.copy(limitMinutes = preset) } }) {
                    Text(formatDuration(preset * 60_000L))
                }
            }
        }
        Text("Applies on", style = MaterialTheme.typography.bodyMedium)
        DayChips(rule.limitDays, onChange = { days -> update { it.copy(limitDays = days) } })
    }
}

@Composable
private fun WindowsSection(rule: Rule, update: ((Rule) -> Rule) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Block windows", style = MaterialTheme.typography.titleSmall)
        Text(
            "Times when these apps are blocked regardless of usage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (window in rule.windows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMinuteOfDay(window.startMinute)} – ${formatMinuteOfDay(window.endMinute)} · ${describeDays(window.days)}",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { update { it.copy(windows = it.windows - window) } }) {
                    Icon(Icons.Default.Delete, "Remove window")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.size(8.dp))
            Text("Add block window")
        }
    }
    if (adding) {
        WindowDialog(
            onDismiss = { adding = false },
            onConfirm = { window ->
                adding = false
                update { it.copy(windows = it.windows + window) }
            },
        )
    }
}

private const val STEP = 5
