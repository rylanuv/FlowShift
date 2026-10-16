package com.flow.shift.feature.appselection

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.flow.shift.core.designsystem.preloadAppIcon
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
        val savedByPackage = saved.associateBy { it.packageName }
        installed.map { app ->
            val savedApp = savedByPackage[app.packageName]
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
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
            
            val apps = resolveInfos
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { resolveInfo ->
                    val appName = runCatching { resolveInfo.loadLabel(pm).toString() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: resolveInfo.activityInfo.packageName
                    AppItem(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = appName,
                        isBlocked = false
                    )
                }
            
            _installedApps.value = apps
            _isLoading.value = false

            val topApps = apps.filter { AppSortingHelper.isSocialOrTopApp(it.packageName, it.appName) }
            for (app in topApps) {
                preloadAppIcon(context, app.packageName)
            }
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

