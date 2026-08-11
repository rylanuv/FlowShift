package com.flow.shift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.core.datastore.SettingsDataStore
import com.flow.shift.feature.appselection.AppSelectionScreen
import com.flow.shift.feature.dashboard.DashboardScreen
import com.flow.shift.feature.modes.EasyModeSettingsScreen
import com.flow.shift.feature.modes.DisciplineModeSettingsScreen
import com.flow.shift.feature.modes.HardcoreModeSettingsScreen
import com.flow.shift.feature.modes.BlockingMode
import com.flow.shift.feature.modes.ModesScreen
import com.flow.shift.feature.onboarding.OnboardingScreen
import com.flow.shift.feature.settings.SettingsScreen
import com.flow.shift.feature.subscription.SubscriptionScreen
import com.flow.shift.feature.settings.TroubleshootScreen
import com.flow.shift.theme.FlowShiftTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import android.content.Intent
import androidx.core.content.ContextCompat
import com.flow.shift.core.usagetracking.AppTrackingService
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.paint
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.flow.shift.theme.SurfaceBlack

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FlowShiftTheme {
                val isOnboardingCompleted by settingsDataStore.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = null)
                
                if (isOnboardingCompleted != null) {
                    if (isOnboardingCompleted == true) {
                        val serviceIntent = Intent(this@MainActivity, AppTrackingService::class.java)
                        ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                        FlowShiftApp(startDestination = "main", settingsDataStore = settingsDataStore)
                    } else {
                        FlowShiftApp(startDestination = "onboarding", settingsDataStore = settingsDataStore)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)))
                }
            }
        }
    }
}


@Composable
fun FlowShiftApp(startDestination: String = "main", settingsDataStore: SettingsDataStore? = null) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    val currentModeStr by (settingsDataStore?.blockingMode ?: kotlinx.coroutines.flow.flowOf("EASY"))
        .collectAsStateWithLifecycle(initialValue = "EASY")
    val currentMode = BlockingMode.fromString(currentModeStr)
    
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            // Only show bottom bar on main screen or individual legacy tabs
            if (currentRoute == "main" || currentRoute in listOf("dashboard", "modes", "settings")) {
                NavigationBar(
                    containerColor = Color(0xFF0A0A0A),
                    contentColor = Color.White
                ) {
                    val items = listOf(
                        Triple(0, "Home", Icons.Default.Home),
                        Triple(1, "Modes", Icons.Default.Shield),
                        Triple(2, "Settings", Icons.Default.Settings)
                    )
                    
                    items.forEach { (index, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            selected = pagerState.currentPage == index && currentRoute == "main",
                            onClick = {
                                if (currentRoute != "main") {
                                    navController.navigate("main") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = Color(0xFF71717A),
                                unselectedTextColor = Color(0xFF71717A),
                                selectedIconColor = Color(0xFFF59E0B),
                                selectedTextColor = Color.White,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("main") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .paint(
                            painter = painterResource(id = currentMode.backgroundImageRes),
                            contentScale = ContentScale.Crop
                        )
                        .background(SurfaceBlack.copy(alpha = 0.25f))
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> DashboardScreen(
                                showBackground = false,
                                onNavigateToApps = { navController.navigate("appSelection") },
                                onNavigateToModes = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                onNavigateToEasyModeSettings = { navController.navigate("easyModeSettings") },
                                onNavigateToDisciplineModeSettings = { navController.navigate("disciplineModeSettings") },
                                onNavigateToHardcoreModeSettings = { navController.navigate("hardcoreModeSettings") }
                            )
                            1 -> ModesScreen(
                                showBackground = false
                            )
                            2 -> SettingsScreen(
                                showBackground = false,
                                onNavigateToAppSelection = { navController.navigate("appSelection") },
                                onNavigateToSubscription = { navController.navigate("subscription") },
                                onNavigateToTroubleshoot = { navController.navigate("troubleshoot") }
                            )
                        }
                    }
                }
            }
            composable("dashboard") {
                DashboardScreen(
                    showBackground = true,
                    onNavigateToApps = { navController.navigate("appSelection") },
                    onNavigateToModes = { navController.navigate("modes") },
                    onNavigateToEasyModeSettings = { navController.navigate("easyModeSettings") },
                    onNavigateToDisciplineModeSettings = { navController.navigate("disciplineModeSettings") },
                    onNavigateToHardcoreModeSettings = { navController.navigate("hardcoreModeSettings") }
                )
            }
            composable("modes") {
                ModesScreen(
                    showBackground = true
                )
            }
            composable("easyModeSettings") {
                EasyModeSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("disciplineModeSettings") {
                DisciplineModeSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("hardcoreModeSettings") {
                HardcoreModeSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    showBackground = true,
                    onNavigateToAppSelection = { navController.navigate("appSelection") },
                    onNavigateToSubscription = { navController.navigate("subscription") },
                    onNavigateToTroubleshoot = { navController.navigate("troubleshoot") }
                )
            }
            composable("troubleshoot") {
                TroubleshootScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("appSelection") {
                AppSelectionScreen()
            }
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = {
                        val serviceIntent = Intent(context, AppTrackingService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                        navController.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("subscription") {
                SubscriptionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

