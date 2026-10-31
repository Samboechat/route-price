package com.routeprice.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Paleta base. Escuro: grafite profundo + verde-limão. Claro: off-white + verde-oliva legível. */
internal object Palette {
    val Ink = Color(0xFF0A0B0D)
    val Graphite900 = Color(0xFF111316)
    val Graphite800 = Color(0xFF171A1E)
    val Graphite700 = Color(0xFF1E2227)
    val Graphite600 = Color(0xFF272C33)
    val Graphite500 = Color(0xFF353B44)
    val Graphite300 = Color(0xFF8A939E)
    val Graphite200 = Color(0xFFB4BCC5)
    val Graphite50 = Color(0xFFF1F3F4)

    val Lime = Color(0xFFC6F45A)
    val LimeDeep = Color(0xFF3F6A00)
    val LimeContainerLight = Color(0xFFDDF5A6)
    val LimeContainerDark = Color(0xFF2B3A0E)
    val Emerald = Color(0xFF2ED3A0)
    val EmeraldDeep = Color(0xFF00805C)

    val Sky = Color(0xFF6CC4FF)
    val SkyDeep = Color(0xFF0B67A8)
    val Amber = Color(0xFFF5BE4F)
    val AmberDeep = Color(0xFF8A5A00)
    val Coral = Color(0xFFFF6B6B)
    val CoralDeep = Color(0xFFB3261E)

    val Paper = Color(0xFFF6F7F4)
    val PaperHigh = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFE2E5E0)
}

internal val DarkScheme = darkColorScheme(
    primary = Palette.Lime,
    onPrimary = Palette.Ink,
    primaryContainer = Palette.LimeContainerDark,
    onPrimaryContainer = Palette.Lime,
    secondary = Palette.Emerald,
    onSecondary = Palette.Ink,
    tertiary = Palette.Sky,
    background = Palette.Ink,
    onBackground = Palette.Graphite50,
    surface = Palette.Ink,
    onSurface = Palette.Graphite50,
    surfaceVariant = Palette.Graphite700,
    onSurfaceVariant = Palette.Graphite200,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Graphite900,
    surfaceContainer = Palette.Graphite800,
    surfaceContainerHigh = Palette.Graphite700,
    surfaceContainerHighest = Palette.Graphite600,
    outline = Palette.Graphite500,
    outlineVariant = Palette.Graphite600,
    error = Palette.Coral,
    onError = Palette.Ink,
    inverseSurface = Palette.Graphite50,
    inverseOnSurface = Palette.Ink,
    scrim = Color(0xCC000000),
)

internal val LightScheme = lightColorScheme(
    primary = Palette.LimeDeep,
    onPrimary = Color.White,
    primaryContainer = Palette.LimeContainerLight,
    onPrimaryContainer = Color(0xFF1C2B00),
    secondary = Palette.EmeraldDeep,
    onSecondary = Color.White,
    tertiary = Palette.SkyDeep,
    background = Palette.Paper,
    onBackground = Color(0xFF111315),
    surface = Palette.Paper,
    onSurface = Color(0xFF111315),
    surfaceVariant = Color(0xFFE8EBE6),
    onSurfaceVariant = Color(0xFF4A515A),
    surfaceContainerLowest = Palette.PaperHigh,
    surfaceContainerLow = Color(0xFFFBFCFA),
    surfaceContainer = Palette.PaperHigh,
    surfaceContainerHigh = Color(0xFFF0F2EE),
    surfaceContainerHighest = Color(0xFFE8EBE6),
    outline = Color(0xFFC5CAC2),
    outlineVariant = Palette.PaperLine,
    error = Palette.CoralDeep,
    onError = Color.White,
)

/** Cores semânticas além do Material: fonte do dado e variação financeira. */
@Immutable
data class ExtendedColors(
    val sourceExternal: Color,
    val sourceDriver: Color,
    val sourceEstimated: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val mapRoute: Color,
    val mapRouteAlternative: Color,
)

internal val DarkExtended = ExtendedColors(
    sourceExternal = Palette.Sky,
    sourceDriver = Palette.Lime,
    sourceEstimated = Palette.Amber,
    positive = Palette.Emerald,
    negative = Palette.Coral,
    warning = Palette.Amber,
    mapRoute = Palette.Lime,
    mapRouteAlternative = Palette.Graphite300,
)

internal val LightExtended = ExtendedColors(
    sourceExternal = Palette.SkyDeep,
    sourceDriver = Palette.LimeDeep,
    sourceEstimated = Palette.AmberDeep,
    positive = Palette.EmeraldDeep,
    negative = Palette.CoralDeep,
    warning = Palette.AmberDeep,
    mapRoute = Palette.LimeDeep,
    mapRouteAlternative = Color(0xFF8A939E),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }
