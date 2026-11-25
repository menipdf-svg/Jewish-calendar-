package com.menipdf.jewishcalendar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Light = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF174EA6),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF5F6368),
    outlineVariant = Color(0xFFDADCE0)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF202124),
    primaryContainer = Color(0xFF2B3A55),
    onPrimaryContainer = Color(0xFFD2E3FC),
    background = Color(0xFF202124),
    surface = Color(0xFF202124),
    surfaceVariant = Color(0xFF303134),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outlineVariant = Color(0xFF3C4043)
)

@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun JcTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
