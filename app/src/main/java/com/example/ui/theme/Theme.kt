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

private val SpaceDarkColorScheme = darkColorScheme(
    primary = SpaceAccent,
    secondary = SpaceTeal,
    tertiary = SpaceCard,
    background = SpaceBackground,
    surface = SpaceSurface,
    onPrimary = SpaceTextPrimary,
    onSecondary = SpaceTextPrimary,
    onBackground = SpaceTextPrimary,
    onSurface = SpaceTextPrimary,
    error = SpaceError
)

private val SpaceLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1F2937),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1F2937),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1F2937),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for the eye-safe Focus-Flow Space Slate experience
    dynamicColor: Boolean = false, // Disable dynamic colors in order to enforce our premium aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SpaceDarkColorScheme else SpaceLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
