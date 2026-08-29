package com.flow.shift.feature.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.theme.SurfaceBlack
import com.flow.shift.theme.TextPrimary
import com.flow.shift.theme.TextSecondary
import com.flow.shift.theme.ModeEasyAccent

@Composable
fun EasyModeSettingsScreen(
    viewModel: EasyModeSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val waitSeconds by viewModel.easyModeWaitSeconds.collectAsStateWithLifecycle()



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Easy Mode Settings",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Wait Duration",
            color = ModeEasyAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select how long you want to wait before taking a break.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val label = when {
            waitSeconds == 60 -> "1 Minute"
            waitSeconds % 60 == 0 -> "${waitSeconds / 60} Minutes"
            waitSeconds > 60 -> "${waitSeconds / 60} Min ${waitSeconds % 60} Sec"
            else -> "$waitSeconds Seconds"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Slider(
                value = waitSeconds.toFloat(),
                onValueChange = { viewModel.setEasyModeWaitSeconds(it.toInt()) },
                valueRange = 15f..180f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = ModeEasyAccent,
                    activeTrackColor = ModeEasyAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
