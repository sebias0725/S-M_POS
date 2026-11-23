package com.shoptracker.pos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object LocalThemePreference {
    var dark by androidx.compose.runtime.mutableStateOf(false)
}

private val Ember = Color(0xFFD21F0D)
private val DarkEmber = Color(0xFFAD190B)
private val SoftEmber = Color(0xFFFFB400)
private val Ink = Color(0xFF211A17)

private val LightColors = lightColorScheme(
    primary = DarkEmber, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9D4), onPrimaryContainer = Ink,
    secondary = Color(0xFFAA2415), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5D7), onSecondaryContainer = Ink,
    tertiary = Color(0xFF79523D), onTertiary = Color.White,
    surface = Color(0xFFFFFBFA), background = Color(0xFFFFFBFA),
    onSurface = Ink, onBackground = Ink,
    surfaceVariant = Color(0xFFF5E9E1), onSurfaceVariant = Color(0xFF55473E),
    outline = Color(0xFF87766B)
)

private val DarkColors = darkColorScheme(
    primary = SoftEmber, onPrimary = Ink,
    primaryContainer = Color(0xFF753310), onPrimaryContainer = Color(0xFFFFE2D1),
    secondary = Color(0xFFFFB386), onSecondary = Ink,
    secondaryContainer = Color(0xFF57321E), onSecondaryContainer = Color(0xFFFFE4D2),
    tertiary = Color(0xFFE7AD8A), onTertiary = Ink,
    surface = Color(0xFF191B1B), background = Color(0xFF121515),
    onSurface = Color(0xFFF5F0EC), onBackground = Color(0xFFF5F0EC),
    surfaceVariant = Color(0xFF292D2C), onSurfaceVariant = Color(0xFFD8CCC4),
    outline = Color(0xFF9E8D82)
)

/** Branded orange controls with a separate, readable foreground for each theme. */
@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    val dark = LocalThemePreference.dark
    // The bundled CHANOS orange is tuned separately for each theme so text stays legible.
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun positiveColor(): Color = if (LocalThemePreference.dark) Color(0xFF80D99A) else Color(0xFF24763B)

@Composable
fun negativeColor(): Color = if (LocalThemePreference.dark) Color(0xFFFF9187) else Color(0xFFB52C26)

@Composable
fun neutralColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun deltaColor(delta: Long): Color = when {
    delta > 0 -> positiveColor()
    delta < 0 -> negativeColor()
    else -> neutralColor()
}
