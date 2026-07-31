package app.calorease.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 配色直接从网页版的 CSS 变量搬过来,一个色值都没改 ——
 * 原生化换的是手感,不是长相。
 *
 *     --canvas #E4EAE3   页面底色
 *     --paper  #F8FAF6   卡片
 *     --ink    #18241E   正文
 *     --muted  #78877E   次要文字
 *     --line   #C9D3C6   分隔线与描边
 *     --intake #A87C15   摄入(赭金)
 *     --burn   #1D6B67   消耗(墨绿)
 *     --warn   #8C4A2F   超标与危险操作
 *
 * 深色是新加的(归档第七节列在「还没做」里)。做法是保持色相不动,
 * 只翻转明度,并把两个强调色提亮 —— 赭金和墨绿在深底上原样太闷,读不出来。
 */
@Immutable
data class CaloreaseColors(
    val canvas: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val intake: Color,
    val burn: Color,
    val warn: Color,
    /** 面板/底栏的背景 */
    val sheet: Color,
    val isDark: Boolean,
)

private val LightColors = CaloreaseColors(
    canvas = Color(0xFFE4EAE3),
    paper = Color(0xFFF8FAF6),
    ink = Color(0xFF18241E),
    muted = Color(0xFF78877E),
    line = Color(0xFFC9D3C6),
    intake = Color(0xFFA87C15),
    burn = Color(0xFF1D6B67),
    warn = Color(0xFF8C4A2F),
    sheet = Color(0xFFF3F7F1),
    isDark = false,
)

private val DarkColors = CaloreaseColors(
    canvas = Color(0xFF141A16),
    paper = Color(0xFF1C241E),
    ink = Color(0xFFE6EDE7),
    muted = Color(0xFF8E9C93),
    line = Color(0xFF2E3A31),
    intake = Color(0xFFD9A93A),
    burn = Color(0xFF4FB3AC),
    warn = Color(0xFFC97A57),
    sheet = Color(0xFF20291F).copy(alpha = 1f),
    isDark = true,
)

val LocalColors: ProvidableCompositionLocal<CaloreaseColors> =
    staticCompositionLocalOf { LightColors }

/** 数字一律用等宽字形,不然一列数字对不齐,看趋势的时候很难受 */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
)

private val CaloreaseTypography = Typography(
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.1.sp),
)

object Dimens {
    val screenPadding = 16.dp
    val cardRadius = 14.dp
    val gap = 10.dp
    val tabBarHeight = 60.dp
}

@Composable
fun CaloreaseTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    // Material3 的组件(输入框、开关、对话框)会去读 ColorScheme,
    // 所以把同一套色值也映射一份进去,免得出现两种绿。
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.burn,
            onPrimary = Color(0xFF08120F),
            secondary = colors.intake,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.sheet,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.warn,
        )
    } else {
        lightColorScheme(
            primary = colors.burn,
            onPrimary = Color.White,
            secondary = colors.intake,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.sheet,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            error = colors.warn,
        )
    }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTextStyle provides CaloreaseTypography.bodyMedium.copy(color = colors.ink),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CaloreaseTypography,
            content = content,
        )
    }
}
