package com.ngalite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngalite.app.data.FontScale
import com.ngalite.app.data.ThemeMode

/**
 * 现代社区语义色板：支持浅色与深色双主题，融合 NGA 暖琥珀品牌色与现代高可读中性色阶。
 */
@Immutable
data class NgaColorPalette(
    val isDark: Boolean,
    val page: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val divider: Color,
    val accent: Color,
    val accentSoft: Color,
    val link: Color,
    val linkSoft: Color,
    val ownerBg: Color,
    val ownerText: Color,
    val hot: Color,
    val hotSoft: Color,
    val quoteBg: Color,
    val quoteBar: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val meta: Color,
    val strong: Color,
    val floor: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
)

private val LightPalette = NgaColorPalette(
    isDark = false,
    page = Color(0xFFF3F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFECEFF4),
    divider = Color(0xFFE6EAF0),
    accent = Color(0xFFD05420),
    accentSoft = Color(0xFFFFEFE8),
    link = Color(0xFF2563EB),
    linkSoft = Color(0xFFEFF6FF),
    ownerBg = Color(0xFFFEF3C7),
    ownerText = Color(0xFFB45309),
    hot = Color(0xFFDC2626),
    hotSoft = Color(0xFFFEE2E2),
    quoteBg = Color(0xFFF6F7FA),
    quoteBar = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF141820),
    textSecondary = Color(0xFF4B5563),
    meta = Color(0xFF7D8796),
    strong = Color(0xFF475569),
    floor = Color(0xFF9CA6B4),
    shimmerBase = Color(0xFFE6E9EF),
    shimmerHighlight = Color(0xFFF4F6F9),
)

private val DarkPalette = NgaColorPalette(
    isDark = true,
    page = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    surfaceElevated = Color(0xFF1E242D),
    surfaceMuted = Color(0xFF232933),
    divider = Color(0xFF262D38),
    accent = Color(0xFFF07A48),
    accentSoft = Color(0xFF3A2218),
    link = Color(0xFF60A5FA),
    linkSoft = Color(0xFF1A2942),
    ownerBg = Color(0xFF382910),
    ownerText = Color(0xFFFBBF24),
    hot = Color(0xFFF87171),
    hotSoft = Color(0xFF3A1A1D),
    quoteBg = Color(0xFF1E232B),
    quoteBar = Color(0xFF3F4B5C),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFCBD5E1),
    meta = Color(0xFF8894A4),
    strong = Color(0xFFB3C0D1),
    floor = Color(0xFF6B7685),
    shimmerBase = Color(0xFF1F242D),
    shimmerHighlight = Color(0xFF2A313D),
)

val LocalAppColors = staticCompositionLocalOf { LightPalette }

/**
 * 兼容且语义化的全局颜色访问入口，自动响应当前浅色/深色主题。
 */
object ForumColors {
    val current: NgaColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val Page: Color @Composable @ReadOnlyComposable get() = current.page
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = current.surfaceElevated
    val SurfaceMuted: Color @Composable @ReadOnlyComposable get() = current.surfaceMuted
    val Divider: Color @Composable @ReadOnlyComposable get() = current.divider
    val Accent: Color @Composable @ReadOnlyComposable get() = current.accent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = current.accentSoft
    val Link: Color @Composable @ReadOnlyComposable get() = current.link
    val LinkSoft: Color @Composable @ReadOnlyComposable get() = current.linkSoft
    val OwnerBg: Color @Composable @ReadOnlyComposable get() = current.ownerBg
    val OwnerText: Color @Composable @ReadOnlyComposable get() = current.ownerText
    val Hot: Color @Composable @ReadOnlyComposable get() = current.hot
    val HotSoft: Color @Composable @ReadOnlyComposable get() = current.hotSoft
    val QuoteBg: Color @Composable @ReadOnlyComposable get() = current.quoteBg
    val QuoteBar: Color @Composable @ReadOnlyComposable get() = current.quoteBar
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = current.textSecondary
    val Meta: Color @Composable @ReadOnlyComposable get() = current.meta
    val Strong: Color @Composable @ReadOnlyComposable get() = current.strong
    val Floor: Color @Composable @ReadOnlyComposable get() = current.floor
}

private fun buildColorScheme(palette: NgaColorPalette) = if (palette.isDark) {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF1F0C04),
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = Color(0xFFFFD8C7),
        secondary = palette.link,
        onSecondary = Color(0xFF0A192F),
        secondaryContainer = palette.linkSoft,
        onSecondaryContainer = Color(0xFFBFDBFE),
        tertiary = palette.ownerText,
        onTertiary = Color(0xFF281800),
        tertiaryContainer = palette.ownerBg,
        onTertiaryContainer = palette.ownerText,
        error = palette.hot,
        onError = Color(0xFF2E0707),
        errorContainer = palette.hotSoft,
        onErrorContainer = Color(0xFFFECACA),
        background = palette.page,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.quoteBg,
        onSurfaceVariant = palette.meta,
        outline = palette.floor,
        outlineVariant = palette.divider,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF0F172A),
        inversePrimary = palette.accentSoft,
    )
} else {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = Color.White,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = Color(0xFF7A2A0A),
        secondary = palette.link,
        onSecondary = Color.White,
        secondaryContainer = palette.linkSoft,
        onSecondaryContainer = Color(0xFF1E40AF),
        tertiary = palette.ownerText,
        onTertiary = Color.White,
        tertiaryContainer = palette.ownerBg,
        onTertiaryContainer = palette.ownerText,
        error = palette.hot,
        onError = Color.White,
        errorContainer = palette.hotSoft,
        onErrorContainer = Color(0xFF991B1B),
        background = palette.page,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.quoteBg,
        onSurfaceVariant = palette.meta,
        outline = palette.floor,
        outlineVariant = palette.divider,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF1E293B),
        inverseOnSurface = Color.White,
        inversePrimary = palette.accentSoft,
    )
}

private val ModernShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private fun buildTypography(scale: Float): Typography {
    fun spScaled(base: Float) = (base * scale).sp
    return Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleLarge = TextStyle(fontSize = spScaled(19f), fontWeight = FontWeight.Bold, lineHeight = spScaled(26f), letterSpacing = (-0.15).sp),
        titleMedium = TextStyle(fontSize = spScaled(16f), fontWeight = FontWeight.SemiBold, lineHeight = spScaled(23f)),
        titleSmall = TextStyle(fontSize = spScaled(15f), fontWeight = FontWeight.SemiBold, lineHeight = spScaled(21f)),
        bodyLarge = TextStyle(fontSize = spScaled(16f), lineHeight = spScaled(25f)),
        bodyMedium = TextStyle(fontSize = spScaled(15f), lineHeight = spScaled(23f)),
        bodySmall = TextStyle(fontSize = spScaled(13f), lineHeight = spScaled(19f)),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
    )
}

@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NgaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: FontScale = FontScale.NORMAL,
    content: @Composable () -> Unit
) {
    val isDark = shouldUseDarkTheme(themeMode)
    val palette = if (isDark) DarkPalette else LightPalette
    val colorScheme = buildColorScheme(palette)
    val typography = buildTypography(fontScale.scale)

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = ModernShapes,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    }
}
