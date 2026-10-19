package dev.russell.audiolimit.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.russell.audiolimit.MainActivity
import dev.russell.audiolimit.R
import dev.russell.audiolimit.domain.RuleStatus
import dev.russell.audiolimit.util.formatDuration
import dev.russell.audiolimit.util.formatUntil
import java.time.ZonedDateTime

/** Limit warnings and "paused" notices. Each distinct notice is posted at most once per process. */
class Notifier(private val context: Context) {
    private val sent = mutableSetOf<String>()

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Limits & locks", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun warn(status: RuleStatus, dayStart: ZonedDateTime) {
        val remaining = status.remainingMillis ?: return
        post(
            key = "warn:${status.rule.id}:${dayStart.toEpochSecond()}",
            title = "${status.rule.name}: ${formatDuration(remaining)} left",
            text = "Audio from these apps will be paused when the limit is reached.",
        )
    }

    fun paused(appLabel: String, pkg: String, status: RuleStatus) {
        val until = status.blockedUntil ?: return
        post(
            key = "pause:$pkg:${until.toEpochSecond()}",
            title = "Paused $appLabel",
            text = "${status.rule.name} — locked until ${formatUntil(until)}",
        )
    }

    @Synchronized
    private fun post(key: String, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (!sent.add(key)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL = "limits"
    }
}
