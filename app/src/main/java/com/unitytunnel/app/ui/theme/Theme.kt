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

private val LightColorScheme = DarkColorScheme // Keep same style for cohesive dark-only prepaid dashboard

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark prepaid theme
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
