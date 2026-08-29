package com.flow.shift.feature.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    fun subscribe() {
        viewModelScope.launch {
            settingsDataStore.setIsPremium(true)
        }
    }
}
