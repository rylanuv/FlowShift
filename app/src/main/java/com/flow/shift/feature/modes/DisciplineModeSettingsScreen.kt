package com.flow.shift.feature.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.flow.shift.theme.ModeStrictAccent
import kotlin.math.roundToInt

@Composable
fun DisciplineModeSettingsScreen(
    viewModel: DisciplineModeSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val challengeType by viewModel.strictChallengeType.collectAsStateWithLifecycle()
    val challengeAmount by viewModel.strictChallengeAmount.collectAsStateWithLifecycle()
    val breakDuration by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()

    val typeOptions = listOf(
        "PUSHUPS" to "Push-ups",
        "MATH" to "Math Problems"
    )



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
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
                text = "Discipline Mode Settings",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Challenge Type Section
        Text(
            text = "Challenge Type",
            color = ModeStrictAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select the type of challenge you want to complete to earn a break.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var expanded by remember { mutableStateOf(false) }
        val selectedOptionText = typeOptions.find { it.first == challengeType }?.second ?: "Push-ups"

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { expanded = true }
                    .padding(16.dp)
            ) {
                Text(
                    text = selectedOptionText,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown",
                    tint = TextPrimary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceBlack)
            ) {
                typeOptions.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = TextPrimary) },
                        onClick = {
                            viewModel.setStrictChallengeType(type)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Challenge Amount Section
        Text(
            text = "Challenge Amount",
            color = ModeStrictAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select how many reps or problems you need to complete.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Text(
                text = "$challengeAmount reps/problems",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = challengeAmount.toFloat(),
                onValueChange = { viewModel.setStrictChallengeAmount(it.roundToInt()) },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = ModeStrictAccent,
                    activeTrackColor = ModeStrictAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1", color = TextSecondary, fontSize = 12.sp)
                Text("100", color = TextSecondary, fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Break Duration Section
        Text(
            text = "Break Duration",
            color = ModeStrictAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select how many minutes of break you get after completing the challenge.",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Text(
                text = "$breakDuration Minutes",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = breakDuration.toFloat(),
                onValueChange = { viewModel.setBreakDurationMinutes(it.roundToInt()) },
                valueRange = 1f..60f,
                colors = SliderDefaults.colors(
                    thumbColor = ModeStrictAccent,
                    activeTrackColor = ModeStrictAccent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1m", color = TextSecondary, fontSize = 12.sp)
                Text("60m", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
