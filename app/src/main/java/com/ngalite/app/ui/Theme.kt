package com.ngalite.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 论坛风格配色表。
 *
 * 设计思路参考经典 BBS / NGA 网页版：浅灰页面底色衬托白色内容块，用 1px 发丝线
 * 分隔信息行，链接蓝标识作者与板块，少量金色/红色用于楼主与热帖强调。
 * 这些颜色不进入 Material ColorScheme 的部分（如引用块、楼主徽标）放在这里，
 * 供各页面直接引用，避免魔法色值散落。
 */
object ForumColors {
    /** 页面底色：浅灰，衬托白色内容块 */
    val Page = Color(0xFFEDEFF3)

    /** 内容白 */
    val Surface = Color(0xFFFFFFFF)

    /** 发丝分隔线，比 Material 默认 outline 更淡，接近网页 1px 边框 */
    val Divider = Color(0xFFE3E7EC)

    /** 链接蓝：作者名、板块名等可点击文本 */
    val Link = Color(0xFF1F6FB2)

    /** 主色：沉稳的论坛蓝，用于选中态与强调 */
    val Accent = Color(0xFF2A5F8F)

    /** 主色浅底：选中标签、徽标底 */
    val AccentSoft = Color(0xFFEAF2FA)

    /** 楼主徽标 */
    val OwnerBg = Color(0xFFFFF3D6)
    val OwnerText = Color(0xFF9A6B00)

    /** 热帖强调 */
    val Hot = Color(0xFFC0392B)
    val HotSoft = Color(0xFFFDEDEB)

    /** 引用块：左侧竖线 + 浅灰底 */
    val QuoteBg = Color(0xFFF5F7F9)
    val QuoteBar = Color(0xFFB9C6D3)

    /** 次要信息文字（时间、回复数等） */
    val Meta = Color(0xFF8B95A1)

    /** 次级但需要突出的文字，例如列表右侧的回复数 */
    val Strong = Color(0xFF5A6572)

    /** 楼层号等最弱的文字 */
    val Floor = Color(0xFFA9B3BF)
}

private val ForumLightColors = lightColorScheme(
    primary = ForumColors.Accent,
    onPrimary = Color.White,
    primaryContainer = ForumColors.AccentSoft,
    onPrimaryContainer = Color(0xFF1B4670),
    secondary = Color(0xFF5B6B7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF2F6),
    onSecondaryContainer = Color(0xFF33414F),
    tertiary = ForumColors.OwnerText,
    onTertiary = Color.White,
    tertiaryContainer = ForumColors.OwnerBg,
    onTertiaryContainer = ForumColors.OwnerText,
    error = ForumColors.Hot,
    onError = Color.White,
    errorContainer = ForumColors.HotSoft,
    onErrorContainer = Color(0xFF8C2318),
    background = ForumColors.Page,
    onBackground = Color(0xFF1F2733),
    surface = ForumColors.Surface,
    onSurface = Color(0xFF1F2733),
    surfaceVariant = ForumColors.QuoteBg,
    onSurfaceVariant = ForumColors.Meta,
    outline = Color(0xFF9AA5B1),
    outlineVariant = ForumColors.Divider,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2A313B),
    inverseOnSurface = Color.White,
    inversePrimary = ForumColors.AccentSoft,
)

/** 论坛风格偏方正：只保留很小的圆角，避免"卡片 App"的观感 */
private val ForumShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** 论坛排版偏紧凑：信息密度高，行高小，标题字重更实 */
private val ForumTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
)

@Composable
fun NgaTheme(
    // NgaLite 固定使用浅色论坛风格，不跟随系统深色模式
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ForumLightColors,
        typography = ForumTypography,
        shapes = ForumShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
