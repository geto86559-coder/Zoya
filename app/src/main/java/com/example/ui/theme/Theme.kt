package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ZoyaDarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidBlack,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = TextPrimary,
    secondary = NeonMagenta,
    onSecondary = VoidBlack,
    secondaryContainer = NeonPurpleGlow,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonPurple,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceHighlight,
    error = CyberRed,
    onError = VoidBlack
  )

@Composable
fun ZoyaTheme(
  darkTheme: Boolean = true, // Zoya is a futuristic dark experience by default
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = ZoyaDarkColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  ZoyaTheme(darkTheme = true, content = content)
}

