package org.css_apps_m3.instagramphotooptimizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ExpressivePalette {
    DYNAMIC,
    MIDNIGHT,
    SUNSET,
    FOREST,
    MONO
}

enum class ExpressiveTypographyStyle {
    BALANCED,
    COMPACT,
    READING
}

data class ExpressiveThemeOptions(
    val useDarkTheme: Boolean,
    val palette: ExpressivePalette = ExpressivePalette.DYNAMIC,
    val typographyStyle: ExpressiveTypographyStyle = ExpressiveTypographyStyle.BALANCED,
    val useHighContrast: Boolean = false
)

private val MidnightLight = lightColorScheme(
    primary = Color(0xFF3253C8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF55617A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8B4A9E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF111424),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF111424),
    surfaceContainerHigh = Color(0xFFE4E7F3),
    outline = Color(0xFF72778C)
)

private val MidnightDark = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF002280),
    secondary = Color(0xFFBEC8E6),
    onSecondary = Color(0xFF283247),
    tertiary = Color(0xFFF0B0FF),
    onTertiary = Color(0xFF5A1E6C),
    background = Color(0xFF0F1320),
    onBackground = Color(0xFFE3E7FA),
    surface = Color(0xFF0F1320),
    onSurface = Color(0xFFE3E7FA),
    surfaceContainerHigh = Color(0xFF1C2234),
    outline = Color(0xFF8B91A8)
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFFAF3F20),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF76574D),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7A5F00),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF281712),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF281712),
    surfaceContainerHigh = Color(0xFFF8E5DF),
    outline = Color(0xFF8E6B61)
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF631B05),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A22),
    tertiary = Color(0xFFEBC248),
    onTertiary = Color(0xFF3F2E00),
    background = Color(0xFF20120E),
    onBackground = Color(0xFFFFDCD2),
    surface = Color(0xFF20120E),
    onSurface = Color(0xFFFFDCD2),
    surfaceContainerHigh = Color(0xFF33201A),
    outline = Color(0xFFA6847A)
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF196A3A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF496653),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF3E6475),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4FCF2),
    onBackground = Color(0xFF102015),
    surface = Color(0xFFF4FCF2),
    onSurface = Color(0xFF102015),
    surfaceContainerHigh = Color(0xFFDDEBDE),
    outline = Color(0xFF6F8776)
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFF8DD7A9),
    onPrimary = Color(0xFF00391A),
    secondary = Color(0xFFB3D1BB),
    onSecondary = Color(0xFF1C3524),
    tertiary = Color(0xFFA7CCDF),
    onTertiary = Color(0xFF063544),
    background = Color(0xFF0D1B12),
    onBackground = Color(0xFFD6E9D8),
    surface = Color(0xFF0D1B12),
    onSurface = Color(0xFFD6E9D8),
    surfaceContainerHigh = Color(0xFF1A2B1F),
    outline = Color(0xFF849C89)
)

private val MonoLight = lightColorScheme(
    primary = Color(0xFF3A3A3A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF727272),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F8F8),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF8F8F8),
    onSurface = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    outline = Color(0xFF7E7E7E)
)

private val MonoDark = darkColorScheme(
    primary = Color(0xFFD0D0D0),
    onPrimary = Color(0xFF262626),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color(0xFF2C2C2C),
    tertiary = Color(0xFF9A9A9A),
    onTertiary = Color(0xFF222222),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFF252525),
    outline = Color(0xFF9B9B9B)
)

private fun ColorScheme.withOptionalHighContrast(enabled: Boolean): ColorScheme {
    if (!enabled) return this
    return copy(
        outline = onSurface,
        surfaceContainerHigh = surface,
        onSurfaceVariant = onSurface,
        onSecondaryContainer = onSecondary
    )
}

@Composable
fun InstagramPhotoOptimizerTheme(
    options: ExpressiveThemeOptions,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val base = when (options.palette) {
        ExpressivePalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (options.useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (options.useDarkTheme) MidnightDark else MidnightLight
            }
        }
        ExpressivePalette.MIDNIGHT -> if (options.useDarkTheme) MidnightDark else MidnightLight
        ExpressivePalette.SUNSET -> if (options.useDarkTheme) SunsetDark else SunsetLight
        ExpressivePalette.FOREST -> if (options.useDarkTheme) ForestDark else ForestLight
        ExpressivePalette.MONO -> if (options.useDarkTheme) MonoDark else MonoLight
    }

    MaterialTheme(
        colorScheme = base.withOptionalHighContrast(options.useHighContrast),
        typography = expressiveTypography(options.typographyStyle),
        content = content
    )
}

@Composable
fun rememberDefaultExpressiveThemeOptions(): ExpressiveThemeOptions {
    return ExpressiveThemeOptions(useDarkTheme = isSystemInDarkTheme())
}
