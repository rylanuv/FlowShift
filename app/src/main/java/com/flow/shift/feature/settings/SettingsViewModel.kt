package com.flow.shift.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.BlockedAppEntity
import com.flow.shift.core.database.UserGamificationDao
import com.flow.shift.core.database.UserGamificationEntity
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val blockedAppDao: BlockedAppDao,
    gamificationDao: UserGamificationDao
) : ViewModel() {

    private val blockedAppsFlow = blockedAppDao.getEnabledBlockedApps()
        .map { apps -> apps.map { BlockedAppInfo(it.packageName, it.appName, it.isEnabled) } }

    private val gamificationFlow = gamificationDao.getUserGamification()
        .map { entity ->
            entity ?: UserGamificationEntity(
                id = 1, totalXp = 0, currentLevel = 1,
                currentStreakDays = 0, lastActiveDateMillis = System.currentTimeMillis(),
                scrollCredits = 30
            )
        }

    val blockingMode: StateFlow<String> = settingsDataStore.blockingMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "EASY"
    )

    val isDeveloperModeEnabled: StateFlow<Boolean> = settingsDataStore.isDeveloperModeEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        // Protection (split into two groups because combine max is 5)
        combine(
            settingsDataStore.strictModeOn,
            settingsDataStore.emergencyPassesPerDay,
            settingsDataStore.preventAppUninstall
        ) { strictMode, emergencyPasses, preventUninstall -> Triple(strictMode, emergencyPasses, preventUninstall) },
        combine(
            settingsDataStore.blockSettingsAccess,
            settingsDataStore.lockDuringFocus
        ) { blockSettings, lockFocus -> Pair(blockSettings, lockFocus) }
    ) { first, second ->
        ProtectionState(first.first, first.second, first.third, second.first, second.second)
    }.combine(
        combine(
            settingsDataStore.breakDurationMinutes,
            settingsDataStore.breaksPerDay,
            settingsDataStore.requireChallengeForBreak,
            settingsDataStore.autoResumeProtection
        ) { duration, perDay, requireChallenge, autoResume ->
            BreakRulesState(duration, perDay, requireChallenge, autoResume)
        }
    ) { protection, breakRules -> protection to breakRules }
    .combine(
        combine(
            settingsDataStore.triggerAfterScrollingMinutes,
            settingsDataStore.cooldownBetweenInterventionsMinutes,
            settingsDataStore.showWarningFirst,
            settingsDataStore.instantIntervention
        ) { trigger, cooldown, showWarning, instant ->
            InterventionState(trigger, cooldown, showWarning, instant)
        }
    ) { (protection, breakRules), intervention -> Triple(protection, breakRules, intervention) }
    .combine(
        combine(
            settingsDataStore.interventionAlerts,
            settingsDataStore.dailySummary,
            settingsDataStore.streakReminders,
            settingsDataStore.motivationalMessages
        ) { alerts, summary, streaks, motivational ->
            NotificationState(alerts, summary, streaks, motivational)
        }
    ) { (protection, breakRules, intervention), notifications ->
        FourState(protection, breakRules, intervention, notifications)
    }
    .combine(
        combine(
            combine(
                settingsDataStore.theme,
                settingsDataStore.animationsEnabled
            ) { theme, animations -> AppearanceState(theme, animations) },
            combine(
                settingsDataStore.challengeDifficulty,
                settingsDataStore.randomizeChallenges,
                settingsDataStore.strictChallengeType,
                settingsDataStore.strictChallengeAmount
            ) { difficulty, randomize, challengeType, challengeAmount ->
                ChallengeState(difficulty, randomize, challengeType, challengeAmount)
            }
        ) { appearance, challenges -> appearance to challenges }
    ) { fourState, (appearance, challenges) ->
        SixState(fourState.protection, fourState.breakRules, fourState.intervention, fourState.notifications, appearance, challenges)
    }
    .combine(blockedAppsFlow) { sixState, blockedApps ->
        SevenState(sixState, blockedApps)
    }
    .combine(
        combine(
            settingsDataStore.blockType,
            settingsDataStore.targetScreenTime
        ) { blockType, targetScreenTime -> blockType to targetScreenTime }
    ) { sevenState, (blockType, targetScreenTime) ->
        EightState(sevenState, blockType, targetScreenTime)
    }
    .combine(gamificationFlow) { eightState, gamification ->
        SettingsUiState(
            protection = eightState.sevenState.sixState.protection,
            breakRules = eightState.sevenState.sixState.breakRules,
            intervention = eightState.sevenState.sixState.intervention,
            notifications = eightState.sevenState.sixState.notifications,
            appearance = eightState.sevenState.sixState.appearance,
            challenges = eightState.sevenState.sixState.challenges,
            blockedApps = eightState.sevenState.blockedApps,
            blockType = eightState.blockType,
            targetScreenTime = eightState.targetScreenTime,
            streakDays = gamification.currentStreakDays,
            totalXp = gamification.totalXp,
            currentLevel = gamification.currentLevel
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    // ── Protection ──
    fun setStrictModeOn(enabled: Boolean) = launch { settingsDataStore.setStrictModeOn(enabled) }
    fun setPreventAppUninstall(enabled: Boolean) = launch { settingsDataStore.setPreventAppUninstall(enabled) }
    fun setBlockSettingsAccess(enabled: Boolean) = launch { settingsDataStore.setBlockSettingsAccess(enabled) }
    fun setLockDuringFocus(enabled: Boolean) = launch { settingsDataStore.setLockDuringFocus(enabled) }

    fun setBlockType(type: String) = launch { settingsDataStore.setBlockType(type) }
    fun setDeveloperModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDeveloperModeEnabled(enabled)
        }
    }

    // --- Screen Time ---
    fun setDailyScreenTime(time: String) = launch { settingsDataStore.setDailyScreenTime(time) }
    fun setTargetScreenTime(time: String) = launch { settingsDataStore.setTargetScreenTime(time) }

    // ── Break Rules ──
    fun setRequireChallengeForBreak(enabled: Boolean) = launch { settingsDataStore.setRequireChallengeForBreak(enabled) }
    fun setAutoResumeProtection(enabled: Boolean) = launch { settingsDataStore.setAutoResumeProtection(enabled) }

    // ── Intervention Rules ──
    fun setShowWarningFirst(enabled: Boolean) = launch { settingsDataStore.setShowWarningFirst(enabled) }
    fun setInstantIntervention(enabled: Boolean) = launch { settingsDataStore.setInstantIntervention(enabled) }

    // ── Notifications ──
    fun setInterventionAlerts(enabled: Boolean) = launch { settingsDataStore.setInterventionAlerts(enabled) }
    fun setDailySummary(enabled: Boolean) = launch { settingsDataStore.setDailySummary(enabled) }
    fun setStreakReminders(enabled: Boolean) = launch { settingsDataStore.setStreakReminders(enabled) }
    fun setMotivationalMessages(enabled: Boolean) = launch { settingsDataStore.setMotivationalMessages(enabled) }

    // ── Appearance ──
    fun setAnimationsEnabled(enabled: Boolean) = launch { settingsDataStore.setAnimationsEnabled(enabled) }

    // ── Challenges ──
    fun setRandomizeChallenges(enabled: Boolean) = launch { settingsDataStore.setRandomizeChallenges(enabled) }
    fun setChallengeType(type: String) = launch { settingsDataStore.setStrictChallengeType(type) }
    fun setChallengeAmount(amount: Int) = launch { settingsDataStore.setStrictChallengeAmount(amount) }

    // ── App Management ──
    fun toggleBlockedApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val existing = blockedAppDao.getBlockedApp(packageName)
            if (existing != null) {
                blockedAppDao.updateBlockedApp(existing.copy(isEnabled = enabled))
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

// ── Intermediate combine helpers ──
private data class FourState(
    val protection: ProtectionState,
    val breakRules: BreakRulesState,
    val intervention: InterventionState,
    val notifications: NotificationState
)

private data class SixState(
    val protection: ProtectionState,
    val breakRules: BreakRulesState,
    val intervention: InterventionState,
    val notifications: NotificationState,
    val appearance: AppearanceState,
    val challenges: ChallengeState
)

private data class SevenState(
    val sixState: SixState,
    val blockedApps: List<BlockedAppInfo>
)

private data class EightState(
    val sevenState: SevenState,
    val blockType: String,
    val targetScreenTime: String
)

// ── UI State ──
data class SettingsUiState(
    val protection: ProtectionState = ProtectionState(),
    val breakRules: BreakRulesState = BreakRulesState(),
    val intervention: InterventionState = InterventionState(),
    val notifications: NotificationState = NotificationState(),
    val appearance: AppearanceState = AppearanceState(),
    val challenges: ChallengeState = ChallengeState(),
    val blockedApps: List<BlockedAppInfo> = emptyList(),
    val blockType: String = "REELS",
    val targetScreenTime: String = "2h",
    val streakDays: Int = 0,
    val totalXp: Int = 0,
    val currentLevel: Int = 1
)

data class ProtectionState(
    val strictModeOn: Boolean = true,
    val emergencyPassesPerDay: Int = 2,
    val preventAppUninstall: Boolean = false,
    val blockSettingsAccess: Boolean = false,
    val lockDuringFocus: Boolean = false
)

data class BreakRulesState(
    val breakDurationMinutes: Int = 5,
    val breaksPerDay: Int = 3,
    val requireChallengeForBreak: Boolean = true,
    val autoResumeProtection: Boolean = true
)

data class InterventionState(
    val triggerAfterScrollingMinutes: Int = 15,
    val cooldownBetweenInterventionsMinutes: Int = 30,
    val showWarningFirst: Boolean = true,
    val instantIntervention: Boolean = false
)

data class NotificationState(
    val interventionAlerts: Boolean = true,
    val dailySummary: Boolean = true,
    val streakReminders: Boolean = true,
    val motivationalMessages: Boolean = false
)

data class AppearanceState(
    val theme: String = "Dark",
    val animationsEnabled: Boolean = true
)

data class ChallengeState(
    val difficulty: String = "Medium",
    val randomizeChallenges: Boolean = true,
    val challengeType: String = "PUSHUPS",
    val challengeAmount: Int = 15
)

data class BlockedAppInfo(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean
)

