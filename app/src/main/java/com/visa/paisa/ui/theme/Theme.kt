package com.visa.paisa.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Teal = Color(0xFF0F6E64)
private val TealLight = Color(0xFF7BE0AD)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F2E4),
    onPrimaryContainer = Color(0xFF03231E),
    secondary = Color(0xFF4B635C),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFF0F1EE),
    onSurfaceVariant = Color(0xFF52605B),
    outline = Color(0xFFD5DBD7),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFF9DF2D6),
    secondary = Color(0xFFB1CCC3),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF191D1C),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF232827),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF3A403E),
    error = Color(0xFFF2B8B5),
)

/**
 * One palette for every chart in the app, ordered so adjacent categories in the legend
 * never sit next to a near-identical hue. Deliberately fixed rather than generated:
 * "Food is always orange" is what makes a pie chart readable at a glance.
 */
object CategoryColors {

    private val palette = mapOf(
        "Food & Dining" to Color(0xFFE8703A),
        "Groceries" to Color(0xFF3E9B72),
        "Transport" to Color(0xFF3B7BC4),
        "Bills & Utilities" to Color(0xFF8E6BC9),
        "Entertainment" to Color(0xFFD64C7F),
        "Shopping" to Color(0xFFE0A32E),
        "Subscriptions" to Color(0xFF2FA8A0),
        "Health" to Color(0xFFC2453F),
        "Education" to Color(0xFF5D7CB8),
        "Travel" to Color(0xFF7BA23F),
        "Cash & ATM" to Color(0xFF7A6A5D),
        "Transfers" to Color(0xFF9AA5A0),
        "Investments" to Color(0xFF4C6A8A),
        "Other" to Color(0xFF9E8FA8),
        "Uncategorized" to Color(0xFFB6BCB9),
    )

    private val fallback = Color(0xFF8C9491)

    operator fun get(category: String): Color = palette[category] ?: fallback
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun PaisaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
