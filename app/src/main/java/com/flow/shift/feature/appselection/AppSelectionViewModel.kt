package com.flow.shift.feature.appselection

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.AppSortingHelper
import com.flow.shift.core.database.BlockedAppDao
import com.flow.shift.core.database.BlockedAppEntity
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
import javax.inject.Inject

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedAppDao: BlockedAppDao
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // Combine installed apps with user's saved preferences
    val appList: StateFlow<List<AppItem>> = combine(
        _installedApps,
        blockedAppDao.getAllBlockedApps()
    ) { installed, saved ->
        installed.map { app ->
            val savedApp = saved.find { it.packageName == app.packageName }
            app.copy(isBlocked = savedApp?.isEnabled ?: false)
        }.sortedWith(
            compareByDescending<AppItem> { it.isBlocked }
                .thenBy { AppSortingHelper.getAppPriority(it.packageName, it.appName) }
                .thenBy { it.appName.lowercase() }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            val apps = packages.filter { 
                // Show launchable apps (includes pre-installed user apps like YouTube/Chrome)
                pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName
            }.map {
                AppItem(
                    packageName = it.packageName,
                    appName = pm.getApplicationLabel(it).toString(),
                    isBlocked = false
                )
            }
            
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun toggleAppBlockStatus(app: AppItem, isBlocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = BlockedAppEntity(
                packageName = app.packageName,
                appName = app.appName,
                isEnabled = isBlocked,
                customDifficulty = null // Default
            )
            blockedAppDao.insertBlockedApp(entity)
        }
    }
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean
)

