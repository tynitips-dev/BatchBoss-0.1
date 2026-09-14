package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BatchPink,
    onPrimary = SurfaceWhite,
    primaryContainer = BatchPinkContainer,
    onPrimaryContainer = BatchPinkDark,
    secondary = CoralOrange,
    onSecondary = SurfaceWhite,
    secondaryContainer = BatchPinkLight,
    onSecondaryContainer = BatchPinkDark,
    tertiary = WarmAmber,
    onTertiary = SurfaceWhite,
    background = BackgroundLight,
    onBackground = DarkText,
    surface = SurfaceWhite,
    onSurface = DarkText,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = MediumText,
    outline = BorderLight,
    outlineVariant = DividerColor
)

private val DarkColorScheme = darkColorScheme(
    primary = BatchPink,
    onPrimary = SurfaceWhite,
    primaryContainer = Color(0xFF4A0015),
    onPrimaryContainer = BatchPinkLight,
    secondary = CoralOrange,
    background = Color(0xFF121212),
    onBackground = SurfaceWhite,
    surface = Color(0xFF1E1E1E),
    onSurface = SurfaceWhite
)

@Composable
fun BatchBossTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
