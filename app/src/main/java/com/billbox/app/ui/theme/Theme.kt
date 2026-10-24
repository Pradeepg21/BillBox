package com.billbox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF475569),
    secondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFB45309),
    tertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFFF7F9FA),
    surface = Color(0xFFF7F9FA),
    surfaceContainer = Color(0xFFEFF3F4),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8EDEF),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFCBD5E1),
    secondaryContainer = Color(0xFF334155),
    tertiary = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFF78350F),
    background = Color(0xFF101415),
    surface = Color(0xFF101415),
    surfaceContainer = Color(0xFF1A2022),
    surfaceContainerLow = Color(0xFF161B1C),
    surfaceContainerHigh = Color(0xFF242B2D),
    error = Color(0xFFF87171),
)

/** Status colours used for warranty and budget badges. */
object StatusColors {
    val good = Color(0xFF16A34A)
    val warning = Color(0xFFD97706)
    val critical = Color(0xFFDC2626)
}

@Composable
fun BillBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
