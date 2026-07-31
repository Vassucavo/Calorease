package app.calorease.logic

import app.calorease.data.HistoryEntry
import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 校准 —— 这个应用存在的理由。
 *
 *     真实每日消耗 = 区间内平均摄入 − (体重变化kg × 7700 ÷ 天数)
 *
 * 公式只是假设,身体的实际反应才是证据。Mifflin-St Jeor 算出来的数字
 * 对每个人都有偏差,但只要连续记几周,体重的变化就能把真实消耗反推出来,
 * 顺带把模型漏掉的食物热效应一并吸收。
 *
 * 三道守卫,任一不满足就拒绝出数并说明原因 —— 宁可不给数字,
 * 也不给一个基于两天体重波动算出来的假数字。
 */
object Calibration {

    /** 每公斤体组织的大致热量 */
    const val KCAL_PER_KG = 7700.0

    /** 首尾至少要隔这么多天。更短的区间里,体重变化几乎都是水分 */
    const val MIN_SPAN = 14

    /** 区间内有摄入记录的天数占比,低于这个数说明记得太稀 */
    const val MIN_COVERAGE = 0.6

    sealed interface Result {
        /** 体重记录不足 2 条 */
        data class NeedTwo(val have: Int) : Result

        /** 首尾跨度不够 */
        data class TooShort(val span: Int) : Result

        /** 记录太稀 */
        data class TooSparse(val logged: Int, val span: Int) : Result

        data class Ok(
            val span: Int,
            val days: Int,
            val coverage: Double,
            val avgIntake: Int,
            val avgBurned: Int,
            val deltaKg: Double,
            /** 反推出来的真实每日消耗 */
            val realTdee: Int,
            /** 真实值 − 公式估算值。正数说明公式偏低 */
            val gap: Int,
            val perWeek: Double,
        ) : Result
    }

    fun run(weights: List<WeightEntry>, logs: Map<String, HistoryEntry>): Result {
        val w = weights.sortedBy { it.date }
        if (w.size < 2) return Result.NeedTwo(w.size)

        val first = w.first()
        val last = w.last()
        val span = Dates.between(first.date, last.date)
        if (span < MIN_SPAN) return Result.TooShort(span)

        val days = logs.keys
            .filter { it >= first.date && it <= last.date && (logs[it]?.intake ?: 0) > 0 }
            .sorted()
        val coverage = days.size.toDouble() / span
        if (coverage < MIN_COVERAGE) return Result.TooSparse(days.size, span)

        val avgIn = days.sumOf { logs.getValue(it).intake.toDouble() } / days.size
        val avgOut = days.sumOf { logs.getValue(it).burned.toDouble() } / days.size
        val deltaKg = last.kg - first.kg
        val realTdee = (avgIn - deltaKg * KCAL_PER_KG / span).roundToInt()

        return Result.Ok(
            span = span,
            days = days.size,
            coverage = coverage,
            avgIntake = avgIn.roundToInt(),
            avgBurned = avgOut.roundToInt(),
            deltaKg = deltaKg,
            realTdee = realTdee,
            gap = realTdee - avgOut.roundToInt(),
            perWeek = deltaKg / (span / 7.0),
        )
    }

    data class Suggestion(val value: Int, val clamped: Boolean, val floor: Int)

    /**
     * 按每周想减 rate kg,反推每天该吃多少。
     * 结果不会低于性别下限 —— 低于那个数就很难吃够蛋白质和微量营养素了。
     */
    fun suggestTarget(tdee: Int, ratePerWeek: Double, profile: Profile?): Suggestion {
        val raw = (tdee - ratePerWeek * KCAL_PER_KG / 7).roundToInt()
        val floor = Nutrition.floorFor(profile)
        return Suggestion(max(raw, floor), raw < floor, floor)
    }

    /** 趋势线上的一个点 */
    data class TrendPoint(
        val date: String,
        val kg: Double,
        val bf: Double?,
        /** 7 次记录的移动平均 —— 界面上要反复强调看线不看点 */
        val avg: Double,
        val bfAvg: Double?,
        val lean: Double?,
    )

    fun trend(weights: List<WeightEntry>): List<TrendPoint> {
        val w = weights.sortedBy { it.date }
        return w.mapIndexed { i, pt ->
            val win = w.subList(max(0, i - 6), i + 1)
            val bfWin = win.mapNotNull { it.bf }
            TrendPoint(
                date = pt.date,
                kg = pt.kg,
                bf = pt.bf,
                avg = win.sumOf { it.kg } / win.size,
                bfAvg = if (bfWin.isEmpty()) null else bfWin.average(),
                lean = pt.leanKg,
            )
        }
    }

    /** 只保留能算出瘦体重的点。减脂期间这条线持平,说明掉的是脂肪不是肌肉 */
    fun leanTrend(weights: List<WeightEntry>): List<TrendPoint> =
        trend(weights).filter { it.lean != null }
}
