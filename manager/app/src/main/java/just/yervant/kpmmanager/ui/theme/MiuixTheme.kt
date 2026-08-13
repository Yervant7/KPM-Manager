package just.yervant.kpmmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

@Composable
fun MiuixKPMTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val colorStyle = appSettings.paletteStyle
    val colorSpec = appSettings.colorSpec

    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(colorStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }

    val miuixColorSpec = if (colorSpec == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    val controller = ThemeController(
        when (appSettings.colorMode) {
            ColorMode.SYSTEM -> ColorSchemeMode.System
            ColorMode.LIGHT -> ColorSchemeMode.Light
            ColorMode.DARK -> ColorSchemeMode.Dark
            ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
            ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
            ColorMode.MONET_DARK, ColorMode.DARK_AMOLED -> ColorSchemeMode.MonetDark
        },
        keyColor = if (appSettings.keyColor == 0) null else Color(appSettings.keyColor),
        isDark = darkTheme,
        paletteStyle = miuixPaletteStyle,
        colorSpec = miuixColorSpec,
    )

    MiuixTheme(
        controller = controller,
        content = {
            val miuixColors = MiuixTheme.colorScheme
            val materialColorScheme = if (darkTheme) {
                androidx.compose.material3.darkColorScheme(
                    primary = miuixColors.primary,
                    onPrimary = miuixColors.onPrimary,
                    primaryContainer = miuixColors.primaryContainer,
                    onPrimaryContainer = miuixColors.onPrimaryContainer,
                    secondary = miuixColors.secondary,
                    onSecondary = miuixColors.onSecondary,
                    secondaryContainer = miuixColors.secondaryContainer,
                    onSecondaryContainer = miuixColors.onSecondaryContainer,
                    tertiary = miuixColors.primary,
                    onTertiary = miuixColors.onPrimary,
                    background = miuixColors.background,
                    onBackground = miuixColors.onBackground,
                    surface = miuixColors.surface,
                    onSurface = miuixColors.onSurface,
                    surfaceVariant = miuixColors.surfaceVariant,
                    onSurfaceVariant = miuixColors.onSurfaceVariantSummary,
                    outline = miuixColors.outline,
                    outlineVariant = miuixColors.dividerLine,
                    error = miuixColors.error,
                    onError = miuixColors.onError,
                    errorContainer = miuixColors.errorContainer,
                    onErrorContainer = miuixColors.onErrorContainer,
                    surfaceContainer = miuixColors.surfaceContainer,
                    surfaceContainerHigh = miuixColors.surfaceContainerHigh,
                    surfaceContainerHighest = miuixColors.surfaceContainerHighest,
                    surfaceContainerLow = miuixColors.surfaceContainer,
                    surfaceContainerLowest = miuixColors.surface
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    primary = miuixColors.primary,
                    onPrimary = miuixColors.onPrimary,
                    primaryContainer = miuixColors.primaryContainer,
                    onPrimaryContainer = miuixColors.onPrimaryContainer,
                    secondary = miuixColors.secondary,
                    onSecondary = miuixColors.onSecondary,
                    secondaryContainer = miuixColors.secondaryContainer,
                    onSecondaryContainer = miuixColors.onSecondaryContainer,
                    tertiary = miuixColors.primary,
                    onTertiary = miuixColors.onPrimary,
                    background = miuixColors.background,
                    onBackground = miuixColors.onBackground,
                    surface = miuixColors.surface,
                    onSurface = miuixColors.onSurface,
                    surfaceVariant = miuixColors.surfaceVariant,
                    onSurfaceVariant = miuixColors.onSurfaceVariantSummary,
                    outline = miuixColors.outline,
                    outlineVariant = miuixColors.dividerLine,
                    error = miuixColors.error,
                    onError = miuixColors.onError,
                    errorContainer = miuixColors.errorContainer,
                    onErrorContainer = miuixColors.onErrorContainer,
                    surfaceContainer = miuixColors.surfaceContainer,
                    surfaceContainerHigh = miuixColors.surfaceContainerHigh,
                    surfaceContainerHighest = miuixColors.surfaceContainerHighest,
                    surfaceContainerLow = miuixColors.surfaceContainer,
                    surfaceContainerLowest = miuixColors.surface
                )
            }

            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            ) {
                MaterialTheme(
                    colorScheme = materialColorScheme,
                    typography = Typography,
                    content = content
                )
            }
        }
    )
}
