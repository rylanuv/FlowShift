package com.flow.shift.feature.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisciplineModeSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val strictChallengeType: StateFlow<String> = settingsDataStore.strictChallengeType
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "PUSHUPS"
        )

    val strictChallengeAmount: StateFlow<Int> = settingsDataStore.strictChallengeAmount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 15
        )

    val breakDurationMinutes: StateFlow<Int> = settingsDataStore.breakDurationMinutes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 5
        )

    fun setStrictChallengeType(type: String) {
        viewModelScope.launch {
            settingsDataStore.setStrictChallengeType(type)
        }
    }

    fun setStrictChallengeAmount(amount: Int) {
        viewModelScope.launch {
            settingsDataStore.setStrictChallengeAmount(amount)
        }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.setBreakDurationMinutes(minutes)
        }
    }
}
