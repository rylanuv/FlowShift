package com.flow.shift.feature.modes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.theme.SurfaceBlack
import com.flow.shift.theme.TextPrimary
import com.flow.shift.theme.TextSecondary
import com.flow.shift.theme.ModeStrictAccent
import com.flow.shift.theme.PremiumGold
import kotlin.math.roundToInt

@Composable
fun DisciplineModeSettingsScreen(
    viewModel: DisciplineModeSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val challengeType by viewModel.strictChallengeType.collectAsStateWithLifecycle()
    val challengeDifficulty by viewModel.strictChallengeDifficulty.collectAsStateWithLifecycle()
    val challengeAmount by viewModel.strictChallengeAmount.collectAsStateWithLifecycle()
    val breakDuration by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()

    val typeOptions = listOf(
        "MATH" to "Maths",
        "PUSHUPS" to "Push-ups",
        "ADVANCED_MATH" to "Advanced Maths",
        "SQUATS" to "Squats",
        "CHARGE_PHONE" to "Charge Your Phone"
    )



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
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

        var expanded by remember { mutableStateOf(false) }
        val selectedOptionText = typeOptions.find { it.first == challengeType }?.second ?: "Maths"

        val context = LocalContext.current
        var pendingCameraChallenge by remember { mutableStateOf<String?>(null) }
        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                if (granted) {
                    pendingCameraChallenge?.let { viewModel.setStrictChallengeType(it) }
                }
                pendingCameraChallenge = null
            }
        )

        // Challenge Setup Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Text(
                text = "Challenge Setup",
                color = ModeStrictAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Configure what you need to do to earn a break.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Type",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
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
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = TextPrimary)
                                    if (type != "MATH" && type != "PUSHUPS" && !isPremium) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = "Premium",
                                            tint = PremiumGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                if (type != "MATH" && type != "PUSHUPS" && !isPremium) {
                                    onNavigateToSubscription()
                                } else if (type == "PUSHUPS" || type == "SQUATS") {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setStrictChallengeType(type)
                                    } else {
                                        pendingCameraChallenge = type
                                        cameraLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                } else {
                                    viewModel.setStrictChallengeType(type)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (challengeType == "MATH" || challengeType == "ADVANCED_MATH") {
                Text(
                    text = "Difficulty",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val difficulties = listOf("Easy", "Medium", "Hard", "Extreme")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    difficulties.forEach { diff ->
                        val isSelected = challengeDifficulty.equals(diff, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ModeStrictAccent else Color.Transparent)
                                .clickable { viewModel.setStrictChallengeDifficulty(diff) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = diff,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (challengeType == "ADVANCED_MATH") {
                Text(
                    text = "Topics",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val topics = listOf(
                    "POLYNOMIAL" to "Polynomial",
                    "GEOMETRY" to "Geometry",
                    "TRIGONOMETRY" to "Trigonometry",
                    "CALCULUS" to "Calculus",
                    "MATRIX" to "Matrix",
                    "LOGARITHM" to "Logarithm",
                    "PROBABILITY" to "Probability",
                    "SEQUENCE" to "Sequence",
                    "VECTOR" to "Vector"
                )

                val advancedMathTopics by viewModel.advancedMathTopics.collectAsStateWithLifecycle()

                // Split into pairs to show 2 columns
                topics.chunked(2).forEach { rowTopics ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTopics.forEach { (topicId, topicName) ->
                            val isSelected = advancedMathTopics.contains(topicId)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ModeStrictAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, if (isSelected) ModeStrictAccent else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleAdvancedMathTopic(topicId) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = topicName,
                                    color = if (isSelected) ModeStrictAccent else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (rowTopics.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Challenge Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Amount",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$challengeAmount reps/problems",
                    color = ModeStrictAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
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

        // Break Duration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Break Duration",
                    color = ModeStrictAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$breakDuration Minutes",
                    color = ModeStrictAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
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

        var isSaving by remember { mutableStateOf(false) }

        Button(
            onClick = {
                if (!isSaving) {
                    isSaving = true
                    viewModel.saveSettings {
                        onNavigateBack()
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ModeStrictAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "Save",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
