package dev.russell.audiolimit.tracking

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import dev.russell.audiolimit.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Exists for its notification-listener grant, which is what lets us watch every app's media sessions.
 * Reports playing packages to [PlaybackTracker] and pauses any that a rule is blocking.
 */
class MediaListenerService : NotificationListenerService() {
    private val graph by lazy { appGraph }
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    /** Controllers we've already asked to pause; if they keep playing we escalate to stop(). */
    private val pauseAttempted = mutableSetOf<MediaController>()
    private var scope: CoroutineScope? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, MediaListenerService::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
        setControllers(msm.getActiveSessions(component))
        scope = MainScope().also { s ->
            s.launch { graph.enforcer.evaluation.collect { enforce() } }
        }
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        scope?.cancel()
        scope = null
        setControllers(emptyList())
    }

    private fun setControllers(controllers: List<MediaController>) {
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        pauseAttempted.clear()
        for (controller in controllers) {
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = update()
                override fun onSessionDestroyed() = update()
            }
            controller.registerCallback(callback, handler)
            callbacks[controller] = callback
        }
        update()
    }

    private fun update() {
        graph.tracker.setPlaying(callbacks.keys.filter { it.isPlaying }.map { it.packageName }.toSet())
        enforce()
    }

    private fun enforce() {
        val evaluation = graph.enforcer.evaluation.value
        val warnMillis = graph.settings.flow.value.warnMinutes * 60_000L
        pauseAttempted.retainAll { it.isPlaying }
        for (controller in callbacks.keys.filter { it.isPlaying }) {
            val pkg = controller.packageName
            val block = evaluation.blockFor(pkg)
            if (block == null) {
                if (warnMillis == 0L) continue
                evaluation.statuses
                    .filter { it.rule.appliesTo(pkg) && (it.remainingMillis ?: Long.MAX_VALUE) in 1..warnMillis }
                    .forEach { graph.notifier.warn(it, evaluation.dayStart) }
                continue
            }
            if (pauseAttempted.add(controller)) controller.transportControls.pause()
            else controller.transportControls.stop()
            graph.notifier.paused(graph.apps.label(pkg), pkg, block)
        }
    }

    private val MediaController.isPlaying get() = playbackState?.state == PlaybackState.STATE_PLAYING
}
