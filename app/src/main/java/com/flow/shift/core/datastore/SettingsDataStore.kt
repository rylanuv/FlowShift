package com.flow.shift.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val IS_STRICT_MODE_ENABLED = booleanPreferencesKey("is_strict_mode_enabled")
        val BLOCKING_MODE = stringPreferencesKey("blocking_mode")
        val BLOCK_TYPE = stringPreferencesKey("block_type")
        val LAST_MODE_DOWNGRADE_TIME = longPreferencesKey("last_mode_downgrade_time")
        val STRICT_CHALLENGE_TYPE = stringPreferencesKey("strict_challenge_type")
        val STRICT_CHALLENGE_AMOUNT = intPreferencesKey("strict_challenge_amount")
        val BREAK_DURATION_MINUTES = intPreferencesKey("break_duration_minutes")

        val DOWNGRADE_REQUEST_TIME = longPreferencesKey("downgrade_request_time")
        val DOWNGRADE_REQUEST_TARGET = stringPreferencesKey("downgrade_request_target")
        val AUTO_DOWNGRADE_AT_MIDNIGHT = booleanPreferencesKey("auto_downgrade_at_midnight")
        val LAST_AUTO_DOWNGRADE_TIME = longPreferencesKey("last_auto_downgrade_time")

        // Developer Mode
        val IS_DEVELOPER_MODE_ENABLED = booleanPreferencesKey("is_developer_mode_enabled")

        // Protection
        val STRICT_MODE_ON = booleanPreferencesKey("strict_mode_on")
        val EMERGENCY_PASSES_PER_DAY = intPreferencesKey("emergency_passes_per_day")
        val PREVENT_APP_UNINSTALL = booleanPreferencesKey("prevent_app_uninstall")
        val BLOCK_SETTINGS_ACCESS = booleanPreferencesKey("block_settings_access")
        val LOCK_DURING_FOCUS = booleanPreferencesKey("lock_during_focus")

        // Break Rules
        val BREAKS_PER_DAY = intPreferencesKey("breaks_per_day")
        val REQUIRE_CHALLENGE_FOR_BREAK = booleanPreferencesKey("require_challenge_for_break")
        val AUTO_RESUME_PROTECTION = booleanPreferencesKey("auto_resume_protection")

        // Intervention Rules
        val TRIGGER_AFTER_SCROLLING_MINUTES = intPreferencesKey("trigger_after_scrolling_minutes")
        val COOLDOWN_BETWEEN_INTERVENTIONS_MINUTES = intPreferencesKey("cooldown_between_interventions_minutes")
        val SHOW_WARNING_FIRST = booleanPreferencesKey("show_warning_first")
        val INSTANT_INTERVENTION = booleanPreferencesKey("instant_intervention")

        // Notifications
        val INTERVENTION_ALERTS = booleanPreferencesKey("intervention_alerts")
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val STREAK_REMINDERS = booleanPreferencesKey("streak_reminders")
        val MOTIVATIONAL_MESSAGES = booleanPreferencesKey("motivational_messages")

        // Appearance
        val THEME = stringPreferencesKey("theme")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")

        // Challenges
        val CHALLENGE_DIFFICULTY = stringPreferencesKey("challenge_difficulty")
        val RANDOMIZE_CHALLENGES = booleanPreferencesKey("randomize_challenges")
        val DAILY_SCREEN_TIME = stringPreferencesKey("daily_screen_time")
        val TARGET_SCREEN_TIME = stringPreferencesKey("target_screen_time")
        val EASY_MODE_WAIT_SECONDS = intPreferencesKey("easy_mode_wait_seconds")
        val FOCUS_MODE_UNTIL_MILLIS = longPreferencesKey("focus_mode_until_millis")
    }

    // ── Existing Flows ──
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_ONBOARDING_COMPLETED] ?: false
    }

    val blockingMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[BLOCKING_MODE] ?: "EASY"
    }

    val blockType: Flow<String> = dataStore.data.map { preferences ->
        preferences[BLOCK_TYPE] ?: "REELS"
    }

    val lastDowngradeTimeMillis: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_MODE_DOWNGRADE_TIME] ?: 0L
    }

    val strictChallengeType: Flow<String> = dataStore.data.map { preferences ->
        preferences[STRICT_CHALLENGE_TYPE] ?: "PUSHUPS"
    }

    val strictChallengeAmount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[STRICT_CHALLENGE_AMOUNT] ?: 15
    }

    val breakDurationMinutes: Flow<Int> = dataStore.data.map { preferences ->
        preferences[BREAK_DURATION_MINUTES] ?: 5
    }

    val easyModeWaitSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EASY_MODE_WAIT_SECONDS] ?: 90
    }

    val downgradeRequestTimeMillis: Flow<Long> = dataStore.data.map { preferences ->
        preferences[DOWNGRADE_REQUEST_TIME] ?: 0L
    }

    val downgradeRequestTarget: Flow<String?> = dataStore.data.map { preferences ->
        preferences[DOWNGRADE_REQUEST_TARGET]
    }

    val autoDowngradeAtMidnight: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_DOWNGRADE_AT_MIDNIGHT] ?: false
    }

    val lastAutoDowngradeTimeMillis: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_AUTO_DOWNGRADE_TIME] ?: 0L
    }

    val isDeveloperModeEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_DEVELOPER_MODE_ENABLED] ?: false
    }

    // ── Protection Flows ──
    val strictModeOn: Flow<Boolean> = dataStore.data.map { it[STRICT_MODE_ON] ?: true }
    val emergencyPassesPerDay: Flow<Int> = dataStore.data.map { it[EMERGENCY_PASSES_PER_DAY] ?: 2 }
    val preventAppUninstall: Flow<Boolean> = dataStore.data.map { it[PREVENT_APP_UNINSTALL] ?: false }
    val blockSettingsAccess: Flow<Boolean> = dataStore.data.map { it[BLOCK_SETTINGS_ACCESS] ?: false }
    val lockDuringFocus: Flow<Boolean> = dataStore.data.map { it[LOCK_DURING_FOCUS] ?: false }

    // ── Break Rules Flows ──
    val breaksPerDay: Flow<Int> = dataStore.data.map { it[BREAKS_PER_DAY] ?: 3 }
    val requireChallengeForBreak: Flow<Boolean> = dataStore.data.map { it[REQUIRE_CHALLENGE_FOR_BREAK] ?: true }
    val autoResumeProtection: Flow<Boolean> = dataStore.data.map { it[AUTO_RESUME_PROTECTION] ?: true }

    // ── Intervention Rules Flows ──
    val triggerAfterScrollingMinutes: Flow<Int> = dataStore.data.map { it[TRIGGER_AFTER_SCROLLING_MINUTES] ?: 15 }
    val cooldownBetweenInterventionsMinutes: Flow<Int> = dataStore.data.map { it[COOLDOWN_BETWEEN_INTERVENTIONS_MINUTES] ?: 30 }
    val showWarningFirst: Flow<Boolean> = dataStore.data.map { it[SHOW_WARNING_FIRST] ?: true }
    val instantIntervention: Flow<Boolean> = dataStore.data.map { it[INSTANT_INTERVENTION] ?: false }

    // ── Notification Flows ──
    val interventionAlerts: Flow<Boolean> = dataStore.data.map { it[INTERVENTION_ALERTS] ?: true }
    val dailySummary: Flow<Boolean> = dataStore.data.map { it[DAILY_SUMMARY] ?: true }
    val streakReminders: Flow<Boolean> = dataStore.data.map { it[STREAK_REMINDERS] ?: true }
    val motivationalMessages: Flow<Boolean> = dataStore.data.map { it[MOTIVATIONAL_MESSAGES] ?: false }

    // ── Appearance Flows ──
    val theme: Flow<String> = dataStore.data.map { it[THEME] ?: "Dark" }
    val animationsEnabled: Flow<Boolean> = dataStore.data.map { it[ANIMATIONS_ENABLED] ?: true }

    // ── Challenge Flows ──
    val challengeDifficulty: Flow<String> = dataStore.data.map { it[CHALLENGE_DIFFICULTY] ?: "Medium" }
    val randomizeChallenges: Flow<Boolean> = dataStore.data.map { it[RANDOMIZE_CHALLENGES] ?: true }

    // ── Existing Setters ──
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setBlockingMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[BLOCKING_MODE] = mode
        }
    }

    suspend fun setBlockType(type: String) {
        dataStore.edit { preferences ->
            preferences[BLOCK_TYPE] = type
        }
    }

    suspend fun setLastDowngradeTime(timeMillis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_MODE_DOWNGRADE_TIME] = timeMillis
        }
    }

    suspend fun setStrictChallengeType(type: String) {
        dataStore.edit { preferences ->
            preferences[STRICT_CHALLENGE_TYPE] = type
        }
    }

    suspend fun setStrictChallengeAmount(amount: Int) {
        dataStore.edit { preferences ->
            preferences[STRICT_CHALLENGE_AMOUNT] = amount
        }
    }

    suspend fun setEasyModeWaitSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[EASY_MODE_WAIT_SECONDS] = seconds
        }
    }

    suspend fun setBreakDurationMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[BREAK_DURATION_MINUTES] = minutes
        }
    }

    suspend fun setDowngradeRequestTime(timeMillis: Long) {
        dataStore.edit { preferences ->
            preferences[DOWNGRADE_REQUEST_TIME] = timeMillis
        }
    }

    suspend fun setDowngradeRequestTarget(mode: String?) {
        dataStore.edit { preferences ->
            if (mode != null) {
                preferences[DOWNGRADE_REQUEST_TARGET] = mode
            } else {
                preferences.remove(DOWNGRADE_REQUEST_TARGET)
            }
        }
    }

    suspend fun setAutoDowngradeAtMidnight(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_DOWNGRADE_AT_MIDNIGHT] = enabled
            if (enabled) {
                preferences[LAST_AUTO_DOWNGRADE_TIME] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setLastAutoDowngradeTime(timeMillis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_AUTO_DOWNGRADE_TIME] = timeMillis
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    // ── Protection Setters ──
    suspend fun setStrictModeOn(enabled: Boolean) { dataStore.edit { it[STRICT_MODE_ON] = enabled } }
    suspend fun setEmergencyPassesPerDay(count: Int) { dataStore.edit { it[EMERGENCY_PASSES_PER_DAY] = count } }
    suspend fun setPreventAppUninstall(enabled: Boolean) { dataStore.edit { it[PREVENT_APP_UNINSTALL] = enabled } }
    suspend fun setBlockSettingsAccess(enabled: Boolean) { dataStore.edit { it[BLOCK_SETTINGS_ACCESS] = enabled } }
    suspend fun setLockDuringFocus(enabled: Boolean) { dataStore.edit { it[LOCK_DURING_FOCUS] = enabled } }

    // ── Break Rules Setters ──
    suspend fun setBreaksPerDay(count: Int) { dataStore.edit { it[BREAKS_PER_DAY] = count } }
    suspend fun setRequireChallengeForBreak(enabled: Boolean) { dataStore.edit { it[REQUIRE_CHALLENGE_FOR_BREAK] = enabled } }
    suspend fun setAutoResumeProtection(enabled: Boolean) { dataStore.edit { it[AUTO_RESUME_PROTECTION] = enabled } }

    // ── Intervention Rules Setters ──
    suspend fun setTriggerAfterScrollingMinutes(minutes: Int) { dataStore.edit { it[TRIGGER_AFTER_SCROLLING_MINUTES] = minutes } }
    suspend fun setCooldownBetweenInterventionsMinutes(minutes: Int) { dataStore.edit { it[COOLDOWN_BETWEEN_INTERVENTIONS_MINUTES] = minutes } }
    suspend fun setShowWarningFirst(enabled: Boolean) { dataStore.edit { it[SHOW_WARNING_FIRST] = enabled } }
    suspend fun setInstantIntervention(enabled: Boolean) { dataStore.edit { it[INSTANT_INTERVENTION] = enabled } }

    // ── Notification Setters ──
    suspend fun setInterventionAlerts(enabled: Boolean) { dataStore.edit { it[INTERVENTION_ALERTS] = enabled } }
    suspend fun setDailySummary(enabled: Boolean) { dataStore.edit { it[DAILY_SUMMARY] = enabled } }
    suspend fun setStreakReminders(enabled: Boolean) { dataStore.edit { it[STREAK_REMINDERS] = enabled } }
    suspend fun setMotivationalMessages(enabled: Boolean) { dataStore.edit { it[MOTIVATIONAL_MESSAGES] = enabled } }

    // ── Appearance Setters ──
    suspend fun setTheme(theme: String) { dataStore.edit { it[THEME] = theme } }
    suspend fun setAnimationsEnabled(enabled: Boolean) { dataStore.edit { it[ANIMATIONS_ENABLED] = enabled } }

    // ── Challenge Setters ──
    suspend fun setChallengeDifficulty(difficulty: String) { dataStore.edit { it[CHALLENGE_DIFFICULTY] = difficulty } }
    suspend fun setRandomizeChallenges(enabled: Boolean) { dataStore.edit { it[RANDOMIZE_CHALLENGES] = enabled } }

    // ── Screen Time ──
    val dailyScreenTime: Flow<String> = dataStore.data.map { it[DAILY_SCREEN_TIME] ?: "2-4" }
    val targetScreenTime: Flow<String> = dataStore.data.map { it[TARGET_SCREEN_TIME] ?: "2h" }
    suspend fun setDailyScreenTime(time: String) { dataStore.edit { it[DAILY_SCREEN_TIME] = time } }
    suspend fun setTargetScreenTime(time: String) { dataStore.edit { it[TARGET_SCREEN_TIME] = time } }

    // ── Focus Mode ──
    val focusModeUntilMillis: Flow<Long> = dataStore.data.map { it[FOCUS_MODE_UNTIL_MILLIS] ?: 0L }
    suspend fun setFocusModeUntilMillis(timeMillis: Long) { dataStore.edit { it[FOCUS_MODE_UNTIL_MILLIS] = timeMillis } }
}

