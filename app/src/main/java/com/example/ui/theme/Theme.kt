package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = BrandNavyElevated,
    onPrimaryContainer = Color.White,
    secondary = BrandAmber,
    onSecondary = Color.Black,
    secondaryContainer = BrandAmberLight,
    onSecondaryContainer = BrandAmberDark,
    tertiary = BrandEmerald,
    onTertiary = Color.White,
    tertiaryContainer = BrandEmeraldLight,
    onTertiaryContainer = BrandEmeraldDark,
    background = CanvasLight,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextMedium,
    outline = SurfaceHover,
    outlineVariant = Color(0xFFCBD5E1),
    error = StatusCancelled,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandAmber,
    onPrimary = Color.Black,
    primaryContainer = BrandNavyElevated,
    onPrimaryContainer = Color.White,
    secondary = BrandEmerald,
    onSecondary = Color.Black,
    tertiary = BrandBlue,
    background = Color(0xFF090D16),
    onBackground = Color(0xFFF8FAFC),
    surface = BrandNavyElevated,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = BrandNavy,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = BrandNavyBorder,
    error = StatusCancelled,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
