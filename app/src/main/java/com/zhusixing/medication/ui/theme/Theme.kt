package com.zhusixing.medication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局配色与字号。
 *
 * 字号整体比 Material3 默认值大一档，是「适老化」这件事在代码里的落点：
 * 老人端首先是能看清，其次才是好看。行高同步放大，避免大字号挤在一起。
 *
 * 不跟随系统深色模式：本产品的核心场景是老人白天在家服药，单套浅色方案已经验证过对比度，
 * 再多一套深色需要重新验一遍，收益不抵成本。真要做，在 colorScheme 这里加分支即可。
 */
private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF147D83),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F1EF),
    onPrimaryContainer = Color(0xFF12484C),
    secondary = Color(0xFF4F6B6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EFEF),
    onSecondaryContainer = Color(0xFF2C4245),
    background = Color(0xFFF3F7F8),
    onBackground = Color(0xFF19272A),
    surface = Color.White,
    onSurface = Color(0xFF19272A),
    surfaceVariant = Color(0xFFE7EFEF),
    onSurfaceVariant = Color(0xFF52686B),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF9AAEB1),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MedicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = AppTypography, content = content)
}
