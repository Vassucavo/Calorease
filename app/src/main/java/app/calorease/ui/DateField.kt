package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.logic.Dates
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle
import java.time.LocalDate
import java.time.YearMonth

private val WEEK_HEADS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 日期这一行:长得像输入框,但点了不是弹键盘,而是弹一块月历([DatePickSheet])。
 *
 * 默认值由调用方给(记录体重给的是今天),所以不需要一个「今天」按钮 ——
 * 什么都不动就已经是今天了。
 */
@Composable
fun DateRow(label: String, value: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(bottom = 5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(10.dp, c.inputBg, elevation = 1.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (Dates.isValidKey(value)) Dates.full(value) else "选择日期",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = if (Dates.isValidKey(value)) c.ink else c.muted,
            )
            StrokeIcon(Icons.Today, color = c.muted, size = 18.dp)
        }
    }
}

/**
 * 选日期的浮层。叠在调用它的那个面板上,下面那层糊掉退到后面。
 *
 * 之前是三个快捷按钮(今天/昨天/前天)加一个手填 YYYY-MM-DD 的输入框 ——
 * 补录前天以前的记录就只能一个字符一个字符地敲。
 *
 * 未来的日期是灰的、点不动 —— 校验那边本来就会拦(不能记录未来),
 * 不如在这里就拦住,别让人点完了才被告知不行。
 */
@Composable
fun BoxScope.DatePickSheet(
    value: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val c = LocalColors.current
    val today = remember { LocalDate.now() }
    val selected = remember(value) { runCatching { Dates.parse(value) }.getOrDefault(today) }
    // 只按初值定一次要显示哪个月,之后翻月是用户自己的操作,不该被外面重置
    var month by remember { mutableStateOf(YearMonth.from(selected)) }

    BottomSheet("选择日期", onDismiss) {
        Column {
            // ---------- 月份切换 ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MonthArrow(Icons.ChevronLeft, enabled = true) { month = month.minusMonths(1) }
                Text(
                    "${month.year} 年 ${month.monthValue} 月",
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = c.ink,
                )
                // 不能翻到还没到来的月份
                MonthArrow(Icons.ChevronRight, enabled = month < YearMonth.from(today)) {
                    month = month.plusMonths(1)
                }
            }

            // ---------- 星期表头 ----------
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEK_HEADS.forEach { w ->
                    Text(
                        w,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = c.muted,
                    )
                }
            }

            // ---------- 日期格 ----------
            val first = month.atDay(1)
            // dayOfWeek.value 是 1 = 周一,正好对上表头的排法
            val lead = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val rows = (lead + days + 6) / 7

            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = r * 7 + col - lead + 1
                        if (dayNum < 1 || dayNum > days) {
                            Box(modifier = Modifier.weight(1f).height(40.dp))
                        } else {
                            val d = month.atDay(dayNum)
                            DayCell(
                                day = dayNum,
                                on = d == selected,
                                isToday = d == today,
                                enabled = !d.isAfter(today),
                                modifier = Modifier.weight(1f),
                                onPick = { onPick(Dates.key(d)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    on: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onPick: () -> Unit,
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .height(40.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(9.dp))
            .then(if (on) Modifier.background(c.burn) else Modifier)
            .alpha(if (enabled) 1f else 0.26f)
            .then(if (enabled) Modifier.clickable(onClick = onPick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = NumberStyle,
            fontSize = 14.sp,
            // 今天不选中时也加粗,一眼能定位到「现在在哪」
            fontWeight = if (on || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                on -> if (c.isDark) Color(0xFF08120F) else Color.White
                isToday -> c.burn
                else -> c.ink
            },
        )
    }
}

@Composable
private fun MonthArrow(icon: VectorIcon, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon, color = c.ink, size = 17.dp, strokeWidth = 2f)
    }
}
