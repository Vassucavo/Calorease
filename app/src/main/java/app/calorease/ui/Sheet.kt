package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle

/**
 * 浮层。
 *
 * 键盘处理:内容整体套 `WindowInsets.ime.union(navigationBars)` 的内边距。
 * 错误档案第 4 条那个「软键盘检测在 adjustResize 下永远失效」的坑在这里
 * 不存在 —— 当初要靠记录历史最大高度去反推键盘高度,是因为 WebView 里
 * 拿不到真实值;原生的 WindowInsets.ime 就是系统给的准确高度。
 *
 * 另外这里也不用 backdrop-filter(错误档案第 3 条)。遮罩就是一层半透明
 * 纯色,没有模糊 —— 原生的模糊要额外的离屏合成,在滚动内容上一样会掉帧,
 * 而这个界面本来也不靠玻璃感撑住。
 */
@Composable
fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 网页版是 rgba(24,36,30,.5) —— 带绿调的深色,不是纯黑
                .background(Color(0xFF18241E).copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    // 面板底色是页面底色 canvas,不是卡片的纸色
                    .background(c.canvas)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* 吃掉点击,别穿透到遮罩 */ },
                    )
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp, bottom = 22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(6.dp),
                    ) {
                        StrokeIcon(Icons.Close, color = c.muted, size = 20.dp, strokeWidth = 2f)
                    }
                }
                content()
            }
        }
    }
}

/**
 * 带标签的输入框。
 *
 * 用 BasicTextField 自己画,不用 Material 的 OutlinedTextField ——
 * 后者自带浮动标签、加粗的聚焦描边、自己的圆角和行高,和网页版的 `.in`
 * (纸色底 + 1px 细边 + 圆角 10 + 内边距 11/12)完全是两种东西,
 * 一放上去整个应用就变成了默认 Material 模板的样子。
 *
 * 标签是上方一行 12sp 的灰字(`label.f span`),不是浮进框里的。
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
                .clip(RoundedCornerShape(10.dp))
                .background(c.paper)
                .border(1.dp, c.line, RoundedCornerShape(10.dp))
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
 * 两者都是「一排各自独立的圆角按钮」,不是一个连体的胶囊 —— 中间有间隔,
 * 每个都有自己的边框和投影。选中态是**深墨底白字(--ink)**,不是墨绿;
 * 只有克数快捷按钮的选中态才用墨绿。这两处颜色我一开始搞反了。
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
                        if (on) Modifier
                        else Modifier.shadow(1.dp, shape, spotColor = Color(0x1F18241E))
                    )
                    .clip(shape)
                    .background(if (on) c.ink else c.paper)
                    .border(1.dp, if (on) c.ink else c.line, shape)
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

/** `.seg` —— 表单里的二选一,间隔 8、圆角 10、内边距 11、14sp */
@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) =
    ChipRow(options, selectedIndex, 8.dp, 10.dp, 11.dp, 14.sp, 14.dp, onSelect)

/** `.tabs` —— 添加餐食面板顶部的三个分页,间隔 6、圆角 9、内边距 10、13sp */
@Composable
fun SheetTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) =
    ChipRow(options, selectedIndex, 6.dp, 9.dp, 10.dp, 13.sp, 16.dp, onSelect)

/**
 * 克数/份数的快捷按钮。
 *
 * 错误档案第 2 条(选中态被主题样式盖掉)和第 9 条(改了值忘了刷新高亮)
 * 在这里都不成立:选中与否是 `selected == value` 当场算出来的,
 * 没有第二处状态需要同步,也没有 CSS 权重可以把它盖掉。
 */
@Composable
fun QuickAmounts(
    presets: List<Double>,
    selected: Double,
    unitLabel: String,
    onPick: (Double) -> Unit,
) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presets.forEach { v ->
            val on = kotlin.math.abs(selected - v) < 1e-9
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) c.burn else c.paper)
                    .border(
                        1.dp,
                        if (on) c.burn else c.line,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onPick(v) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()) + unitLabel,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) (if (c.isDark) Color(0xFF08120F) else Color.White) else c.ink,
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
            Text(
                if (checked) "✓" else " ",
                fontSize = 13.sp,
                color = if (c.isDark) Color(0xFF08120F) else Color.White,
            )
        }
        Text(label, fontSize = 14.sp, color = c.muted)
    }
}

/** 简单的是/否确认框。危险操作走这个 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalColors.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.sheet)
                .padding(20.dp),
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                message,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = c.muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                if (danger) {
                    DangerButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                } else {
                    SolidButton(confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
