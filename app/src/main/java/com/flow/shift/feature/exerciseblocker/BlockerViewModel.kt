package com.flow.shift.feature.exerciseblocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.progress.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class BlockerUiState {
    object Intro : BlockerUiState()
    object Exercising : BlockerUiState()
    object Success : BlockerUiState()
}

@HiltViewModel
class BlockerViewModel @Inject constructor(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BlockerUiState>(BlockerUiState.Intro)
    val uiState: StateFlow<BlockerUiState> = _uiState.asStateFlow()

    private val _currentReps = MutableStateFlow(0)
    val currentReps: StateFlow<Int> = _currentReps.asStateFlow()
    private var unlockRecorded = false

    fun startExercise() {
        _uiState.value = BlockerUiState.Exercising
    }

    fun updateReps(
        reps: Int,
        requiredReps: Int,
        targetPackage: String,
        breakDurationMinutes: Int
    ) {
        _currentReps.value = reps
        if (reps >= requiredReps) {
            _uiState.value = BlockerUiState.Success
            recordUnlock(targetPackage, requiredReps, breakDurationMinutes)
        }
    }

    fun completeManualChallenge(
        targetPackage: String,
        requiredReps: Int,
        breakDurationMinutes: Int
    ) {
        _currentReps.value = requiredReps
        _uiState.value = BlockerUiState.Success
        recordUnlock(targetPackage, requiredReps, breakDurationMinutes)
    }

    private fun recordUnlock(targetPackage: String, repsCompleted: Int, breakDurationMinutes: Int) {
        if (unlockRecorded) return
        unlockRecorded = true
        viewModelScope.launch {
            progressRepository.recordUnlock(
                targetPackage = targetPackage,
                repsCompleted = repsCompleted,
                durationUnlockedMillis = TimeUnit.MINUTES.toMillis(breakDurationMinutes.toLong())
            )
        }
    }
}

