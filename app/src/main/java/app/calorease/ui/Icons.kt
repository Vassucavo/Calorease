package app.calorease.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标。
 *
 * 路径数据来自网页版 index.html 的 ICONS 常量,几何形状一模一样,
 * 但**参数被重新分隔过**,原因如下。
 *
 * 原版是压缩写法,把椭圆弧的两个标志位和后面的坐标粘在一起:
 *
 *     a2 2 0 012 2     ← rx=2 ry=2 旋转=0 大弧=0 扫向=1 x=2 y=2
 *
 * 这在 SVG 规范里合法(标志位只能是单个 0 或 1,所以不需要分隔符),
 * 但 Compose 的 PathParser 是按数字文法切词的,会把 `012` 读成数字 12,
 * 之后整组参数全部错位 —— 结果就是含弧的图标(日历、时钟、齿轮)画出来
 * 是扭曲的,而只有直线的(秤、推子)完全正常。这个对照本身就是诊断线索。
 *
 * 所以这里把每个参数都显式空格分开,标志位单独成词。几何没动。
 *
 * 描边参数也照抄原来的 SVG 属性:viewBox 0 0 24 24、stroke-width 1.7、
 * linecap round、linejoin round、fill none。
 */
data class VectorIcon(
    val paths: List<String>,
    /** SVG 里的 <circle cx cy r>,PathParser 不认,单独画 */
    val circles: List<Triple<Float, Float, Float>> = emptyList(),
)

object Icons {
    val Today = VectorIcon(listOf("M 3 9 h 18 M 7 3 v 3 M 17 3 v 3 M 5 5 h 14 a 2 2 0 0 1 2 2 v 12 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 V 7 a 2 2 0 0 1 2 -2 z"))

    val Weight = VectorIcon(
        paths = listOf("M 5 21 h 14 l -2 -11 H 7 L 5 21 z"),
        circles = listOf(Triple(12f, 6f, 2.5f)),
    )

    val Tune = VectorIcon(listOf("M 4 20 V 10 M 12 20 V 4 M 20 20 v -6 M 1 10 h 6 M 9 4 h 6 M 17 14 h 6"))

    val Logs = VectorIcon(listOf("M 3 12 a 9 9 0 1 0 9 -9 9 9 0 0 0 -6.4 2.7 L 3 8 M 3 3 v 5 h 5 M 12 7 v 5 l 3 2"))

    val Settings = VectorIcon(
        paths = listOf(
            "M 19.4 15 a 1.6 1.6 0 0 0 .3 1.8 l .1 .1 a 2 2 0 1 1 -2.8 2.8 l -.1 -.1 a 1.6 1.6 0 0 0 -1" +
            ".8 -.3 1.6 1.6 0 0 0 -1 1.5 V 21 a 2 2 0 1 1 -4 0 v -.1 a 1.6 1.6 0 0 0 -1 -1.5 1.6 1.6 0 " +
            "0 0 -1.8 .3 l -.1 .1 a 2 2 0 1 1 -2.8 -2.8 l .1 -.1 a 1.6 1.6 0 0 0 .3 -1.8 1.6 1.6 0 0 0 " +
            "-1.5 -1 H 3 a 2 2 0 1 1 0 -4 h .1 a 1.6 1.6 0 0 0 1.5 -1 1.6 1.6 0 0 0 -.3 -1.8 l -.1 -.1 " +
            "a 2 2 0 1 1 2.8 -2.8 l .1 .1 a 1.6 1.6 0 0 0 1.8 .3 H 9 a 1.6 1.6 0 0 0 1 -1.5 V 3 a 2 2 0" +
            " 1 1 4 0 v .1 a 1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8 -.3 l .1 -.1 a 2 2 0 1 1 2.8 2.8 l -" +
            ".1 .1 a 1.6 1.6 0 0 0 -.3 1.8 V 9 a 1.6 1.6 0 0 0 1.5 1 H 21 a 2 2 0 1 1 0 4 h -.1 a 1.6 1" +
            ".6 0 0 0 -1.5 1 z"
        ),
        circles = listOf(Triple(12f, 12f, 3f)),
    )

    /** 日期切换那两个箭头 */
    val ChevronLeft = VectorIcon(listOf("M 15 18 l -6 -6 6 -6"))
    val ChevronRight = VectorIcon(listOf("M 9 18 l 6 -6 -6 -6"))

    /** 关闭浮层的叉 */
    val Close = VectorIcon(listOf("M 18 6 L 6 18 M 6 6 l 12 12"))

    /**
     * 复制。两张叠起来的纸:前面一张完整,后面一张只画露出来的两条边。
     *
     * 后面那张故意不画成完整的方框 —— 描边图标里两个闭合矩形叠在一起,
     * 在 18dp 这种尺寸下会糊成一团网格。只留左上那个折角,形状反而更清楚,
     * 和这套图标里其他几个的线条密度也一致。
     */
    val Copy = VectorIcon(
        listOf(
            "M 10 8 h 10 a 2 2 0 0 1 2 2 v 10 a 2 2 0 0 1 -2 2 H 10 a 2 2 0 0 1 -2 -2 V 10 a 2 2 0 0 1 2 -2 z",
            "M 4 16 a 2 2 0 0 1 -2 -2 V 4 a 2 2 0 0 1 2 -2 h 10 a 2 2 0 0 1 2 2",
        )
    )
}

@Composable
fun StrokeIcon(
    icon: VectorIcon,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.7f,
) {
    // 解析一次就够,路径数据是常量
    val parsed: List<Path> = remember(icon) {
        icon.paths.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier = modifier.size(size)) {
        // 原始 viewBox 是 24x24,按目标尺寸等比缩放
        val k = this.size.minDimension / 24f
        scale(scale = k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val stroke = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            parsed.forEach { drawPath(it, color = color, style = stroke) }
            icon.circles.forEach { (cx, cy, r) ->
                drawCircle(
                    color = color,
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = stroke,
                )
            }
        }
    }
}
