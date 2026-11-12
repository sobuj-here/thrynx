package com.thrynx.app.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ThrynxCyan,
    onPrimary = ThrynxBgDark,
    primaryContainer = ThrynxCardDark,
    onPrimaryContainer = ThrynxCyan,
    secondary = ThrynxCobalt,
    onSecondary = ThrynxBgDark,
    background = ThrynxBgDark,
    onBackground = ThrynxTextPrimaryDark,
    surface = ThrynxSurfaceDark,
    onSurface = ThrynxTextPrimaryDark,
    surfaceVariant = ThrynxCardDark,
    onSurfaceVariant = ThrynxTextSecondaryDark,
    outline = ThrynxCardStrokeDark
)

private val LightColorScheme = lightColorScheme(
    primary = ThrynxCobalt,
    onPrimary = ThrynxSurfaceLight,
    primaryContainer = ThrynxCardLight,
    onPrimaryContainer = ThrynxCobalt,
    secondary = ThrynxCyan,
    onSecondary = ThrynxSurfaceLight,
    background = ThrynxBgLight,
    onBackground = ThrynxTextPrimaryLight,
    surface = ThrynxSurfaceLight,
    onSurface = ThrynxTextPrimaryLight,
    surfaceVariant = ThrynxCardLight,
    onSurfaceVariant = ThrynxTextSecondaryLight,
    outline = ThrynxCardStrokeLight
)

@Composable
fun ThrynxTheme(
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
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
