package com.flow.shift.feature.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisciplineModeSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = settingsDataStore.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _draftChallengeType = MutableStateFlow<String?>(null)
    val strictChallengeType: StateFlow<String> = combine(
        settingsDataStore.strictChallengeType,
        _draftChallengeType
    ) { saved, draft ->
        draft ?: saved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "MATH"
    )

    private val _draftChallengeAmount = MutableStateFlow<Int?>(null)
    val strictChallengeAmount: StateFlow<Int> = combine(
        settingsDataStore.strictChallengeAmount,
        _draftChallengeAmount
    ) { saved, draft ->
        draft ?: saved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 15
    )

    private val _draftBreakDuration = MutableStateFlow<Int?>(null)
    val breakDurationMinutes: StateFlow<Int> = combine(
        settingsDataStore.breakDurationMinutes,
        _draftBreakDuration
    ) { saved, draft ->
        draft ?: saved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 5
    )

    private val _draftChallengeDifficulty = MutableStateFlow<String?>(null)
    val strictChallengeDifficulty: StateFlow<String> = combine(
        settingsDataStore.challengeDifficulty,
        _draftChallengeDifficulty
    ) { saved, draft ->
        draft ?: saved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "Medium"
    )

    private val _draftAdvancedMathTopics = MutableStateFlow<Set<String>?>(null)
    val advancedMathTopics: StateFlow<Set<String>> = combine(
        settingsDataStore.advancedMathTopics,
        _draftAdvancedMathTopics
    ) { saved, draft ->
        draft ?: saved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = setOf("POLYNOMIAL")
    )

    fun setStrictChallengeType(type: String) {
        _draftChallengeType.value = type
    }

    fun setStrictChallengeAmount(amount: Int) {
        _draftChallengeAmount.value = amount
    }

    fun setBreakDurationMinutes(minutes: Int) {
        _draftBreakDuration.value = minutes
    }

    fun setStrictChallengeDifficulty(difficulty: String) {
        _draftChallengeDifficulty.value = difficulty
    }

    fun toggleAdvancedMathTopic(topic: String) {
        val current = _draftAdvancedMathTopics.value ?: advancedMathTopics.value
        val newTopics = current.toMutableSet()
        if (newTopics.contains(topic)) {
            if (newTopics.size > 1) { // ensure at least one topic remains
                newTopics.remove(topic)
            }
        } else {
            newTopics.add(topic)
        }
        _draftAdvancedMathTopics.value = newTopics
    }

    fun saveSettings(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val typeToSave = _draftChallengeType.value ?: strictChallengeType.value
            val amountToSave = _draftChallengeAmount.value ?: strictChallengeAmount.value
            val durationToSave = _draftBreakDuration.value ?: breakDurationMinutes.value
            val difficultyToSave = _draftChallengeDifficulty.value ?: strictChallengeDifficulty.value
            val topicsToSave = _draftAdvancedMathTopics.value ?: advancedMathTopics.value

            settingsDataStore.setStrictChallengeType(typeToSave)
            settingsDataStore.setStrictChallengeAmount(amountToSave)
            settingsDataStore.setBreakDurationMinutes(durationToSave)
            settingsDataStore.setChallengeDifficulty(difficultyToSave)
            settingsDataStore.setAdvancedMathTopics(topicsToSave)
            onSaved()
        }
    }
}
