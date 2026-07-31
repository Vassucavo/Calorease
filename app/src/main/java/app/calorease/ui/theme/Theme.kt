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
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    // ---------- 玻璃质感 ----------
    // 网页版里这是**默认开着**的:applyGlass() 写的是
    // classList.toggle("glass", !profile || profile.glass !== false),
    // 只有显式设成 false 才关。所以这套半透明表面才是这个应用的基准外观,
    // 不是可选装饰。我第一版把它当成可选项没做,整个视觉基调就丢了。
    val glass: Boolean,
    /** 卡片:比其他表面再透一点 */
    val cardBg: Color,
    /** 条目、快捷按钮、次要按钮这些内部元素 */
    val rowBg: Color,
    /** 输入框要读得清,透明度给高一点 */
    val inputBg: Color,
    /** 玻璃态下的描边是高光白,不是分隔线色 */
    val surfaceBorder: Color,
    /** 浮层、底栏这类顶层固定面 */
    val panelBg: Color,
    val panelBorder: Color,
    /** 浮层背后的遮罩 */
    val scrim: Color,
)

/**
 * 页面背景。玻璃态下是三层径向渐变叠一层线性渐变,数值照抄 body.glass:
 *
 *   radial(900x520 at 8% -6%)   #F4F8F0 → 透明 62%
 *   radial(760x460 at 108% 14%) #D6E5E1 → 透明 58%
 *   radial(680x520 at 50% 108%) #E9EFE6 → 透明 60%
 *   linear(180deg)              #E9EFE7 → #DDE6DB
 *
 * CSS 的径向渐变可以是椭圆(两个半径),Compose 的 radialGradient 只有一个
 * 半径,所以这里按较大的那一边取,视觉上差别很小。
 */
fun Modifier.pageBackground(c: CaloreaseColors): Modifier = this.drawBehind {
    if (!c.glass) {
        drawRect(c.canvas)
        return@drawBehind
    }
    // 底层:自上而下的线性渐变
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFFE9EFE7), Color(0xFFDDE6DB)),
            startY = 0f,
            endY = size.height,
        )
    )
    // 三层径向光晕。CSS 里是椭圆(两个半径),Compose 的 radialGradient 只有
    // 一个半径,这里取两者的较大值 —— 这几层本来就很淡,差别看不出来。
    fun glow(color: Color, xPct: Float, yPct: Float, rx: Float, ry: Float, stop: Float) {
        val r = maxOf(rx, ry) * density
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color, stop to color.copy(alpha = 0f)),
                center = Offset(size.width * xPct, size.height * yPct),
                radius = r,
            )
        )
    }
    glow(Color(0xFFF4F8F0), 0.08f, -0.06f, 900f, 520f, 0.62f)
    glow(Color(0xFFD6E5E1), 1.08f, 0.14f, 760f, 460f, 0.58f)
    glow(Color(0xFFE9EFE6), 0.50f, 1.08f, 680f, 520f, 0.60f)
}

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
    glass = true,
    cardBg = Color(0xFFFDFEFC).copy(alpha = 0.66f),
    rowBg = Color(0xFFFDFEFC).copy(alpha = 0.60f),
    inputBg = Color(0xFFFEFFFD).copy(alpha = 0.82f),
    surfaceBorder = Color.White.copy(alpha = 0.78f),
    // 浮层要靠背后那层真实模糊撑出磨砂感,所以不能太不透明 ——
    // 之前调到 0.94 是因为当时还没有模糊,面一透就只能看见清晰的内容,很脏。
    panelBg = Color(0xFFFAFCF8).copy(alpha = 0.74f),
    panelBorder = Color.White.copy(alpha = 0.62f),
    scrim = Color(0xFF18241E).copy(alpha = 0.30f),
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
    sheet = Color(0xFF20291F),
    isDark = true,
    // 深色下不做玻璃 —— 网页版没有深色态,那套半透明白面反过来没有对应物,
    // 硬凑只会两头不像。深色走实色,浅色(默认)走玻璃。
    glass = false,
    cardBg = Color(0xFF1C241E),
    rowBg = Color(0xFF1C241E),
    inputBg = Color(0xFF1C241E),
    surfaceBorder = Color(0xFF2E3A31),
    panelBg = Color(0xFF171E19),
    panelBorder = Color(0xFF2E3A31),
    scrim = Color(0xFF000000).copy(alpha = 0.55f),
)

/** 设置里关掉玻璃之后的浅色态:表面回到实色的 paper / line */
private val FlatLightColors = LightColors.copy(
    glass = false,
    cardBg = LightColors.paper,
    rowBg = LightColors.paper,
    inputBg = LightColors.paper,
    surfaceBorder = LightColors.line,
    panelBg = LightColors.canvas,
    panelBorder = LightColors.line,
    scrim = Color(0xFF18241E).copy(alpha = 0.5f),
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
    /** 玻璃质感。网页版默认开着,只有用户在设置里关掉才是 false */
    glass: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dark -> DarkColors
        glass -> LightColors
        else -> FlatLightColors
    }
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
