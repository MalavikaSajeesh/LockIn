package com.lockin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Old palette was flat black + fire-engine red, which reads as an error state.
 * This one keeps the dark base (easier on the eyes, and dark = "focus mode")
 * but moves the accent to violet, with emerald for progress and amber for
 * tokens. Red is reserved for the locked state only, so it means something.
 */
val Ink          = Color(0xFF0F0D17)
val InkElevated  = Color(0xFF1A1726)
val InkHigh      = Color(0xFF241F33)
val Violet       = Color(0xFF8B7CFF)
val VioletDeep   = Color(0xFF5B4BE0)
val Emerald      = Color(0xFF34D399)
val Amber        = Color(0xFFFBBF24)
val Rose         = Color(0xFFFB7185)
val OnInk        = Color(0xFFECEAF3)
val Muted        = Color(0xFF9A93B0)

/** Used for the progress ring and hero surfaces. */
val ProgressBrush = Brush.linearGradient(listOf(Violet, Emerald))
val HeroBrush = Brush.linearGradient(listOf(Color(0xFF241F33), Color(0xFF1A1726)))

private val LockInColorScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF16121F),
    primaryContainer = VioletDeep,
    onPrimaryContainer = Color.White,
    secondary = Emerald,
    onSecondary = Color(0xFF07231A),
    tertiary = Amber,
    onTertiary = Color(0xFF2A1E00),
    background = Ink,
    onBackground = OnInk,
    surface = InkElevated,
    onSurface = OnInk,
    surfaceVariant = InkHigh,
    onSurfaceVariant = Muted,
    outline = Color(0xFF3A3450),
    error = Rose,
    onError = Color(0xFF2B0710)
)

private val LockInTypography = Typography(
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
)

@Composable
fun LockInTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LockInColorScheme,
        typography = LockInTypography,
        content = content
    )
}
