package dev.russell.audiolimit.tracking

import dev.russell.audiolimit.data.db.UsageDao
import dev.russell.audiolimit.data.db.UsageSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns "which packages are playing" into usage_segment rows: one open row per playing package,
 * whose end is flushed every [FLUSH_MS] and on stop.
 */
class PlaybackTracker(private val dao: UsageDao, private val scope: CoroutineScope) {
    private val _playing = MutableStateFlow<Set<String>>(emptySet())
    val playing: StateFlow<Set<String>> = _playing.asStateFlow()

    private val mutex = Mutex()
    /** Package -> id of its open segment row. Guarded by [mutex]. */
    private val open = mutableMapOf<String, Long>()
    private var ticker: Job? = null

    /** Report the full set of packages playing right now. */
    fun setPlaying(packages: Set<String>) {
        _playing.value = packages
        // Reconcile against the latest value, not [packages], so out-of-order launches can't regress.
        scope.launch { mutex.withLock { reconcile(_playing.value) } }
    }

    private suspend fun reconcile(target: Set<String>) {
        val now = System.currentTimeMillis()
        for (pkg in open.keys - target) dao.extend(open.remove(pkg)!!, now)
        for (pkg in target - open.keys) {
            open[pkg] = dao.insert(UsageSegment(packageName = pkg, startMillis = now, endMillis = now))
        }
        if (open.isEmpty()) {
            ticker?.cancel()
            ticker = null
        } else if (ticker == null) {
            ticker = scope.launch {
                while (true) {
                    delay(FLUSH_MS)
                    mutex.withLock {
                        val t = System.currentTimeMillis()
                        open.values.forEach { dao.extend(it, t) }
                    }
                }
            }
        }
    }

    private companion object {
        const val FLUSH_MS = 10_000L
    }
}
