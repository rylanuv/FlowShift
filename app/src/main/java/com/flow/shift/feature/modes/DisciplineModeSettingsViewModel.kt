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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class DisciplineModeSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = settingsDataStore.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
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

    private val _draftChallengeAmounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    @OptIn(ExperimentalCoroutinesApi::class)
    val strictChallengeAmount: StateFlow<Int> = strictChallengeType.flatMapLatest { type ->
        combine(
            settingsDataStore.getChallengeAmountForTypeFlow(type),
            _draftChallengeAmounts.map { it[type] }
        ) { saved, draft ->
            draft ?: saved
        }
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
        initialValue = setOf("RANDOM")
    )

    fun setStrictChallengeType(type: String) {
        viewModelScope.launch {
            val premium = isPremium.value || settingsDataStore.isPremium.first()
            if (!premium && settingsDataStore.isPremiumChallenge(type)) {
                return@launch
            }
            _draftChallengeType.value = type
            if (type == "ADVANCED_MATH") {
                val current = _draftAdvancedMathTopics.value ?: advancedMathTopics.value
                if (current.isEmpty()) {
                    _draftAdvancedMathTopics.value = setOf("RANDOM")
                }
            }
        }
    }

    fun setStrictChallengeAmount(amount: Int) {
        val type = strictChallengeType.value
        _draftChallengeAmounts.value = _draftChallengeAmounts.value.toMutableMap().apply {
            put(type, amount)
        }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        _draftBreakDuration.value = minutes
    }

    fun setStrictChallengeDifficulty(difficulty: String) {
        _draftChallengeDifficulty.value = difficulty
    }

    fun toggleAdvancedMathTopic(topic: String) {
        _draftAdvancedMathTopics.value = setOf(topic)
    }

    fun saveSettings(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val typeToSave = _draftChallengeType.value ?: strictChallengeType.value
            val amountToSave = _draftChallengeAmounts.value[typeToSave] ?: strictChallengeAmount.value
            val durationToSave = _draftBreakDuration.value ?: breakDurationMinutes.value
            val difficultyToSave = _draftChallengeDifficulty.value ?: strictChallengeDifficulty.value
            val topicsToSave = _draftAdvancedMathTopics.value ?: advancedMathTopics.value

            val premium = isPremium.value || settingsDataStore.isPremium.first()
            val finalType = if (!premium && settingsDataStore.isPremiumChallenge(typeToSave)) {
                settingsDataStore.fallbackChallenge(typeToSave)
            } else {
                typeToSave
            }

            settingsDataStore.setStrictChallengeType(finalType)
            
            // Save drafted amounts for all types that were modified
            _draftChallengeAmounts.value.forEach { (type, amount) ->
                settingsDataStore.setChallengeAmountForType(type, amount)
            }
            
            // If the current type wasn't drafted but the user clicked save, save its amount too
            if (!_draftChallengeAmounts.value.containsKey(finalType)) {
                 settingsDataStore.setChallengeAmountForType(finalType, amountToSave)
            }
            
            settingsDataStore.setBreakDurationMinutes(durationToSave)
            settingsDataStore.setChallengeDifficulty(difficultyToSave)
            settingsDataStore.setAdvancedMathTopics(topicsToSave)
            onSaved()
        }
    }
}
