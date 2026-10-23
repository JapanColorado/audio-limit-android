package dev.russell.audiolimit.ui.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.russell.audiolimit.AppGraph
import dev.russell.audiolimit.data.AppEntry
import dev.russell.audiolimit.domain.Rule
import dev.russell.audiolimit.domain.RuleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

class EditRuleViewModel(
    private val graph: AppGraph,
    private val ruleId: Long,
    presetPackage: String?,
) : ViewModel() {
    var draft by mutableStateOf<Rule?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var mediaPackages by mutableStateOf<Set<String>>(emptySet())
        private set

    val isNew get() = ruleId == 0L

    /** Set while this rule is blocking or warning; the editor then only accepts stricter changes. */
    val lockedUntil: StateFlow<ZonedDateTime?> = graph.enforcer.evaluation
        .map { it.statusOf(ruleId)?.frozenUntil }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            draft = if (isNew) {
                Rule(
                    name = presetPackage?.let { graph.apps.label(it) } ?: "",
                    kind = RuleKind.APP,
                    packages = setOfNotNull(presetPackage),
                    limitMinutes = 60,
                )
            } else {
                graph.rules.get(ruleId)
            }
            mediaPackages = graph.usage.mediaPackages()
            apps = withContext(Dispatchers.IO) { graph.apps.launchableApps() }
        }
    }

    fun label(pkg: String) = graph.apps.label(pkg)

    fun update(transform: (Rule) -> Rule) {
        draft = draft?.let(transform)
        error = null
    }

    fun save(onDone: () -> Unit) {
        val rule = draft ?: return
        viewModelScope.launch {
            error = graph.guard.saveRule(rule)
            if (error == null) onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            error = graph.guard.deleteRule(ruleId)
            if (error == null) onDone()
        }
    }
}
