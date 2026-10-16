package com.flow.shift.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.R
import com.flow.shift.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val blockingModeStr by viewModel.blockingMode.collectAsStateWithLifecycle()
    val currentMode = com.flow.shift.feature.modes.BlockingMode.fromString(blockingModeStr)
    val modeAccent = when (currentMode) {
        com.flow.shift.feature.modes.BlockingMode.EASY -> ModeEasyAccent
        com.flow.shift.feature.modes.BlockingMode.STRICT -> ModeStrictAccent
        com.flow.shift.feature.modes.BlockingMode.HARDCORE -> ModeHardcoreAccent
    }

    val context = LocalContext.current
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(authUser) {
        if (authUser == null) {
            onNavigateBack()
        }
    }

    val displayName = authUser?.displayName?.takeIf { it.isNotBlank() } ?: "Google User"
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(modeAccent, modeAccent.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.Black,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Name
            Text(
                text = displayName,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Google Connection Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCardLight.copy(alpha = 0.6f))
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connected with Google",
                    color = TextSecondary,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Account Actions Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceCard.copy(alpha = 0.35f))
                    .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "ACCOUNT ACTIONS",
                    color = TextMuted,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Sign Out",
                    valueColor = TextSecondary,
                    onClick = { showSignOutDialog = true }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                SettingsNavRow(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete Account",
                    valueColor = DangerRed,
                    onClick = { showDeleteAccountDialog = true }
                )
            }
        }

        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
                title = { Text("Sign Out") },
                text = { Text("Are you sure you want to sign out of your Google account?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.signOut {
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                            showSignOutDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) {
                        Text("Sign Out")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSignOutDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                containerColor = SurfaceCard,
                titleContentColor = DangerRed,
                textContentColor = TextSecondary,
                title = { Text("Delete Account") },
                text = { Text("Are you sure you want to permanently delete your account and associated data? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAccount { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else if (!error.isNullOrBlank()) {
                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                }
                            }
                            showDeleteAccountDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) {
                        Text("Delete Permanently")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteAccountDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
