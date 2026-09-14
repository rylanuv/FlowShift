package com.flow.shift.feature.statistics

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.shift.core.designsystem.AppIcon
import com.flow.shift.feature.dashboard.formatDurationShort
import com.flow.shift.theme.*

private val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.25f),
        Color.White.copy(alpha = 0.05f)
    )
)

@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
    ) {
        when (val state = uiState) {
            is StatisticsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Amber500
                )
            }
            is StatisticsUiState.Success -> {
                StatisticsContent(
                    metrics = state.metrics,
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { viewModel.setTimePeriod(it) },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun StatisticsContent(
    metrics: StatisticsMetrics,
    selectedPeriod: StatsTimePeriod,
    onSelectPeriod: (StatsTimePeriod) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedChartIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp)
    ) {
        // ── Top Bar ──
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Statistics",
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Your screen freedom breakdown",
                        color = TextMuted,
                        fontFamily = AppFontFamily,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Period Toggle ──
        item {
            PeriodSelector(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = {
                    selectedChartIndex = -1
                    onSelectPeriod(it)
                }
            )
        }

        // ── Permission Warning Banner (if needed) ──
        if (!metrics.hasUsageAccess) {
            item {
                UsagePermissionBanner(
                    onGrantClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        val periodSuffix = when (selectedPeriod) {
            StatsTimePeriod.TODAY -> "today"
            StatsTimePeriod.PAST_7_DAYS -> "this week"
            StatsTimePeriod.PAST_MONTH -> "this month"
        }

        // ── Hero Total Screen Time Card ("Scrolling has costed you") ──
        item {
            HeroScreenTimeCard(
                metrics = metrics,
                selectedPeriod = selectedPeriod
            )
        }

        // ── Protected Apps Breakdown ──
        item {
            Text(
                text = "Protected Apps Breakdown",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ── App Breakdown List ──
        if (metrics.appStatsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard.copy(alpha = 0.55f))
                        .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No protected apps configured yet",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(metrics.appStatsList, key = { it.packageName }) { appStat ->
                AppStatRow(stat = appStat)
            }
        }

        // ── Vulnerable Hours (Doomscroll Intervention Insights) ──
        item {
            InterventionInsightsCard(
                distribution = metrics.timeOfDayDistribution,
                mostBlockedApp = metrics.mostBlockedAppName,
                totalInterventions = metrics.totalInterventions
            )
        }

        // ── Daily Screen Time (Usage Bar Chart - 7 Days / Past Month only) ──
        if (selectedPeriod != StatsTimePeriod.TODAY) {
            item {
                DailyUsageChartCard(
                    dailyItems = metrics.dailyUsageList,
                    averageUsageMillis = metrics.averageDailyUsageMillis,
                    selectedIndex = selectedChartIndex,
                    onSelectIndex = { selectedChartIndex = it }
                )
            }
        }

        // ── Insight Cards ("those cards") ──
        item {
            InsightCardsSection(
                metrics = metrics,
                periodSuffix = periodSuffix
            )
        }
    }
}

// ── Segmented Control / Period Selector ──
@Composable
private fun PeriodSelector(
    selectedPeriod: StatsTimePeriod,
    onPeriodSelected: (StatsTimePeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard.copy(alpha = 0.6f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        val periods = listOf(
            StatsTimePeriod.TODAY to "Today",
            StatsTimePeriod.PAST_7_DAYS to "7 Days",
            StatsTimePeriod.PAST_MONTH to "Past Month"
        )

        periods.forEach { (period, title) ->
            val isSelected = selectedPeriod == period
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Amber500.copy(alpha = 0.2f) else Color.Transparent,
                label = "periodBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Amber500 else TextSecondary,
                label = "periodText"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) Amber500.copy(alpha = 0.4f) else Color.Transparent,
                label = "periodBorder"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable { onPeriodSelected(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ── Usage Permission Banner ──
@Composable
private fun UsagePermissionBanner(onGrantClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Amber500.copy(alpha = 0.12f))
            .border(1.dp, Amber500.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Amber500,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Usage Permission Required",
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Grant access to calculate screen time on protected apps.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onGrantClick,
            colors = ButtonDefaults.textButtonColors(contentColor = Amber500)
        ) {
            Text("Grant", fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDurationHrMin(millis: Long): String {
    val totalMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(millis).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} hr ${minutes} min"
        hours > 0 -> "${hours} hr"
        else -> "${minutes} min"
    }
}

// ── Hero Screen Time Card ──
@Composable
private fun HeroScreenTimeCard(
    metrics: StatisticsMetrics,
    selectedPeriod: StatsTimePeriod
) {
    val durationText = if (!metrics.hasUsageAccess) "--" else formatDurationHrMin(metrics.totalProtectedUsageMillis)
    val subtitleText = when (selectedPeriod) {
        StatsTimePeriod.TODAY -> {
            if (!metrics.hasUsageAccess) "Grant usage access" else "of your day"
        }
        StatsTimePeriod.PAST_7_DAYS, StatsTimePeriod.PAST_MONTH -> {
            "Daily Average: ${formatDurationHrMin(metrics.averageDailyUsageMillis)}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "SCROLLING HAS COSTED YOU",
                color = Amber500,
                fontFamily = AppFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = durationText,
                color = TextPrimary,
                fontFamily = AppFontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitleText,
                color = TextSecondary,
                fontFamily = AppFontFamily,
                fontSize = 13.sp
            )
        }
    }
}

// ── Dashboard Grid Card (Fixed Size) ──
@Composable
private fun StatGridCard(
    modifier: Modifier = Modifier,
    label: String,
    labelColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = labelColor,
            fontFamily = AppFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 2,
            lineHeight = 13.sp
        )
        content()
    }
}

// ── Insight Cards ──
@Composable
private fun InsightCardsSection(
    metrics: StatisticsMetrics,
    periodSuffix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── Row 1: Breaks & Additional Time ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Breaks Taken ──
            StatGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                label = "BREAKS TAKEN",
                labelColor = ModeStrictAccent
            ) {
                Column {
                    Text(
                        text = metrics.totalBreaksTaken.toString(),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (metrics.totalBreaksTaken == 1) "break $periodSuffix" else "breaks $periodSuffix",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp
                    )
                }
            }

            // ── Additional Time Spent ──
            StatGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                label = "ADDITIONAL TIME SPENT",
                labelColor = Amber500
            ) {
                Column {
                    Text(
                        text = formatDurationShort(metrics.earnedMillisToday),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "from breaks $periodSuffix",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── Row 2: Reels & Blocks ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Reels Scrolled ──
            StatGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                label = "REELS SCROLLED",
                labelColor = Amber500
            ) {
                Column {
                    Text(
                        text = metrics.totalReelsCount.toString(),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (metrics.totalReelsCount == 1) "reel scrolled $periodSuffix" else "reels scrolled $periodSuffix",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp
                    )
                }
            }

            // ── Doomscrolls Blocked ──
            StatGridCard(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp),
                label = "DOOMSCROLLS BLOCKED",
                labelColor = StatusGreen
            ) {
                Column {
                    Text(
                        text = metrics.totalInterventions.toString(),
                        color = TextPrimary,
                        fontFamily = AppFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (metrics.totalInterventions == 1) "scroll blocked $periodSuffix" else "scrolls blocked $periodSuffix",
                        color = TextSecondary,
                        fontFamily = AppFontFamily,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// ── 7-Day Bar Chart ──
@Composable
private fun DailyUsageChartCard(
    dailyItems: List<DailyUsageItem>,
    averageUsageMillis: Long,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val isMonthly = dailyItems.size > 7
    val chartTitle = if (isMonthly) "Last 30 Days" else "Last 7 Days"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DAILY SCREEN TIME",
                    color = Amber500,
                    fontFamily = AppFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = chartTitle,
                    color = TextPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (selectedIndex in dailyItems.indices) {
                val item = dailyItems[selectedIndex]
                val dateLabel = if (isMonthly) {
                    java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(item.dateMillis))
                } else {
                    item.dayLabel
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$dateLabel: ${formatDurationShort(item.usageMillis)}",
                        color = Amber500,
                        fontFamily = AppFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.interventionCount} blocks",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                Text(
                    text = "Avg: ${formatDurationShort(averageUsageMillis)}",
                    color = TextSecondary,
                    fontFamily = AppFontFamily,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (dailyItems.isNotEmpty()) {
            val maxUsage = dailyItems.maxOfOrNull { it.usageMillis }?.coerceAtLeast(60_000L) ?: 60_000L

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dailyItems) {
                            detectTapGestures { offset ->
                                val barWidth = size.width / dailyItems.size
                                val index = (offset.x / barWidth).toInt().coerceIn(0, dailyItems.size - 1)
                                onSelectIndex(if (selectedIndex == index) -1 else index)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val barCount = dailyItems.size
                    val totalBarSlotWidth = w / barCount
                    val barWidth = if (isMonthly) (totalBarSlotWidth * 0.65f).coerceAtLeast(3.dp.toPx()) else totalBarSlotWidth * 0.45f
                    val cornerRadius = if (isMonthly) CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()) else CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    val chartHeight = h - 28.dp.toPx()

                    // Average reference line
                    if (maxUsage > 0L && averageUsageMillis > 0L) {
                        val avgY = chartHeight * (1f - (averageUsageMillis.toFloat() / maxUsage.toFloat()).coerceIn(0.05f, 0.95f))
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, avgY),
                            end = Offset(w, avgY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    dailyItems.forEachIndexed { index, item ->
                        val centerX = (index * totalBarSlotWidth) + (totalBarSlotWidth / 2f)
                        val barLeft = centerX - (barWidth / 2f)
                        val fraction = (item.usageMillis.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)
                        val barHeight = (chartHeight * fraction).coerceAtLeast(4.dp.toPx())
                        val barTop = chartHeight - barHeight

                        val isHighlighted = (selectedIndex == index) || (selectedIndex == -1 && item.isToday)
                        val barColor = when {
                            selectedIndex == index -> Amber500
                            item.isToday -> Amber500
                            else -> Color.White.copy(alpha = 0.18f)
                        }

                        // Draw bar
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = cornerRadius
                        )

                        // Subtle bottom line track
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.05f),
                            topLeft = Offset(barLeft, chartHeight - 2.dp.toPx()),
                            size = Size(barWidth, 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }
                }

                // Row of labels below canvas
                if (isMonthly) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val labelIndices = listOf(0, 7, 14, 21, dailyItems.lastIndex).distinct()
                        val monthDayFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                        labelIndices.forEach { idx ->
                            if (idx in dailyItems.indices) {
                                val item = dailyItems[idx]
                                Text(
                                    text = if (item.isToday) "Today" else monthDayFormat.format(java.util.Date(item.dateMillis)),
                                    color = if (item.isToday) Amber500 else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        dailyItems.forEachIndexed { index, item ->
                            val isHighlighted = (selectedIndex == index) || (selectedIndex == -1 && item.isToday)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = item.dayLabel,
                                    color = if (isHighlighted) Amber500 else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = item.dateNumber,
                                    color = if (isHighlighted) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Time of Day Vulnerability Card ──
@Composable
private fun InterventionInsightsCard(
    distribution: TimeOfDayDistribution,
    mostBlockedApp: String?,
    totalInterventions: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "VULNERABLE HOURS",
            color = Amber500,
            fontFamily = AppFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Doomscroll Intervention Insights",
            color = TextPrimary,
            fontFamily = AppFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (totalInterventions == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No interventions recorded for this period. Clean streak!",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            val maxCount = maxOf(
                distribution.morningCount,
                distribution.afternoonCount,
                distribution.eveningCount,
                distribution.nightCount,
                1
            )

            val slots = listOf(
                "Morning\n6A-12P" to distribution.morningCount,
                "Afternoon\n12P-6P" to distribution.afternoonCount,
                "Evening\n6P-10P" to distribution.eveningCount,
                "Night\n10P-6A" to distribution.nightCount
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slots.forEach { (slotLabel, count) ->
                    val isPeak = count > 0 && count == maxCount
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPeak) Amber500.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
                            .border(
                                1.dp,
                                if (isPeak) Amber500.copy(alpha = 0.35f) else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = count.toString(),
                            color = if (isPeak) Amber500 else TextPrimary,
                            fontFamily = AppFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Mini bar
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isPeak) Amber500 else Color.White.copy(alpha = 0.3f))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = slotLabel,
                            color = TextMuted,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            if (mostBlockedApp != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Most blocked app: $mostBlockedApp",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Protected App Row Item ──
@Composable
private fun AppStatRow(stat: AppStatItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard.copy(alpha = 0.55f))
            .border(1.dp, GlassBorderBrush, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = stat.packageName,
            appName = stat.appName,
            modifier = Modifier.size(46.dp),
            cornerRadius = 12.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.appName,
                    color = TextPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDurationShort(stat.usageMillis),
                    color = TextPrimary,
                    fontFamily = AppFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Percentage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(stat.percentageOfTotal)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Amber500.copy(alpha = 0.8f), Amber500)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stat.openCount} ${if (stat.openCount == 1) "open" else "opens"}",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                val isShortFormApp = stat.packageName == "com.instagram.android" ||
                    stat.packageName == "com.google.android.youtube" ||
                    stat.packageName == "com.facebook.katana" ||
                    stat.packageName == "com.zhiliaoapp.musically" ||
                    stat.packageName == "com.snapchat.android"

                if (stat.reelCount > 0 || isShortFormApp) {
                    val label = if (stat.packageName == "com.google.android.youtube") "shorts" else "reels"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (stat.reelCount > 0) Amber500.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${stat.reelCount} $label",
                            color = if (stat.reelCount > 0) Amber500 else TextMuted,
                            fontFamily = AppFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "${stat.interventionCount} ${if (stat.interventionCount == 1) "block" else "blocks"}",
                    color = if (stat.interventionCount > 0) StatusGreen else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
