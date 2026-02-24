package com.twohearts.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TwoHeartsRed   = Color(0xFFE85D75)
private val TwoHeartsLight = Color(0xFFFFF1F3)
private val TwoHeartsDark  = Color(0xFF8B1A2D)

private val LightColors = lightColorScheme(
    primary          = TwoHeartsRed,
    onPrimary        = Color.White,
    primaryContainer = TwoHeartsLight,
    onPrimaryContainer = TwoHeartsDark,
    secondary        = Color(0xFF7B5EA7),
    onSecondary      = Color.White,
    background       = Color(0xFFFFFBFE),
    surface          = Color(0xFFFFFBFE),
    onBackground     = Color(0xFF1C1B1F),
    onSurface        = Color(0xFF1C1B1F)
)

@Composable
fun TwoHeartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography  = Typography(),
        content     = content
    )
}
