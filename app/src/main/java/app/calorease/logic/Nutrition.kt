package app.calorease.logic

import app.calorease.data.DayLog
import app.calorease.data.Profile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 消耗模型:
 *
 *     每日消耗 = 基础代谢(Mifflin-St Jeor) + 手表活动卡路里 + 手动运动条目
 *
 * 刻意没有用活动系数(1.2 / 1.375 / …),因为那会和手表的活动卡路里重复计算。
 * 代价是漏掉了食物热效应(约摄入的 10%),所以这个模型系统性偏低 ——
 * 但校准会自动把这个偏差吸收掉,见 Calibration.kt。
 */
object Nutrition {

    /** 基础代谢率。身体数据没填全时返回 0 */
    fun bmr(p: Profile?): Int {
        if (p == null || p.weightKg <= 0 || p.heightCm <= 0 || p.age <= 0) return 0
        val base = 10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.age
        return (if (p.isFemale) base - 161 else base + 5).roundToInt()
    }

    /** 当天总消耗 */
    fun burned(p: Profile?, day: DayLog): Int =
        bmr(p) + day.watchActive + day.manualBurn

    /**
     * 今天还能吃多少。
     *
     * intake 模式 —— 就是设定值,不随运动量变化。
     * net    模式 —— 消耗 + 结余目标(结余为负即缺口),会随运动量浮动。
     *                缺口大于消耗时夹到 0,不出负数。
     */
    fun allowance(p: Profile?, day: DayLog): Int {
        val t = p?.target ?: 0
        if (t == 0) return 0
        return if (p!!.isNetMode) max(0, burned(p, day) + t) else t
    }

    /** 目标热量的下限。低于这个数就很难吃够蛋白质和微量营养素 */
    fun floorFor(p: Profile?): Int = if (p?.sex == "male") 1500 else 1200

    /**
     * 按「每 100g」或「每份」的食物库数值,换算成实际吃下去的热量/蛋白质。
     * amount 在 g 模式下是克数,在 x 模式下是份数。
     */
    fun scale(per: Double, amount: Double, perHundredGrams: Boolean): Int =
        (if (perHundredGrams) per * amount / 100.0 else per * amount).roundToInt()
}
