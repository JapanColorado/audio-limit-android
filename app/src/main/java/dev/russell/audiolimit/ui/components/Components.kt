package dev.russell.audiolimit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.russell.audiolimit.appGraph
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val bitmap = remember(pkg) { context.appGraph.apps.icon(pkg)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = modifier)
    else Icon(Icons.Default.Android, contentDescription = null, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in DayOfWeek.entries) {
            FilterChip(
                selected = day in selected,
                enabled = enabled,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

/** "Every day", "Weekdays", "Weekends", or "Mon, Wed, Fri". */
fun describeDays(days: Set<DayOfWeek>): String {
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when (days) {
        DayOfWeek.entries.toSet() -> "Every day"
        DayOfWeek.entries.toSet() - weekend -> "Weekdays"
        weekend -> "Weekends"
        else -> days.sorted().joinToString { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}
