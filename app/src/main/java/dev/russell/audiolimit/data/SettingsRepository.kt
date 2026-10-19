package dev.russell.audiolimit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppSettings(
    /** Local hour (0-23) at which daily usage resets. */
    val resetHour: Int = 4,
    /** Bounce the user out of this app's system settings pages while any lock is active. */
    val protectSettings: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("settings")

/** Raw settings storage. Changes from the UI must go through LockGuard, which enforces locks. */
class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    val flow: StateFlow<AppSettings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun current(): AppSettings = context.dataStore.data.first().toSettings()

    suspend fun setResetHour(hour: Int) {
        context.dataStore.edit { it[RESET_HOUR] = hour }
    }

    suspend fun setProtectSettings(enabled: Boolean) {
        context.dataStore.edit { it[PROTECT_SETTINGS] = enabled }
    }

    private fun Preferences.toSettings() = AppSettings(
        resetHour = this[RESET_HOUR] ?: AppSettings().resetHour,
        protectSettings = this[PROTECT_SETTINGS] ?: AppSettings().protectSettings,
    )

    private companion object {
        val RESET_HOUR = intPreferencesKey("reset_hour")
        val PROTECT_SETTINGS = booleanPreferencesKey("protect_settings")
    }
}
