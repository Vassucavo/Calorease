package app.calorease.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Repository
import app.calorease.logic.Calibration
import app.calorease.logic.Dates
import app.calorease.ui.theme.LocalColors
import kotlin.math.abs

/**
 * 体重页。
 *
 * 整页的立场:**看线不看点**。散点是每天的水分噪声,7 次记录的移动平均
 * 才是趋势。所以大数字显示的是趋势值 avg,不是最后一次称出来的 kg。
 */
@Composable
fun WeightScreen(
    state: Repository.AppState,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = LocalColors.current
    val trend = Calibration.trend(state.weights)

    Column {
        if (trend.isNotEmpty()) {
            val last = trend.last()
            val sub = if (trend.size > 1) {
                val delta = last.avg - trend.first().avg
                val span = Dates.between(trend.first().date, last.date)
                if (span > 0) {
                    "$span 天内${if (delta >= 0) "增加" else "减少"} ${abs(delta).f1()} kg"
                } else "持续称重才能画出趋势"
            } else "持续称重才能画出趋势"

            Card(modifier = Modifier.padding(bottom = 10.dp)) {
                Eyebrow("趋势体重")
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(last.avg.f1(), color = c.ink, modifier = Modifier.padding(bottom = 4.dp))
                    Text(
                        " kg",
                        fontSize = 15.sp,
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text(sub, fontSize = 12.sp, lineHeight = 20.sp, color = c.muted)
                if (trend.size >= 2) {
                    Spark(trend, lineColor = c.burn, dotColor = c.muted)
                }
            }

            BodyComposition(trend = Calibration.leanTrend(state.weights))
        }

        // 「记录体重」不在这里 —— 它和今日页的「添加餐食」一样固定在屏幕底部,见 App.kt

        SectionHeader("全部记录", "${state.weights.size} 条", mono = false)

        if (state.weights.isEmpty()) {
            EmptyHint(
                "还没有记录。\n以前自己记过的数据也能补录 —— 记录时可以选日期。\n" +
                    "有两条相隔 14 天以上的记录，校准功能就能用了。"
            )
        } else {
            state.weights.sortedByDescending { it.date }.forEach { w ->
                ItemRow(
                    name = Dates.full(w.date),
                    sub = w.bf?.let { "体脂 ${it.f1()}% · 瘦体重 ${w.leanKg!!.f1()}kg" } ?: "点击可修改",
                    trailing = "${w.kg.f1()} kg",
                    onTap = { onEdit(w.date) },
                    onDelete = { onDelete(w.date) },
                )
            }
        }
    }
}

/** 体脂率、瘦体重、脂肪量,以及对这三个数怎么读的判断 */
@Composable
private fun BodyComposition(trend: List<Calibration.TrendPoint>) {
    if (trend.isEmpty()) return
    val c = LocalColors.current
    val last = trend.last()
    val lean = last.lean!!
    val fat = last.kg - lean

    Card(modifier = Modifier.padding(bottom = 10.dp)) {
        StatRow("最近体脂率", "${last.bf!!.f1()}%")
        StatRow("瘦体重", "${lean.f1()} kg")

        if (trend.size > 1) {
            val first = trend.first()
            val firstLean = first.lean!!
            val dLean = lean - firstLean
            val dFat = fat - (first.kg - firstLean)
            val span = Dates.between(first.date, last.date)

            StatRow("脂肪量", "${fat.f1()} kg")
            StatRow(
                "$span 天内脂肪",
                (if (dFat >= 0) "+" else "−") + "${abs(dFat).f1()} kg",
                valueColor = if (dFat <= 0) c.burn else c.warn,
            )
            StatRow(
                "$span 天内瘦体重",
                (if (dLean >= 0) "+" else "−") + "${abs(dLean).f1()} kg",
                valueColor = if (dLean >= -0.5) c.burn else c.warn,
                last = true,
            )
        } else {
            StatRow("脂肪量", "${fat.f1()} kg", last = true)
        }
    }

    if (trend.size > 1) {
        val first = trend.first()
        val firstLean = first.lean!!
        val dLean = lean - firstLean
        val dFat = fat - (first.kg - firstLean)
        val verdict = when {
            dFat < -0.3 && dLean >= -0.5 ->
                "脂肪在掉，瘦体重基本守住了 —— 这正是减脂想要的结果。"
            dFat < -0.3 && dLean < -0.5 ->
                "脂肪在掉，但瘦体重也掉了不少。可以考虑提高蛋白质摄入、加一些力量训练，或者把缺口调小一点。"
            dFat >= 0.3 ->
                "脂肪量在上升。如果这不是你想要的，看看校准页给的目标是不是该调整了。"
            else -> "变化还很小，再记一段时间才看得出方向。"
        }
        Callout("怎么读这几个数", verdict)
    }
}

/**
 * 趋势折线。实线是 7 次移动平均,半透明散点是每次实际称出来的值。
 * 横轴按真实日期间隔,不是按记录序号 —— 隔了两周才称的一次不该和昨天那次等距。
 */
@Composable
private fun Spark(
    points: List<Calibration.TrendPoint>,
    lineColor: Color,
    dotColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .padding(top = 10.dp),
    ) {
        val pad = 10f
        val w = size.width
        val h = size.height

        val days = points.map { Dates.parse(it.date).toEpochDay().toFloat() }
        val spanX = (days.last() - days.first()).takeIf { it != 0f } ?: 1f
        val values = points.map { it.kg } + points.map { it.avg }
        val lo = values.min()
        val hi = values.max()
        val range = (hi - lo).takeIf { it != 0.0 } ?: 1.0

        fun px(i: Int) = pad + (days[i] - days.first()) / spanX * (w - 2 * pad)
        fun py(v: Double) = (h - pad - ((v - lo) / range).toFloat() * (h - 2 * pad))

        points.forEachIndexed { i, p ->
            drawCircle(
                color = dotColor.copy(alpha = 0.5f),
                radius = 2.5f * density,
                center = Offset(px(i), py(p.kg)),
            )
        }

        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(px(i), py(p.avg)) else path.lineTo(px(i), py(p.avg))
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.5f * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
