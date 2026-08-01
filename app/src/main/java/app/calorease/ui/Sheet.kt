package app.calorease.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle

/**
 * 底部浮层。
 *
 * **不用 Dialog。** 之前用 Dialog 有两个躲不开的问题:
 *
 * 1. Dialog 是独立窗口,拿不到背后的页面内容,所以做不出真正的毛玻璃 ——
 *    只能靠一层不透明度很高的纯色硬凑,结果就是「玻璃很假」。
 * 2. Dialog 的窗口默认按内容高度自适应,内容一超过屏幕就被裁掉而不是滚动。
 *    表单底部的「保存」按钮显示不全就是这么来的。
 *
 * 现在浮层画在主组件树里:背后的页面内容能加真实模糊(见 App 里的 blur),
 * 高度也被屏幕约束住,内容超了就在浮层内部滚,不会被裁掉。
 *
 * 键盘用 WindowInsets.ime —— 系统给的真实高度,不需要像 WebView 时代那样
 * 靠记录历史最大高度去反推(错误档案第 4 条)。
 */
@Composable
fun BoxScope.BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    /** 内容自己管滚动时传 false,比如添加餐食那个面板 */
    scrollable: Boolean = true,
    /**
     * 内容区左右内边距。传 0 的话调用方自己加 —— 这样里面的列表才能
     * 自己带缩进,而顶/底的淡出覆盖整个面板宽度。
     */
    contentPadding: Dp = 18.dp,
    /**
     * 内容区的下内边距,默认正好等于底部淡出带的高度 —— 内容停在淡出带
     * 上沿,不会被削掉。
     *
     * 传 0 表示「让内容一直铺到底,从淡出带里穿过去」。滚动的长列表要这个:
     * 它得真的滑进去才淡得出来,停在上沿反而是硬切断。
     */
    contentBottomPadding: Dp = SheetBottomFade,
    /**
     * 底部淡出带的高度。传 0 表示这个浮层自己管收边 —— 添加餐食那个面板
     * 底部有固定按钮,整片淡出会把按钮一起削掉,它得自己只淡出列表那一段。
     */
    fadeBottom: Dp = SheetBottomFade,
    /** 标题右边、关闭叉左边的位置。放搜索之类的入口 */
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    BackHandler(onBack = onDismiss)

    // 遮罩:整屏,点空白处关闭
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    )

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // 和网页版的 max-height:94vh 对应,顶上永远留一条能点关闭的空隙
            .heightIn(max = screenHeight * 0.94f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(c.panelBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* 吃掉点击,别穿透到遮罩 */ },
            )
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.statusBars)
            )
            .padding(top = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                // 只留 6 —— 剩下的间距挪进内容区里(见下面的 SheetShadowRoom),
                // 那一截要在裁剪范围**之内**,第一个部件的投影才有地方画
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                action?.invoke()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(6.dp),
                ) {
                    StrokeIcon(Icons.Close, color = c.muted, size = 20.dp, strokeWidth = 2f)
                }
            }
        }
        // 这里必须是 Column。之前为了挂滚动修饰符图省事用了 Box,而 Box 是
        // 层叠布局 —— 调用方发的同级元素(比如三个分页 + 搜索列表)会直接
        // 压在一起。「搜索食物」框和顶部分页重叠就是这么来的。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .then(if (fadeBottom > 0.dp) Modifier.fadeOutBottom(fadeBottom) else Modifier)
                .then(
                    if (scrollable) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                )
                .padding(horizontal = contentPadding)
                // 上下都给投影留出余量。淡出那层是离屏合成,会把内容裁到自己的
                // 边界上,不留这一截,第一个和最后一个部件的投影就被削平了。
                .padding(top = SheetShadowRoom, bottom = contentBottomPadding)
        ) {
            content()
        }
    }
}

/** 浮层底部淡出带的高度。内容的下内边距默认也用它,免得正文被削掉 */
private val SheetBottomFade = 26.dp

/** 内容区上下留给部件投影的余量。glassSurface 的落影最多外扩这么多 */
val SheetShadowRoom = 10.dp

/**
 * 模糊半径。
 *
 * **浮层自己不做模糊。** 试过在面板上单独挂 Modifier.blur,不管半径调成多少、
 * 放在 clip 前面还是后面,面板顶边始终是一条清晰的直线 —— 因为 blur 默认会把
 * 模糊结果裁到自己的矩形边界上(BlurredEdgeTreatment.Rectangle),
 * 那条边界就是那道线,和半径无关。
 *
 * 所以二级浮层打开时,是**页面和一级浮层作为一个整体**被糊一次(见 App),
 * 中间根本不存在两层的交界,自然也就没有那条线。
 */
val PageBlur = 20.dp

/**
 * 让内容在底部**真的淡出**,而不是拿一条渐变色带盖住它。
 *
 * 之前是画一条「上透下实」的色带压在内容上,颜色只能靠算 —— 面板是半透明的,
 * 压在模糊过的页面上,那个合成色没法用一个固定色值精确复现,差一点就穿帮,
 * 一眼能看出面板上贴着一条别的颜色。
 *
 * 现在改成用 DstIn 混合把内容自身的 alpha 推到 0:露出来的就是面板本来的
 * 背景,不是另一种颜色,所以永远对得上,连深浅色模式都不用分别调。
 * 代价是要开一层离屏合成 —— 只在浮层和两个固定按钮那里用,开销可以忽略。
 */
fun Modifier.fadeEdges(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // 渐变里的颜色只有 alpha 有意义 —— DstIn 拿源的 alpha 去乘目标的 alpha,
        // 黑色(alpha 1)保留,透明(alpha 0)擦掉,中间就是淡出。
        val t = top.toPx().coerceAtMost(size.height)
        if (t > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = t,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, t),
                blendMode = BlendMode.DstIn,
            )
        }
        val b = bottom.toPx().coerceAtMost(size.height)
        if (b > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - b,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - b),
                size = Size(size.width, b),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** 只淡出底边 */
fun Modifier.fadeOutBottom(height: Dp): Modifier = fadeEdges(bottom = height)

/**
 * 带标签的输入框。
 *
 * 用 BasicTextField 自己画,不用 Material 的 OutlinedTextField ——
 * 后者自带浮动标签、加粗的聚焦描边、自己的圆角和行高,和网页版的 `.in`
 * 完全是两种东西,一放上去整个应用就变成了默认 Material 模板的样子。
 */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    decimal: Boolean = false,
    placeholder: String? = null,
) {
    val c = LocalColors.current
    Column(modifier = modifier.padding(bottom = 12.dp)) {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        val style = if (numeric || decimal) {
            NumberStyle.copy(fontSize = 16.sp, color = c.ink)
        } else {
            TextStyle(fontSize = 16.sp, color = c.ink)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(c.burn),
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    decimal -> KeyboardType.Decimal
                    numeric -> KeyboardType.Number
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(10.dp, c.inputBg, elevation = 1.dp)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = style.copy(color = c.muted))
                }
                inner()
            },
        )
    }
}

/** 校验失败时的红字。没有错误就什么都不占 */
@Composable
fun FieldError(message: String?) {
    if (message == null) return
    Text(
        message,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = LocalColors.current.warn,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/**
 * 分段控件(`.seg`)和面板顶部的分页(`.tabs`)。
 *
 * 两者都是「一排各自独立的圆角按钮」,不是连体胶囊 —— 中间有间隔,
 * 每个都有自己的边和投影。选中态是深墨底白字,只有克数快捷按钮才用墨绿。
 */
@Composable
private fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    gap: Dp,
    radius: Dp,
    verticalPadding: Dp,
    fontSize: TextUnit,
    bottomPadding: Dp,
    onSelect: (Int) -> Unit,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(radius)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (on) Modifier.clip(shape).background(c.ink)
                        else Modifier.glassSurface(radius, c.rowBg)
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) c.canvas else c.muted,
                )
            }
        }
    }
}

/** `.seg` —— 表单里的二选一 */
@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) =
    ChipRow(options, selectedIndex, 8.dp, 10.dp, 11.dp, 14.sp, 14.dp, onSelect)

/** `.tabs` —— 添加餐食面板顶部的三个分页 */
@Composable
fun SheetTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) =
    ChipRow(options, selectedIndex, 6.dp, 9.dp, 10.dp, 13.sp, 16.dp, onSelect)

/**
 * 克数/份数的快捷按钮。
 *
 * 选中与否是 `selected == value` 当场算出来的,没有第二处状态要同步,
 * 也没有样式层级能把它盖掉 —— 错误档案第 2 条和第 9 条在这里都不成立。
 */
@Composable
fun QuickAmounts(
    presets: List<Double>,
    selected: Double,
    unitLabel: String,
    onPick: (Double) -> Unit,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presets.forEach { v ->
            val on = kotlin.math.abs(selected - v) < 1e-9
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (on) Modifier.clip(shape).background(c.burn)
                        else Modifier.glassSurface(9.dp, c.rowBg)
                    )
                    .clickable { onPick(v) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()) + unitLabel,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) c.canvas else c.ink,
                )
            }
        }
    }
}

/** 勾选框加一行说明 */
@Composable
fun CheckRow(checked: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) c.burn else Color.Transparent)
                .border(1.dp, if (checked) c.burn else c.line, RoundedCornerShape(5.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (checked) "✓" else " ", fontSize = 13.sp, color = c.canvas)
        }
        Text(label, fontSize = 14.sp, color = c.muted)
    }
}

