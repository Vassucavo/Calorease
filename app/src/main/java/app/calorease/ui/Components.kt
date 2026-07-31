package app.calorease.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle

/**
 * 界面零件。尺寸和圆角都是从网页版的 CSS 里一比一量出来的:
 *
 *     .card  圆角 16、内边距 18、1px 描边
 *     .row   圆角 10、内边距 11/13、条目间距 6
 *     .big   42sp / 600
 *     h2.sec 12sp / 字距 .06em / muted
 *     .bar-track 高 9、圆角 5
 *
 * 换成原生是为了换手感,不是换长相。
 */

/** 千分位。界面上所有热量数字都走这个,和网页版的 toLocaleString() 对齐 */
fun Int.grouped(): String {
    val s = kotlin.math.abs(this).toString()
    val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(ch)
    }
    return (if (this < 0) "-" else "") + sb
}

/** 一位小数,和网页版的 f1() 一致 */
fun Double.f1(): String = String.format("%.1f", this)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}

/** 段落小标题。右边可以挂一个数字(消耗/摄入的合计) */
@Composable
fun SectionHeader(title: String, trailing: String? = null, trailingColor: Color? = null) {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            fontSize = 12.sp,
            letterSpacing = 0.72.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.muted,
        )
        if (trailing != null) {
            Text(
                trailing,
                style = NumberStyle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = trailingColor ?: c.muted,
            )
        }
    }
}

/** 小号说明文字 */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 10.dp),
        fontSize = 12.sp,
        lineHeight = 20.sp,
        color = LocalColors.current.muted,
    )
}

/** 空状态 */
@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp),
        fontSize = 14.sp,
        lineHeight = 24.sp,
        color = LocalColors.current.muted,
    )
}

/** 眉标 —— 大数字上面那行小字 */
@Composable
fun Eyebrow(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        color = LocalColors.current.muted,
    )
}

/** 42sp 的主数字 */
@Composable
fun BigNumber(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = NumberStyle,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/**
 * 摄入/消耗的对比条。两条共用同一个基准(两者较大的那个),
 * 所以长短是可比的 —— 这点和网页版一致。
 */
@Composable
fun MeasureBar(label: String, value: Int, fraction: Float, color: Color) {
    val c = LocalColors.current
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "bar",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Eyebrow(label)
        Text(
            value.grouped(),
            style = NumberStyle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(c.line),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color),
        )
    }
}

/** 卡片里的一行「标签 — 数值」,底部一条细分隔线,最后一行不画线 */
@Composable
fun StatRow(label: String, value: String, valueColor: Color? = null, last: Boolean = false) {
    val c = LocalColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(label, fontSize = 13.sp, color = c.muted)
            Text(
                value,
                style = NumberStyle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: c.ink,
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** 带左侧墨绿竖条的说明块。用来放「怎么读这几个数」这类解释 */
@Composable
fun Callout(title: String, body: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .height(IntrinsicSize.Min)   // 让左边那条竖线能跟着文字高度撑满
            .clip(RoundedCornerShape(10.dp))
            .background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(10.dp)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.burn))
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                body,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = c.muted,
            )
        }
    }
}

/** 实心主按钮 */
@Composable
fun SolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background ?: c.burn)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (c.isDark) Color(0xFF08120F) else Color.White,
        )
    }
}

/**
 * 列表里的一条。
 *
 * [onTap] 有值时整行可点(改这条),[onDelete] 有值时右边出一个删除叉。
 * [trailing] 是右边那个数字,[trailingLabel] 用于代替数字显示别的东西(比如「填写」)。
 */
@Composable
fun ItemRow(
    name: String,
    sub: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
    onTap: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.paper)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = c.ink,
            )
            if (sub != null) {
                Text(sub, fontSize = 11.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = NumberStyle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = trailingColor ?: c.ink,
            )
        }
        action?.invoke()
        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", fontSize = 20.sp, color = c.muted)
            }
        }
    }
}
