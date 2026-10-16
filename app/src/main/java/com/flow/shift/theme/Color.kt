package com.flow.shift.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Glass border brush for premium card edges
val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.30f),
        Color.White.copy(alpha = 0.10f)
    )
)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ReelsRot Amber/Black Premium Theme
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)
val Amber700 = Color(0xFFB45309)
val AmberGlow = Color(0x14F59E0B) // 8% alpha for glow effects

val StatusGreen = Color(0xFF22C55E)
val StatusRed = Color(0xFFEF4444)

val SurfaceBlack = Color(0xFF000000)
val SurfaceCard = Color(0xFF111111)
val SurfaceCardLight = Color(0xFF1A1A1A)
val SurfaceCardBorder = Color(0xFF222222)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)

// Settings-specific colors
val DangerRed = Color(0xFFEF4444)
val PremiumGold = Color(0xFFFFD700)
val SurfaceCardDarker = Color(0xFF0D0D0D)
val OrangeGlow = Color(0x26F59E0B)            // 15% alpha amber for icon backgrounds

// Mode-specific accent colors
val ModeEasyAccent = Color(0xFF5ECCFF)         // Sky blue (unifies default/legacy with challenge mode)
val ModeStrictAccent = Color(0xFF5ECCFF)      // Sky blue for challenge mode (#5ECCFF)
val ModeHardcoreAccent = Color(0xFFEF4444)    // Red
val ModeEasyBg = Color(0x225ECCFF)            // 13.5% alpha sky blue
val ModeStrictBg = Color(0x225ECCFF)          // 13.5% alpha sky blue
val ModeHardcoreBg = Color(0x22EF4444)        // 13.5% alpha red

