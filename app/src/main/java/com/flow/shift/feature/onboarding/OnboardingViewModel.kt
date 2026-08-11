package com.flow.shift.feature.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.AppSortingHelper
import com.flow.shift.core.database.BlockedAppEntity
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.feature.modes.BlockingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val blockedAppDao: BlockedAppDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val TOTAL_STEPS = 7
    }

    // ── Navigation ──
    private val _currentStep = MutableStateFlow(savedStateHandle.get<Int>("currentStep") ?: 0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    // ── Step 2: Screen Time ──
    private val _selectedScreenTime = MutableStateFlow<Float?>(savedStateHandle.get<Float>("selectedScreenTime"))
    val selectedScreenTime: StateFlow<Float?> = _selectedScreenTime.asStateFlow()

    // ── Step 3: Target Screen Time ──
    private val _selectedTargetScreenTime = MutableStateFlow<Float?>(savedStateHandle.get<Float>("selectedTargetScreenTime"))
    val selectedTargetScreenTime: StateFlow<Float?> = _selectedTargetScreenTime.asStateFlow()

    // ── Step 4: Mode Selection ──
    private val _selectedMode = MutableStateFlow(
        savedStateHandle.get<String>("selectedMode")?.let { BlockingMode.valueOf(it) } ?: BlockingMode.EASY
    )
    val selectedMode: StateFlow<BlockingMode> = _selectedMode.asStateFlow()

    private val _selectedChallengeType = MutableStateFlow(savedStateHandle.get<String>("selectedChallengeType") ?: "PUSHUPS")
    val selectedChallengeType: StateFlow<String> = _selectedChallengeType.asStateFlow()

    private val _challengeAmount = MutableStateFlow(savedStateHandle.get<Int>("challengeAmount") ?: 15)
    val challengeAmount: StateFlow<Int> = _challengeAmount.asStateFlow()

    // ── Step 5: App Selection ──
    private val _installedApps = MutableStateFlow<List<OnboardingAppItem>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("searchQuery") ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Combine installed apps with blocked state from DB
    val appList: StateFlow<List<OnboardingAppItem>> = combine(
        _installedApps,
        blockedAppDao.getAllBlockedApps(),
        _searchQuery
    ) { installed, saved, query ->
        val mapped = installed.map { app ->
            val savedApp = saved.find { it.packageName == app.packageName }
            app.copy(isBlocked = savedApp?.isEnabled ?: false)
        }
        val comparator = compareByDescending<OnboardingAppItem> { it.isBlocked }
            .thenBy { AppSortingHelper.getAppPriority(it.packageName, it.appName) }
            .thenBy { it.appName.lowercase() }

        if (query.isBlank()) {
            mapped.sortedWith(comparator)
        } else {
            mapped.filter {
                it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }.sortedWith(comparator)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Count of blocked apps for recap
    val blockedAppCount: StateFlow<Int> = combine(
        _installedApps,
        blockedAppDao.getAllBlockedApps()
    ) { _, saved ->
        saved.count { it.isEnabled }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val apps = packages.filter {
                pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName
            }.map {
                OnboardingAppItem(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    isBlocked = false
                )
            }

            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    // ── Navigation ──
    fun nextStep() {
        if (_currentStep.value < TOTAL_STEPS - 1) {
            _currentStep.value += 1
            savedStateHandle["currentStep"] = _currentStep.value
        }
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value -= 1
            savedStateHandle["currentStep"] = _currentStep.value
        }
    }

    // ── Selections ──
    fun selectScreenTime(time: Float) {
        _selectedScreenTime.value = time
        savedStateHandle["selectedScreenTime"] = time
    }

    fun selectTargetScreenTime(time: Float) {
        _selectedTargetScreenTime.value = time
        savedStateHandle["selectedTargetScreenTime"] = time
    }

    fun selectMode(mode: BlockingMode) {
        _selectedMode.value = mode
        savedStateHandle["selectedMode"] = mode.name
    }

    fun selectChallengeType(type: String) {
        _selectedChallengeType.value = type
        savedStateHandle["selectedChallengeType"] = type
        if (type == "MATH" && _challengeAmount.value == 15) {
            _challengeAmount.value = 5 // Reasonable default for Math
            savedStateHandle["challengeAmount"] = 5
        } else if (type == "PUSHUPS" && _challengeAmount.value == 5) {
            _challengeAmount.value = 15 // Reasonable default for Pushups
            savedStateHandle["challengeAmount"] = 15
        }
    }

    fun selectChallengeAmount(amount: Int) {
        _challengeAmount.value = amount
        savedStateHandle["challengeAmount"] = amount
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        savedStateHandle["searchQuery"] = query
    }

    fun toggleAppBlocked(app: OnboardingAppItem, isBlocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = BlockedAppEntity(
                packageName = app.packageName,
                appName = app.appName,
                isEnabled = isBlocked,
                customDifficulty = null
            )
            blockedAppDao.insertBlockedApp(entity)
        }
    }

    // ── Completion ──
    fun completeOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Save screen time
            _selectedScreenTime.value?.let {
                settingsDataStore.setDailyScreenTime(it.toInt().toString())
            }
            (_selectedTargetScreenTime.value ?: 8f).let { sliderVal ->
                val idx = sliderVal.toInt().coerceIn(0, 36)
                val totalMinutes = if (idx <= 24) idx * 15 else 360 + (idx - 24) * 30
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                val str = when {
                    h == 0 && m == 0 -> "0m"
                    h == 0 -> "${m}m"
                    m == 0 -> "${h}h"
                    else -> "${h}h ${m}m"
                }
                settingsDataStore.setTargetScreenTime(str)
            }

            // Save mode
            settingsDataStore.setBlockingMode(_selectedMode.value.name)

            // Save challenge type if strict
            if (_selectedMode.value == BlockingMode.STRICT) {
                settingsDataStore.setStrictChallengeType(_selectedChallengeType.value)
                settingsDataStore.setStrictChallengeAmount(_challengeAmount.value)
            }

            // Mark onboarding complete
            settingsDataStore.setOnboardingCompleted(true)
            
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

data class OnboardingAppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean
)

