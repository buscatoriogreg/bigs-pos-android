package com.rgbpos.bigs.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BigsColors = lightColorScheme(
    primary = Color(0xFF2C3E50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D566E),
    secondary = Color(0xFF27AE60),
    onSecondary = Color.White,
    surface = Color(0xFFF8F9FA),
    background = Color(0xFFF8F9FA),
    error = Color(0xFFE74C3C),
)

@Composable
fun BigsPOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BigsColors,
        content = content,
    )
}
