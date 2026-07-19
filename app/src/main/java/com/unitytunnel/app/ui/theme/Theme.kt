package com.unitytunnel.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = GoldColor,
  onPrimary = BgColor,
  secondary = TealColor,
  onSecondary = BgColor,
  background = BgColor,
  onBackground = TextColor,
  surface = SurfaceColor,
  onSurface = TextColor,
  surfaceVariant = SurfaceAltColor,
  onSurfaceVariant = TextDimColor,
  tertiary = TealColor
)

private val LightColorScheme = lightColorScheme(
  primary = LightGoldColor,
  onPrimary = Color.White,
  secondary = LightTealColor,
  onSecondary = Color.White,
  background = LightBgColor,
  onBackground = LightTextColor,
  surface = LightSurfaceColor,
  onSurface = LightTextColor,
  surfaceVariant = LightSurfaceAltColor,
  onSurfaceVariant = LightTextDimColor,
  tertiary = LightTealColor
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
