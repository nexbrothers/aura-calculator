package app.aurio.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aurio_settings")

data class Settings(
    /** 0 = not started … 5 = finished. Lets onboarding resume after the app is closed. */
    val onboardingStep: Int = 0,
    val name: String = "",
    val focusReminders: Boolean = true,
    val limitWarnings: Boolean = true,
    val dailyRecap: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val reduceMotion: Boolean = false,
    val ambientSound: Boolean = false,
    val defaultFocusMinutes: Int = 25,
    val cloudBackup: Boolean = false,
) {
    val onboardingDone get() = onboardingStep >= 5
}

class SettingsStore(private val context: Context) {
    private object K {
        val step = intPreferencesKey("onboarding_step")
        val name = stringPreferencesKey("name")
        val focusReminders = booleanPreferencesKey("focus_reminders")
        val limitWarnings = booleanPreferencesKey("limit_warnings")
        val dailyRecap = booleanPreferencesKey("daily_recap")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val ambient = booleanPreferencesKey("ambient_sound")
        val focusMinutes = intPreferencesKey("default_focus_minutes")
        val cloud = booleanPreferencesKey("cloud_backup")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            onboardingStep = p[K.step] ?: d.onboardingStep,
            name = p[K.name] ?: d.name,
            focusReminders = p[K.focusReminders] ?: d.focusReminders,
            limitWarnings = p[K.limitWarnings] ?: d.limitWarnings,
            dailyRecap = p[K.dailyRecap] ?: d.dailyRecap,
            quietStartMinute = p[K.quietStart] ?: d.quietStartMinute,
            quietEndMinute = p[K.quietEnd] ?: d.quietEndMinute,
            reduceMotion = p[K.reduceMotion] ?: d.reduceMotion,
            ambientSound = p[K.ambient] ?: d.ambientSound,
            defaultFocusMinutes = p[K.focusMinutes] ?: d.defaultFocusMinutes,
            cloudBackup = p[K.cloud] ?: d.cloudBackup,
        )
    }

    suspend fun setOnboardingStep(step: Int) = context.dataStore.edit { it[K.step] = step }
    suspend fun setName(name: String) = context.dataStore.edit { it[K.name] = name.trim().take(30) }
    suspend fun setFocusReminders(v: Boolean) = context.dataStore.edit { it[K.focusReminders] = v }
    suspend fun setLimitWarnings(v: Boolean) = context.dataStore.edit { it[K.limitWarnings] = v }
    suspend fun setDailyRecap(v: Boolean) = context.dataStore.edit { it[K.dailyRecap] = v }
    suspend fun setQuietHours(startMin: Int, endMin: Int) = context.dataStore.edit {
        it[K.quietStart] = startMin.coerceIn(0, 1439); it[K.quietEnd] = endMin.coerceIn(0, 1439)
    }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[K.reduceMotion] = v }
    suspend fun setAmbientSound(v: Boolean) = context.dataStore.edit { it[K.ambient] = v }
    suspend fun setDefaultFocusMinutes(m: Int) = context.dataStore.edit { it[K.focusMinutes] = m.coerceIn(5, 90) }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
