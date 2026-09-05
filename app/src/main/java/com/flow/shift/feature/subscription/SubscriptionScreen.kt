package com.flow.shift.feature.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.flow.shift.R
import com.flow.shift.core.billing.BillingPurchaseState
import com.flow.shift.theme.*

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val plans by viewModel.plans.collectAsState()
    val selectedPlanId by viewModel.selectedPlanId.collectAsState()
    val purchaseState by viewModel.purchaseState.collectAsState()

    LaunchedEffect(purchaseState) {
        when (val state = purchaseState) {
            is BillingPurchaseState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetPurchaseState()
                onNavigateBack()
            }
            is BillingPurchaseState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetPurchaseState()
            }
            is BillingPurchaseState.Cancelled -> {
                viewModel.resetPurchaseState()
            }
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.subscription_bg_2),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceBlack.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Back Button & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardLight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNavigateBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Hero Icon & Title
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PremiumGold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Unlock FlowShift Pro",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Take full control of your time with advanced protection, custom challenges, and limitless focus.",
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Features list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FeatureRow("AI Squats & Pose Tracking Challenges")
            FeatureRow("Advanced Math Challenges")
            FeatureRow("Charge Phone Challenge")
            FeatureRow("Reels Block Only")
            FeatureRow("All Future Pro Challenges & Modes Included")
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Plans
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            plans.forEach { plan ->
                PlanCard(
                    id = plan.id,
                    title = plan.title,
                    price = plan.formattedPrice,
                    period = plan.period,
                    badgeText = plan.badgeText,
                    selected = selectedPlanId == plan.id,
                    onClick = { viewModel.selectPlan(plan.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        val isLoading = purchaseState is BillingPurchaseState.Loading
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isLoading) PremiumGold.copy(alpha = 0.6f) else PremiumGold)
                .clickable(enabled = !isLoading) { 
                    val activity = context as? Activity
                    if (activity != null) {
                        viewModel.startPurchase(activity)
                    }
                }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Continue",
                    color = Color.Black,
                    fontFamily = AppFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Restore Purchase",
            color = if (isLoading) TextMuted else TextSecondary,
            fontFamily = AppFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !isLoading,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.restorePurchases()
                }
                .padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Cancel anytime. Terms & conditions apply.",
            color = TextMuted,
            fontFamily = AppFontFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PremiumGold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlanCard(
    id: String,
    title: String,
    price: String,
    period: String,
    badgeText: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                PremiumGold.copy(alpha = 0.18f),
                Color(0xFF161616).copy(alpha = 0.55f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color(0xFF121212).copy(alpha = 0.45f)
            )
        )
    }

    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                PremiumGold,
                PremiumGold.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush = backgroundBrush)
            .border(
                width = if (selected) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PremiumGold.copy(alpha = 0.18f))
                                .border(1.dp, PremiumGold.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = PremiumGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = period,
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                    )
                }
            }

            // Radio Button alternative
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (selected) PremiumGold else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(PremiumGold)
                    )
                }
            }
        }
    }
}

