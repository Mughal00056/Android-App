package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkStudioColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = CompilerEmerald,
    onSecondary = Color(0xFF00291B),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = NdkViolet,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF3B1D7A),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark,
    error = ErrorCoral,
    onError = Color.White
)

private val LightStudioColorScheme = lightColorScheme(
    primary = StudioBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = StudioEmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = StudioVioletTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = StudioLightBg,
    onBackground = Color(0xFF0F172A),
    surface = StudioLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = StudioLightVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = ErrorCoral,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
