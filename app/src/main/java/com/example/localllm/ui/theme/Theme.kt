package com.example.localllm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Color Palette ─────────────────────────────────────────────────────────────

// Dark — GitHub-inspired deep blue-black with cyan accent
private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF58A6FF),
    onPrimary        = Color(0xFF00213D),
    primaryContainer = Color(0xFF003567),
    onPrimaryContainer = Color(0xFFADD4FF),

    secondary        = Color(0xFF3FB950),
    onSecondary      = Color(0xFF003910),
    secondaryContainer = Color(0xFF00521C),
    onSecondaryContainer = Color(0xFF9AE59F),

    tertiary         = Color(0xFFD2A8FF),
    onTertiary       = Color(0xFF2D0046),
    tertiaryContainer= Color(0xFF450063),
    onTertiaryContainer = Color(0xFFEDCFFF),

    error            = Color(0xFFF85149),
    onError          = Color(0xFF690005),
    errorContainer   = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background       = Color(0xFF0D1117),
    onBackground     = Color(0xFFE6EDF3),
    surface          = Color(0xFF161B22),
    onSurface        = Color(0xFFE6EDF3),
    surfaceVariant   = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceTint      = Color(0xFF58A6FF),
    outline          = Color(0xFF30363D),
    outlineVariant   = Color(0xFF21262D),
    inverseSurface   = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF0D1117),
    inversePrimary   = Color(0xFF0969DA),
    scrim            = Color(0xFF000000)
)

// Light — Clean GitHub light mode
private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF0969DA),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF023B95),

    secondary        = Color(0xFF1A7F37),
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF004221),

    tertiary         = Color(0xFF8250DF),
    onTertiary       = Color(0xFFFFFFFF),
    tertiaryContainer= Color(0xFFF0E7FF),
    onTertiaryContainer = Color(0xFF3D0E91),

    error            = Color(0xFFCF222E),
    onError          = Color(0xFFFFFFFF),
    errorContainer   = Color(0xFFFFDDD9),
    onErrorContainer = Color(0xFF7D0012),

    background       = Color(0xFFF6F8FA),
    onBackground     = Color(0xFF1F2328),
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF1F2328),
    surfaceVariant   = Color(0xFFEAEEF2),
    onSurfaceVariant = Color(0xFF57606A),
    outline          = Color(0xFFD0D7DE),
    outlineVariant   = Color(0xFFEAEEF2),
    inverseSurface   = Color(0xFF1F2328),
    inverseOnSurface = Color(0xFFF6F8FA),
    inversePrimary   = Color(0xFF58A6FF)
)

// ─── Typography ────────────────────────────────────────────────────────────────

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),

    headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),

    titleLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall   = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    labelLarge   = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall   = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

// ─── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun LocalLLMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
