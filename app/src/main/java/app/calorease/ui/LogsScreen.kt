package app.calorease.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.calorease.data.Repository
import app.calorease.logic.Dates
import app.calorease.ui.theme.LocalColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 记录页。最近 60 天,倒序。
 *
 * 顶上先给 7 天平均结余而不是当天的数 —— 单日波动大到没有参考价值,
 * 以周为单位看才能看出方向。整个应用反复在讲同一件事:看趋势,别看单日。
 */
@Composable
fun LogsScreen(
    state: Repository.AppState,
    onOpenDay: (String) -> Unit,
) {
    val c = LocalColors.current
    val keys = state.history.keys.sortedDescending().take(60)

    if (keys.isEmpty()) {
        EmptyHint("还没有任何记录。")
        return
    }

    val recent = keys.take(7)
    val avgNet = recent.sumOf { (state.history.getValue(it).intake - state.history.getValue(it).burned).toDouble() }
        .div(recent.size).roundToInt()
    val avgIn = recent.sumOf { state.history.getValue(it).intake.toDouble() }
        .div(recent.size).roundToInt()

    Column {
        Card(modifier = Modifier.padding(bottom = 10.dp)) {
            Eyebrow("最近 7 天平均结余")
            BigNumber(
                (if (avgNet >= 0) "+" else "−") + abs(avgNet).grouped(),
                color = if (avgNet >= 0) c.intake else c.burn,
            )
            Note("平均每天摄入 ${avgIn.grouped()}。单日波动很大，以周为单位看才靠谱。")
        }

        // 那天称的体重也挂上去。这一页是回头看的地方,而这个应用从头到尾
        // 在讲的就是「摄入减消耗」和体重的对应关系 —— 两个数隔在两页里,
        // 就得来回翻才对得上。没称的那天不显示,不占位置。
        val byDate = state.weights.associateBy { it.date }

        keys.forEach { k ->
            val h = state.history.getValue(k)
            val net = h.intake - h.burned
            val kg = byDate[k]?.kg
            ItemRow(
                name = Dates.full(k),
                sub = "摄入 ${h.intake.grouped()} · 消耗 ${h.burned.grouped()}" +
                    (kg?.let { " · ${it.f1()} kg" } ?: ""),
                trailing = (if (net >= 0) "+" else "−") + abs(net).grouped(),
                trailingColor = if (net >= 0) c.intake else c.burn,
                onTap = { onOpenDay(k) },
            )
        }
    }
}
