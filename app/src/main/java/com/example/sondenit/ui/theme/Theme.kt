package com.example.sondenit.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SonDeNitColorScheme = darkColorScheme(
    primary = MoonGlow,
    onPrimary = NightDeep,
    secondary = Lavender,
    onSecondary = NightDeep,
    tertiary = SkyTeal,
    onTertiary = NightDeep,
    background = NightDeep,
    onBackground = OnNight,
    surface = NightMid,
    onSurface = OnNight,
    surfaceVariant = NightSurface,
    onSurfaceVariant = OnNightMuted,
    surfaceContainerHigh = NightSurfaceHigh,
    error = DangerRed,
    onError = NightDeep,
)

@Composable
fun SonDeNitTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NightDeep.toArgb()
            window.navigationBarColor = NightDeep.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = SonDeNitColorScheme,
        typography = Typography,
        content = content,
    )
}
