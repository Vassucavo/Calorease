package app.calorease.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Repository
import app.calorease.logic.Calibration
import app.calorease.ui.theme.LocalColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 校准页 —— 这个应用存在的理由。
 *
 * 拿实际吃了多少和体重实际怎么变放在一起,反推真实的每日消耗。
 * 三道守卫任一不满足就拒绝出数,并说清楚差在哪 ——
 * 宁可不给数字,也不给一个基于两天水分波动算出来的假数字。
 */
@Composable
fun TuneScreen(
    state: Repository.AppState,
    onApplyTarget: (Int) -> Unit,
) {
    val c = LocalColors.current

    when (val r = Calibration.run(state.weights, state.history)) {
        is Calibration.Result.NeedTwo -> NotReady(
            "校准至少需要两条体重记录，现在有 ${r.have} 条。"
        )

        is Calibration.Result.TooShort -> NotReady(
            "两条记录相隔 ${r.span} 天。校准要等到 ${Calibration.MIN_SPAN} 天以上 —— " +
                "更短的区间里，变化几乎都是水分。"
        )

        is Calibration.Result.TooSparse -> NotReady(
            "区间内 ${r.span} 天里只记录了 ${r.logged} 天的摄入。覆盖率要到 60% 以上，结果才有意义。"
        )

        is Calibration.Result.Ok -> Column {
            Card(modifier = Modifier.padding(bottom = 10.dp)) {
                Eyebrow("真实每日消耗")
                BigNumber(
                    r.realTdee.grouped(),
                    color = c.burn,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                StatRow("平均摄入", r.avgIntake.grouped())
                StatRow(
                    "体重变化",
                    (if (r.deltaKg >= 0) "+" else "−") + "${abs(r.deltaKg).f1()} kg",
                )
                StatRow("观测区间", "${r.span} 天")
                StatRow(
                    "变化速度",
                    (if (r.perWeek >= 0) "+" else "−") + "${abs(r.perWeek).f1()} kg/周",
                    last = true,
                )
            }

            val gap = r.gap
            val explain = when {
                gap == 0 -> "完全吻合，这种情况很少见。"
                gap > 0 -> "公式偏低了 ${abs(gap).grouped()}。这在预期之中：基础代谢加手表活动量，" +
                    "漏掉了消化食物本身要花的能量（约占摄入的 10%）。"
                else -> "公式偏高了 ${abs(gap).grouped()}。手表普遍高估活动卡路里，这大概是主要原因。"
            }
            Callout(
                "公式 vs 实测",
                "应用算出的平均消耗是 ${r.avgBurned.grouped()}，你身体给出的是 " +
                    "${r.realTdee.grouped()} —— $explain",
            )

            SectionHeader("按这个数字设目标")

            listOf(0.25 to "慢一些，最容易坚持", 0.5 to "中等速度").forEach { (rate, hint) ->
                val s = Calibration.suggestTarget(r.realTdee, rate, state.profile)
                ItemRow(
                    name = "每周 $rate kg",
                    sub = if (s.clamped) "已抬到下限 ${s.floor}" else hint,
                    trailing = s.value.grouped(),
                    trailingColor = c.burn,
                    action = {
                        Text(
                            "采用",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.burn,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    },
                    onTap = { onApplyTarget(s.value) },
                )
            }

            Note(
                "每隔几周重算一次。体重下降后消耗本身会跟着降，第一个月管用的目标，到第三个月就会卡住。" +
                    if (r.coverage < 0.85) {
                        "当前记录覆盖率 ${(r.coverage * 100).roundToInt()}%，记得越全，这个数字越准。"
                    } else ""
            )
        }
    }
}

@Composable
private fun NotReady(message: String) {
    Column {
        Callout("数据还不够", message)
        EmptyHint(
            "攒够之后，这一页会把你实际吃了多少和体重实际怎么变的放在一起，反推出你真实的每日消耗。" +
                "这个观测出来的数字，比任何公式都可靠。"
        )
    }
}
