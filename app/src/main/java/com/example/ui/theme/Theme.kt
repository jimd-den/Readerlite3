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
import com.example.domain.model.ColorThemeOption

private val CosmicColorScheme = darkColorScheme(
  primary = CosmicPrimary,
  onPrimary = CosmicOnPrimary,
  primaryContainer = CosmicSurfaceVariant,
  onPrimaryContainer = CosmicPrimary,
  secondary = CosmicPrimary,
  onSecondary = CosmicOnPrimary,
  secondaryContainer = CosmicSurfaceVariant,
  onSecondaryContainer = CosmicPrimary,
  background = CosmicBackground,
  onBackground = CosmicOnSurface,
  surface = CosmicSurface,
  onSurface = CosmicOnSurface,
  surfaceVariant = CosmicSurfaceVariant,
  onSurfaceVariant = CosmicOnSurfaceVariant,
  outline = CosmicOnSurfaceVariant.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
  primary = LightMinimalPrimary,
  onPrimary = LightMinimalOnPrimary,
  primaryContainer = LightMinimalSurfaceVariant,
  onPrimaryContainer = LightMinimalPrimary,
  secondary = LightMinimalPrimary,
  onSecondary = LightMinimalOnPrimary,
  secondaryContainer = LightMinimalSurfaceVariant,
  onSecondaryContainer = LightMinimalPrimary,
  background = LightMinimalBackground,
  onBackground = LightMinimalOnSurface,
  surface = LightMinimalSurface,
  onSurface = LightMinimalOnSurface,
  surfaceVariant = LightMinimalSurfaceVariant,
  onSurfaceVariant = LightMinimalOnSurfaceVariant,
  outline = LightMinimalOnSurfaceVariant.copy(alpha = 0.5f)
)

private val SepiaColorScheme = lightColorScheme(
  primary = SepiaPrimary,
  onPrimary = SepiaOnPrimary,
  primaryContainer = SepiaSurfaceVariant,
  onPrimaryContainer = SepiaPrimary,
  secondary = SepiaPrimary,
  onSecondary = SepiaOnPrimary,
  secondaryContainer = SepiaSurfaceVariant,
  onSecondaryContainer = SepiaPrimary,
  background = SepiaBackground,
  onBackground = SepiaOnSurface,
  surface = SepiaSurface,
  onSurface = SepiaOnSurface,
  surfaceVariant = SepiaSurfaceVariant,
  onSurfaceVariant = SepiaOnSurfaceVariant,
  outline = SepiaOnSurfaceVariant.copy(alpha = 0.5f)
)

private val SunsetColorScheme = darkColorScheme(
  primary = SunsetPrimary,
  onPrimary = SunsetOnPrimary,
  primaryContainer = SunsetSurfaceVariant,
  onPrimaryContainer = SunsetPrimary,
  secondary = SunsetPrimary,
  onSecondary = SunsetOnPrimary,
  secondaryContainer = SunsetSurfaceVariant,
  onSecondaryContainer = SunsetPrimary,
  background = SunsetBackground,
  onBackground = SunsetOnSurface,
  surface = SunsetSurface,
  onSurface = SunsetOnSurface,
  surfaceVariant = SunsetSurfaceVariant,
  onSurfaceVariant = SunsetOnSurfaceVariant,
  outline = SunsetOnSurfaceVariant.copy(alpha = 0.5f)
)

private val ForestColorScheme = darkColorScheme(
  primary = ForestPrimary,
  onPrimary = ForestOnPrimary,
  primaryContainer = ForestSurfaceVariant,
  onPrimaryContainer = ForestPrimary,
  secondary = ForestPrimary,
  onSecondary = ForestOnPrimary,
  secondaryContainer = ForestSurfaceVariant,
  onSecondaryContainer = ForestPrimary,
  background = ForestBackground,
  onBackground = ForestOnSurface,
  surface = ForestSurface,
  onSurface = ForestOnSurface,
  surfaceVariant = ForestSurfaceVariant,
  onSurfaceVariant = ForestOnSurfaceVariant,
  outline = ForestOnSurfaceVariant.copy(alpha = 0.5f)
)

private val ObsidianColorScheme = darkColorScheme(
  primary = ObsidianPrimary,
  onPrimary = ObsidianOnPrimary,
  primaryContainer = ObsidianSurfaceVariant,
  onPrimaryContainer = ObsidianPrimary,
  secondary = ObsidianPrimary,
  onSecondary = ObsidianOnPrimary,
  secondaryContainer = ObsidianSurfaceVariant,
  onSecondaryContainer = ObsidianPrimary,
  background = ObsidianBackground,
  onBackground = ObsidianOnSurface,
  surface = ObsidianSurface,
  onSurface = ObsidianOnSurface,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = ObsidianOnSurfaceVariant,
  outline = ObsidianOnSurfaceVariant.copy(alpha = 0.5f)
)

@Composable
fun MyApplicationTheme(
  selectedTheme: ColorThemeOption = ColorThemeOption.COSMIC_SLATE,
  content: @Composable () -> Unit,
) {
  val colorScheme = when (selectedTheme) {
    ColorThemeOption.COSMIC_SLATE -> CosmicColorScheme
    ColorThemeOption.LIGHT_MINIMAL -> LightColorScheme
    ColorThemeOption.SEPIA_VINTAGE -> SepiaColorScheme
    ColorThemeOption.WARM_SUNSET -> SunsetColorScheme
    ColorThemeOption.FOREST_INK -> ForestColorScheme
    ColorThemeOption.OBSIDIAN_OLED -> ObsidianColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

