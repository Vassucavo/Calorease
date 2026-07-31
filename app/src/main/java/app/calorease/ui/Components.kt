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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
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


/**
 * 玻璃表面:半透明底 + 一圈高光细边 + 一层很淡的投影。
 *
 * 投影是关键 —— 上一版我只画了底和边,没有投影,于是边框在承担全部的
 * 「把轮廓拉出来」的活,看上去就是一条突兀的白线贴在浅色块上,很假。
 * 网页版每个玻璃面都带 box-shadow(卡片 0 6px 20px,内部元素 0 1px 2px +
 * 0 4px 12px),那层柔和的落影才是让面「浮起来」的东西。
 *
 * clip 关掉,否则投影会被自己的形状裁掉。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    color: Color,
    elevation: Dp = 3.dp,
    borderColor: Color? = null,
): Modifier {
    val c = LocalColors.current
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0xFF18241E),
            spotColor = Color(0xFF18241E),
        )
        .clip(shape)
        .background(color)
        .border(1.dp, borderColor ?: c.surfaceBorder, shape)
}

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
            .glassSurface(RoundedCornerShape(16.dp), c.cardBg, elevation = 6.dp)
            .padding(18.dp),
        content = content,
    )
}

/**
 * 段落小标题。右边可以挂一段尾字。
 *
 * 两种尾字在网页版里长得不一样:消耗/摄入的合计是 `class="num"` 加强调色 ——
 * 等宽、加粗;而「我的食物 0 条」这种计数是内联的 `font-weight:400` 普通文字。
 * [mono] 用来区分这两种。
 */
@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    trailingColor: Color? = null,
    mono: Boolean = true,
) {
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
                style = if (mono) NumberStyle else LocalTextStyle.current,
                fontSize = 12.sp,
                fontWeight = if (mono) FontWeight.SemiBold else FontWeight.Normal,
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
            .glassSurface(RoundedCornerShape(10.dp), c.rowBg),
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

/**
 * 页面顶部的标题栏。眉标一行小字 + 大标题一行,对应网页版的 `.head`:
 * 高度至少 42、下边距 16,眉标 11sp 字距 .1em,标题 18sp/600。
 */
@Composable
fun PageHeader(eyebrow: String, title: String) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(eyebrow, fontSize = 11.sp, letterSpacing = 1.1.sp, color = c.muted)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
    }
}

/*
 * 按钮。四种,和网页版的 .btn / .btn.ghost / .btn.gold|teal / .btn.danger 一一对应:
 *
 *   .btn        深墨底(--ink)白字     圆角 10,内边距 13/16,15sp/600
 *   .btn.ghost  纸色底 + 细边 + 微投影
 *   .btn.teal   墨绿底                表单的「保存」用这个
 *   .btn.gold   赭金底                「加入记录」用这个
 *   .btn.danger 纸色底 + 警示色字和边  「覆盖」「删除」用这个
 *
 * 深色模式下实心按钮的字要用近黑色,不然亮底上的白字会糊。
 */
private val BtnShadow = 3.dp

@Composable
private fun ButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    background: Color,
    contentColor: Color,
    border: Color? = null,
    shadow: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (shadow) Modifier.glassSurface(shape, background, borderColor = border)
                else Modifier.clip(shape).background(background)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}

/** 实心按钮。默认墨绿(.btn.teal),传 background 可换成赭金等 */
@Composable
fun SolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
) {
    val c = LocalColors.current
    ButtonBase(
        text, onClick, modifier,
        background = background ?: c.burn,
        contentColor = if (c.isDark) Color(0xFF08120F) else Color.White,
    )
}

/** 深墨底的主按钮(.btn) —— 底部「添加餐食」和首次设置的「保存」用它 */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    ButtonBase(
        text, onClick, modifier,
        background = c.ink,
        contentColor = c.canvas,
    )
}

/** 纸色底的次要按钮(.btn.ghost) */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    ButtonBase(
        text, onClick, modifier,
        background = c.rowBg,
        contentColor = c.ink,
        border = c.surfaceBorder,
        shadow = true,
    )
}

/** 危险操作(.btn.danger):纸色底,警示色的字和边框 —— 不是实心红 */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    ButtonBase(
        text, onClick, modifier,
        background = c.rowBg,
        contentColor = c.warn,
        border = c.warn,
        shadow = true,
    )
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
            .glassSurface(RoundedCornerShape(10.dp), c.rowBg)
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
