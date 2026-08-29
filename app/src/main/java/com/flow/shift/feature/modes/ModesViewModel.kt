package com.flow.shift.feature.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ModesViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = settingsDataStore.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val uiState: StateFlow<ModesUiState> = combine(
        settingsDataStore.blockingMode,
        settingsDataStore.lastDowngradeTimeMillis,
        settingsDataStore.strictChallengeType,
        settingsDataStore.downgradeRequestTimeMillis,
        settingsDataStore.downgradeRequestTarget,
        settingsDataStore.autoDowngradeAtMidnight,
        settingsDataStore.bypassDowngradeWaitTime
    ) { args ->
        ModesUiState(
            currentMode = BlockingMode.fromString(args[0] as String),
            lastDowngradeTimeMillis = args[1] as Long,
            strictChallengeType = args[2] as String,
            downgradeRequestTimeMillis = args[3] as Long,
            downgradeRequestTarget = args[4] as String?,
            autoDowngradeAtMidnight = args[5] as Boolean,
            bypassDowngradeWaitTime = args[6] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModesUiState()
    )

    // Pending mode change that awaits user confirmation
    private val _pendingModeChange = MutableStateFlow<PendingModeChange?>(null)
    val pendingModeChange: StateFlow<PendingModeChange?> = _pendingModeChange.asStateFlow()

    // Temporarily track the user's challenge selection in the confirmation dialog
    private val _pendingStrictChallengeType = MutableStateFlow("PUSHUPS")
    val pendingStrictChallengeType: StateFlow<String> = _pendingStrictChallengeType.asStateFlow()

    private var downgradeTimerJob: Job? = null
    private val _downgradeWaitRemainingSeconds = MutableStateFlow(0)
    val downgradeWaitRemainingSeconds: StateFlow<Int> = _downgradeWaitRemainingSeconds.asStateFlow()

    fun requestModeChange(newMode: BlockingMode) {
        val currentMode = uiState.value.currentMode
        if (newMode == currentMode) return

        // Default the pending selection to whatever is currently saved
        _pendingStrictChallengeType.value = uiState.value.strictChallengeType

        // Upgrading or downgrading — show confirmation
        if (newMode.isDowngradeFrom(currentMode)) {
            viewModelScope.launch {
                settingsDataStore.setAutoDowngradeAtMidnight(false)
            }
            val requestTime = uiState.value.downgradeRequestTimeMillis
            val requestTarget = uiState.value.downgradeRequestTarget
            val now = System.currentTimeMillis()
            
            var validRequest = requestTime > 0L

            val waitSeconds = 6 * 60 * 60 // 6 hours
            
            if (uiState.value.bypassDowngradeWaitTime) {
                _downgradeWaitRemainingSeconds.value = 0
            } else if (validRequest && requestTarget == newMode.name) {
                val elapsed = (now - requestTime) / 1000
                val remaining = waitSeconds - elapsed.toInt()
                _downgradeWaitRemainingSeconds.value = if (remaining > 0) remaining else 0
            } else {
                // New request
                _downgradeWaitRemainingSeconds.value = waitSeconds
                viewModelScope.launch {
                    settingsDataStore.setDowngradeRequestTime(now)
                    settingsDataStore.setDowngradeRequestTarget(newMode.name)
                }
            }

            downgradeTimerJob?.cancel()
            downgradeTimerJob = viewModelScope.launch {
                while (_downgradeWaitRemainingSeconds.value > 0) {
                    delay(1000)
                    _downgradeWaitRemainingSeconds.value -= 1
                }
            }
        } else {
            _downgradeWaitRemainingSeconds.value = 0
        }

        _pendingModeChange.value = PendingModeChange(
            targetMode = newMode,
            type = if (newMode.isUpgradeFrom(currentMode))
                PendingChangeType.UPGRADE_CONFIRMATION
            else
                PendingChangeType.DOWNGRADE_CONFIRMATION,
            cooldownRemainingMillis = 0L
        )
    }

    fun confirmModeChange() {
        val pending = _pendingModeChange.value ?: return
        val currentMode = uiState.value.currentMode

        viewModelScope.launch {
            if (pending.targetMode.isDowngradeFrom(currentMode)) {
                if (downgradeWaitRemainingSeconds.value > 0) {
                    if (uiState.value.autoDowngradeAtMidnight) {
                        settingsDataStore.setDowngradeRequestTarget(pending.targetMode.name)
                        if (pending.targetMode == BlockingMode.STRICT) {
                            settingsDataStore.setStrictChallengeType(_pendingStrictChallengeType.value)
                        }
                        settingsDataStore.setLastAutoDowngradeTime(System.currentTimeMillis())
                        _pendingModeChange.value = null
                        return@launch
                    }
                }
                settingsDataStore.setLastDowngradeTime(System.currentTimeMillis())
                settingsDataStore.setDowngradeRequestTime(0L)
                settingsDataStore.setDowngradeRequestTarget(null)
            }
            if (pending.targetMode == BlockingMode.STRICT) {
                settingsDataStore.setStrictChallengeType(_pendingStrictChallengeType.value)
            }
            settingsDataStore.setBlockingMode(pending.targetMode.name)
            downgradeTimerJob?.cancel()
            _pendingModeChange.value = null
        }
    }

    fun dismissModeChange() {
        downgradeTimerJob?.cancel()
        _pendingModeChange.value = null
    }

    fun setPendingStrictChallengeType(type: String) {
        _pendingStrictChallengeType.value = type
    }

    fun setAutoDowngradeAtMidnight(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoDowngradeAtMidnight(enabled)
        }
    }
}

data class ModesUiState(
    val currentMode: BlockingMode = BlockingMode.EASY,
    val lastDowngradeTimeMillis: Long = 0L,
    val strictChallengeType: String = "PUSHUPS",
    val downgradeRequestTimeMillis: Long = 0L,
    val downgradeRequestTarget: String? = null,
    val autoDowngradeAtMidnight: Boolean = false,
    val bypassDowngradeWaitTime: Boolean = false
)

data class PendingModeChange(
    val targetMode: BlockingMode,
    val type: PendingChangeType,
    val cooldownRemainingMillis: Long = 0L
)

enum class PendingChangeType {
    UPGRADE_CONFIRMATION,
    DOWNGRADE_CONFIRMATION
}

