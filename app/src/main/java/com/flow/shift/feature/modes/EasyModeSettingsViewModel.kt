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
class EasyModeSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val easyModeWaitSeconds: StateFlow<Int> = settingsDataStore.easyModeWaitSeconds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 90
        )

    fun setEasyModeWaitSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setEasyModeWaitSeconds(seconds)
        }
    }
}
