package com.flow.shift.feature.appselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.flow.shift.core.AppSortingHelper
import com.flow.shift.core.designsystem.AppIcon
import com.flow.shift.theme.Amber500
import com.flow.shift.theme.SurfaceCardBorder
import com.flow.shift.theme.SurfaceBlack

@Composable
fun AppSelectionScreen(
    viewModel: AppSelectionViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val apps by viewModel.appList.collectAsStateWithLifecycle()

    var isMoreAppsExpanded by remember { mutableStateOf(false) }
    val topApps = remember(apps) {
        apps.filter { it.isBlocked || AppSortingHelper.isSocialOrTopApp(it.packageName, it.appName) }
    }
    val otherApps = remember(apps) {
        apps.filter { !it.isBlocked && !AppSortingHelper.isSocialOrTopApp(it.packageName, it.appName) }
    }

    Scaffold(
        containerColor = SurfaceBlack
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Select Apps to Block",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(topApps, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleAppBlockStatus(app, isChecked)
                                }
                            )
                        }
                        if (otherApps.isNotEmpty()) {
                            item(key = "more_apps_header") {
                                ExpandableAppsHeader(
                                    title = "More Apps",
                                    count = otherApps.size,
                                    isExpanded = isMoreAppsExpanded,
                                    onToggle = { isMoreAppsExpanded = !isMoreAppsExpanded }
                                )
                            }
                            if (isMoreAppsExpanded) {
                                items(otherApps, key = { it.packageName }) { app ->
                                    AppListItem(
                                        app = app,
                                        onCheckedChange = { isChecked ->
                                            viewModel.toggleAppBlockStatus(app, isChecked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionBottomSheet(
    onDismissRequest: () -> Unit,
    viewModel: AppSelectionViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val apps by viewModel.appList.collectAsStateWithLifecycle()

    var isMoreAppsExpanded by remember { mutableStateOf(false) }
    val topApps = remember(apps) {
        apps.filter { it.isBlocked || AppSortingHelper.isSocialOrTopApp(it.packageName, it.appName) }
    }
    val otherApps = remember(apps) {
        apps.filter { !it.isBlocked && !AppSortingHelper.isSocialOrTopApp(it.packageName, it.appName) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceBlack,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .padding(bottom = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Amber500
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Select Protected Apps",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose which apps to monitor and protect",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(topApps, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleAppBlockStatus(app, isChecked)
                                }
                            )
                        }
                        if (otherApps.isNotEmpty()) {
                            item(key = "more_apps_header") {
                                ExpandableAppsHeader(
                                    title = "More Apps",
                                    count = otherApps.size,
                                    isExpanded = isMoreAppsExpanded,
                                    onToggle = { isMoreAppsExpanded = !isMoreAppsExpanded }
                                )
                            }
                            if (isMoreAppsExpanded) {
                                items(otherApps, key = { it.packageName }) { app ->
                                    AppListItem(
                                        app = app,
                                        onCheckedChange = { isChecked ->
                                            viewModel.toggleAppBlockStatus(app, isChecked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableAppsHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$title ($count)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = Color.Gray,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun AppListItem(
    app: AppItem,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = app.packageName,
                    appName = app.appName,
                    modifier = Modifier.size(42.dp),
                    cornerRadius = 10.dp,
                    fallbackFontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = app.appName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Switch(
                checked = app.isBlocked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Amber500,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
        Divider(color = SurfaceCardBorder, thickness = 1.dp)
    }
}

