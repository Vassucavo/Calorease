package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
 * 选日期,不是填日期。
 *
 * 之前是三个快捷按钮(今天/昨天/前天)加一个手填 YYYY-MM-DD 的输入框 ——
 * 补录前天以前的记录就只能一个字符一个字符地敲,太费事。现在是一整块月历:
 * 翻月、点一下就选好了,「今天」单独留一个按钮。
 *
 * 未来的日期点不动 —— 校验那边本来就会拦(不能记录未来),不如在这里就变灰,
 * 别让人点完了才被告知不行。
 *
 * [value] 和 [onChange] 走的都是 "YYYY-MM-DD" 字符串,和存储层一致。
 */
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalColors.current
    val today = remember { LocalDate.now() }
    val selected = remember(value) {
        runCatching { Dates.parse(value) }.getOrDefault(today)
    }
    var month by remember(value) { mutableStateOf(YearMonth.from(selected)) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(bottom = 5.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(12.dp, c.inputBg, elevation = 1.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            // ---------- 月份切换 ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MonthArrow(Icons.ChevronLeft, enabled = true) { month = month.minusMonths(1) }
                Text(
                    "${month.year} 年 ${month.monthValue} 月",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = c.ink,
                )
                // 不能翻到还没到来的月份
                MonthArrow(
                    Icons.ChevronRight,
                    enabled = month < YearMonth.from(today),
                ) { month = month.plusMonths(1) }
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
            // dayOfWeek.value 是 1=周一,正好对上表头的排法
            val lead = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val rows = (lead + days + 6) / 7

            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = r * 7 + col - lead + 1
                        if (dayNum < 1 || dayNum > days) {
                            Box(modifier = Modifier.weight(1f).height(34.dp))
                        } else {
                            val d = month.atDay(dayNum)
                            DayCell(
                                day = dayNum,
                                on = d == selected,
                                enabled = !d.isAfter(today),
                                modifier = Modifier.weight(1f),
                                onPick = { onChange(Dates.key(d)) },
                            )
                        }
                    }
                }
            }
        }

        GhostButton(
            "今天",
            onClick = { onChange(Dates.key(today)) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onPick: () -> Unit,
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .height(34.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (on) Modifier.background(c.burn) else Modifier)
            .alpha(if (enabled) 1f else 0.28f)
            .then(if (enabled) Modifier.clickable(onClick = onPick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = NumberStyle,
            fontSize = 13.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                on -> if (c.isDark) Color(0xFF08120F) else Color.White
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
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon, color = c.ink, size = 16.dp, strokeWidth = 2f)
    }
}
