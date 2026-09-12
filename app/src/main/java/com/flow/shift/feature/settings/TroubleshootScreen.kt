package com.flow.shift.feature.settings

import android.annotation.SuppressLint
import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flow.shift.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.shift.feature.settings.SettingsViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasCameraPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasAccessibilityPermission by remember { mutableStateOf(hasAccessibilityPermission(context)) }
    var hasNotificationPermission by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val isChineseOEM = Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")
    val autostartCheckSupported = remember { canCheckAutostart(context) }
    var userInteractedAutostart by remember { mutableStateOf(false) }
    var hasAutostartPermission by remember {
        mutableStateOf(
            if (!isChineseOEM) true
            else if (autostartCheckSupported) hasAutostartPermission(context)
            else false
        )
    }
    var hasBackgroundWindowsPermission by remember { mutableStateOf(hasBackgroundWindowPermission(context)) }
    var hasBatteryOptimizationExemption by remember { mutableStateOf(isBatteryOptimizationExempt(context)) }

    val coroutineScope = rememberCoroutineScope()

    val refreshPermissions = remember(context, autostartCheckSupported) {
        {
            hasUsagePermission = hasUsageStatsPermission(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            hasAccessibilityPermission = hasAccessibilityPermission(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            hasBatteryOptimizationExemption = isBatteryOptimizationExempt(context)
            if (isChineseOEM) {
                hasBackgroundWindowsPermission = hasBackgroundWindowPermission(context)
                if (autostartCheckSupported) {
                    hasAutostartPermission = hasAutostartPermission(context)
                } else if (userInteractedAutostart) {
                    hasAutostartPermission = true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                coroutineScope.launch {
                    val retryDelays = listOf(150L, 300L, 600L, 1000L, 1500L, 2000L)
                    for (d in retryDelays) {
                        delay(d)
                        refreshPermissions()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            refreshPermissions()
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Troubleshoot", 
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBlack,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = SurfaceBlack
    ) { padding ->
        var devModeClickCount by remember { mutableStateOf(0) }
        val isDeveloperModeEnabled by viewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle()
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        var showAccessibilityDialog by remember { mutableStateOf(false) }
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }

        if (showAccessibilityDialog) {
            com.flow.shift.core.designsystem.AccessibilityDisclosureDialog(
                onDismiss = { showAccessibilityDialog = false },
                onAccept = {
                    showAccessibilityDialog = false
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false 
                    passwordInput = ""
                },
                title = { Text("Developer Mode", fontFamily = AppFontFamily) },
                text = {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password", fontFamily = AppFontFamily) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (passwordInput == "4fsN#") {
                            viewModel.setDeveloperModeEnabled(true)
                            showPasswordDialog = false
                            passwordInput = ""
                        } else {
                            passwordInput = ""
                        }
                    }) {
                        Text("Enable", fontFamily = AppFontFamily)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = false 
                        passwordInput = ""
                    }) {
                        Text("Cancel", fontFamily = AppFontFamily)
                    }
                },
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Permissions Status",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        devModeClickCount++
                        if (devModeClickCount >= 6) {
                            if (!isDeveloperModeEnabled) {
                                showPasswordDialog = true
                            } else {
                                viewModel.setDeveloperModeEnabled(false)
                            }
                            devModeClickCount = 0
                        }
                    },
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppFontFamily
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "FlowShift requires several permissions to work correctly. Make sure all are granted.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                PermissionItem(
                    icon = Icons.Default.DataUsage,
                    title = "Usage Access",
                    description = "Required to track how much time you spend on apps.",
                    isGranted = hasUsagePermission,
                    onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                )
            }

            item {
                PermissionItem(
                    icon = Icons.Default.Layers,
                    title = "Display Over Other Apps",
                    description = "Required to show the block screen over distracting apps.",
                    isGranted = hasOverlayPermission,
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item {
                PermissionItem(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service",
                    description = "Required to prevent bypassing the block screen.",
                    isGranted = hasAccessibilityPermission,
                    onClick = {
                        if (hasAccessibilityPermission(context)) {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } else {
                            showAccessibilityDialog = true
                        }
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        description = "Required to keep the service running in the background.",
                        isGranted = hasNotificationPermission,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                PermissionItem(
                    icon = Icons.Default.BatteryAlert,
                    title = "Battery Optimization",
                    description = "Prevents your phone from killing FlowShift in the background. Critical for reliable blocking.",
                    isGranted = hasBatteryOptimizationExemption,
                    onClick = {
                        openBatteryOptimizationSettings(context)
                    }
                )
            }

            if (isChineseOEM) {
                item {
                    PermissionItem(
                        icon = Icons.Default.Settings,
                        title = "Autostart",
                        description = "Required for your phone to keep FlowShift running in the background.",
                        isGranted = hasAutostartPermission,
                        onClick = {
                            userInteractedAutostart = true
                            openAutostartSettings(context)
                        }
                    )
                }

                item {
                    PermissionItem(
                        icon = Icons.Default.OpenInNew,
                        title = "Background Windows",
                        description = "Allows FlowShift to open the blocker from the background.",
                        isGranted = hasBackgroundWindowsPermission,
                        onClick = {
                            openBackgroundWindowsSettings(context)
                        }
                    )
                }
            }


            if (isDeveloperModeEnabled) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Developer Settings",
                        color = Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily
                    )
                }
                
                item {
                    DeveloperToggleItem(
                        icon = Icons.Default.WorkspacePremium,
                        title = "Pretend Subscribed",
                        description = "Simulate an active premium subscription",
                        isChecked = state.pretendSubscribed,
                        onCheckedChange = { viewModel.setPretendSubscribed(it) }
                    )
                }

                item {
                    DeveloperToggleItem(
                        icon = Icons.Default.CameraAlt, // Reusing an icon since we don't have something like Timer/Speed handy right here, or I can just use a simple one
                        title = "Bypass Downgrade Wait Time",
                        description = "Skip the 6-hour waiting period for downgrading mode",
                        isChecked = state.bypassDowngradeWaitTime,
                        onCheckedChange = { viewModel.setBypassDowngradeWaitTime(it) }
                    )
                }

                item {
                    DeveloperToggleItem(
                        icon = Icons.Default.Lock,
                        title = "Bypass Target Lock",
                        description = "Allow modifying daily target even when locked",
                        isChecked = state.bypassTargetLock,
                        onCheckedChange = { viewModel.setBypassTargetLock(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isGranted) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = AppFontFamily,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (isGranted) "Granted" else "Not Granted",
            tint = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DeveloperToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF00E5FF).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = AppFontFamily,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00E5FF),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCardDarker,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasAccessibilityPermission(context: Context): Boolean {
    var accessibilityEnabled = 0
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
        // Ignored
    }
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            val serviceName = context.packageName + "/" + com.flow.shift.core.usagetracking.ScrollBlockerAccessibilityService::class.java.canonicalName
            return settingValue.contains(serviceName)
        }
    }
    return false
}

private fun canCheckAutostart(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    if (manufacturer in listOf("xiaomi", "redmi", "poco")) {
        return true
    }
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_AUTO_START")
            field.getInt(null)
        } catch (e: Exception) {
            10008
        }
        val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
        method.invoke(appOps, op, Process.myUid(), context.packageName)
        true
    } catch (e: Exception) {
        false
    }
}

private fun hasAutostartPermission(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    if (manufacturer !in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")) {
        return true
    }

    try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_AUTO_START")
            field.getInt(null)
        } catch (e: Exception) {
            10008
        }

        val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
        val mode = method.invoke(appOps, op, Process.myUid(), context.packageName) as Int
        return mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        return false
    }
}

private fun openAutostartSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
        Intent().setComponent(android.content.ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
    )

    for (intent in intents) {
        try {
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            // Ignore and try the next one
        }
    }

    openAppSettings(context)
}

private fun hasBackgroundWindowPermission(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    if (manufacturer !in listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "oneplus", "realme", "iqoo", "huawei", "honor")) {
        return true
    }
    
    try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        // 1. Try string op name if supported
        try {
            val mode = appOps.checkOpNoThrow("android:background_start_activity", Process.myUid(), context.packageName)
            if (mode == AppOpsManager.MODE_ALLOWED) return true
            if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) return false
        } catch (e: Exception) {
            // Fall through to int op code
        }

        // 2. Try integer op code (10021 or OP_BACKGROUND_START_ACTIVITY field)
        val op = try {
            val field = AppOpsManager::class.java.getDeclaredField("OP_BACKGROUND_START_ACTIVITY")
            field.getInt(null)
        } catch (e: Exception) {
            10021
        }
        
        val method = appOps.javaClass.getMethod("checkOpNoThrow", Int::class.java, Int::class.java, String::class.java)
        val mode = method.invoke(appOps, op, Process.myUid(), context.packageName) as Int
        return mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        // If we can't check it programmatically, default to true to not block the user
        return true
    }
}

private fun openBackgroundWindowsSettings(context: Context) {
    try {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", context.packageName)
        }
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            context.startActivity(intent)
            return
        }
    } catch (e: Exception) {
        // Fallback
    }
    
    openAppSettings(context)
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}

private fun isBatteryOptimizationExempt(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun openBatteryOptimizationSettings(context: Context) {
    if (isBatteryOptimizationExempt(context)) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        } catch (e: Exception) {
            openAppSettings(context)
            return
        }
    }
    try {
        // Direct exemption request — shows a system dialog to the user
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: open the full battery optimization list
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
            openAppSettings(context)
        }
    }
}


