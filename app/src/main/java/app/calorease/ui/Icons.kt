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
 * 路径数据是从网页版 index.html 的 ICONS 常量里**原样复制**过来的,
 * 一个字符都没改写 —— 用 Compose 自带的 PathParser 解析 SVG 路径串,
 * 而不是手工翻译成 lineTo/arcTo。手工翻译一定会走样,尤其是那些
 * `a 2 2 0 012 2` 的圆角弧线。
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
    val Today = VectorIcon(
        listOf("M3 9h18M7 3v3M17 3v3M5 5h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2z")
    )

    val Weight = VectorIcon(
        paths = listOf("M5 21h14l-2-11H7L5 21z"),
        circles = listOf(Triple(12f, 6f, 2.5f)),
    )

    val Tune = VectorIcon(
        listOf("M4 20V10M12 20V4M20 20v-6M1 10h6M9 4h6M17 14h6")
    )

    val Logs = VectorIcon(
        listOf("M3 12a9 9 0 109-9 9 9 0 00-6.4 2.7L3 8M3 3v5h5M12 7v5l3 2")
    )

    val Settings = VectorIcon(
        paths = listOf(
            "M19.4 15a1.6 1.6 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.6 1.6 0 00-1.8-.3 1.6 " +
                "1.6 0 00-1 1.5V21a2 2 0 11-4 0v-.1a1.6 1.6 0 00-1-1.5 1.6 1.6 0 00-1.8.3l-.1.1a2 " +
                "2 0 11-2.8-2.8l.1-.1a1.6 1.6 0 00.3-1.8 1.6 1.6 0 00-1.5-1H3a2 2 0 110-4h.1a1.6 " +
                "1.6 0 001.5-1 1.6 1.6 0 00-.3-1.8l-.1-.1a2 2 0 112.8-2.8l.1.1a1.6 1.6 0 001.8.3H9a1.6 " +
                "1.6 0 001-1.5V3a2 2 0 114 0v.1a1.6 1.6 0 001 1.5 1.6 1.6 0 001.8-.3l.1-.1a2 2 0 " +
                "112.8 2.8l-.1.1a1.6 1.6 0 00-.3 1.8V9a1.6 1.6 0 001.5 1H21a2 2 0 110 4h-.1a1.6 " +
                "1.6 0 00-1.5 1z"
        ),
        circles = listOf(Triple(12f, 12f, 3f)),
    )

    /** 日期切换那两个箭头 */
    val ChevronLeft = VectorIcon(listOf("M15 18l-6-6 6-6"))
    val ChevronRight = VectorIcon(listOf("M9 18l6-6-6-6"))

    /** 关闭浮层的叉 */
    val Close = VectorIcon(listOf("M18 6L6 18M6 6l12 12"))
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
