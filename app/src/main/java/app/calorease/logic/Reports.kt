package app.calorease.logic

import app.calorease.data.HistoryEntry
import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import kotlin.math.roundToInt

/**
 * 把记录整理成一段纯文本,复制走给别的工具读。
 *
 * 这个应用不联网,也不打算联网(清单里连 INTERNET 权限都没有),所以
 * 「拿数据去分析」这件事只能靠剪贴板交接。既然如此,格式就要一次给全 ——
 * 让人再回来补一句「我身高多少」是最没必要的往返。
 *
 * 纯函数,和界面无关,所以能直接跑单元测试。
 */
object Reports {

    /**
     * 体重记录表。
     *
     * **热量一起给**。体重是结果,摄入和消耗是原因,只给结果那一列的话,
     * 对面只能说「你在掉秤」,说不出为什么、也说不出该调什么。两列并排,
     * 才能看出「这一周掉得慢是因为吃多了还是动少了」。
     *
     * 身体数据也带上一行:同样的掉秤速度,对 55kg 和 95kg 的人意义完全不同。
     */
    fun weightTable(
        weights: List<WeightEntry>,
        history: Map<String, HistoryEntry>,
        profile: Profile?,
    ): String {
        val sorted = weights.sortedBy { it.date }
        val sb = StringBuilder()

        sb.append("Calorease 体重与热量记录\n")
        if (profile != null && profile.isComplete) {
            sb.append(
                "身体数据：${if (profile.isFemale) "女" else "男"}，${profile.age} 岁，" +
                    "${profile.heightCm} cm，基础代谢约 ${Nutrition.bmr(profile)} 千卡\n"
            )
            if (profile.target != 0) {
                sb.append(
                    if (profile.isNetMode) {
                        "目标：每天结余 ${if (profile.target >= 0) "+" else "−"}${kotlin.math.abs(profile.target)} 千卡\n"
                    } else {
                        "目标：每天摄入 ${profile.target} 千卡\n"
                    }
                )
            }
        }
        sb.append("\n")

        if (sorted.isEmpty()) {
            sb.append("（还没有体重记录）\n")
            return sb.toString()
        }

        sb.append("日期,体重kg,体脂%,瘦体重kg,摄入kcal,消耗kcal\n")
        for (w in sorted) {
            val h = history[w.date]
            sb.append(w.date).append(',')
            sb.append(round1(w.kg)).append(',')
            sb.append(w.bf?.let { round1(it) } ?: "").append(',')
            sb.append(w.leanKg?.let { round1(it) } ?: "").append(',')
            sb.append(h?.intake?.toString() ?: "").append(',')
            sb.append(h?.burned?.toString() ?: "").append('\n')
        }

        // 首尾差和跨度先算好。这两个数对面自己也能从表里算,但算错的概率不低,
        // 而它们恰恰是整段数据里最关键的两个。
        if (sorted.size >= 2) {
            val span = Dates.between(sorted.first().date, sorted.last().date)
            val delta = sorted.last().kg - sorted.first().kg
            sb.append("\n")
            sb.append("跨度 $span 天，体重变化 ${if (delta >= 0) "+" else "−"}${round1(kotlin.math.abs(delta))} kg\n")
            if (span > 0) {
                // 每周变化要两位小数。一位的话 0.35 会进位成 0.4 ——
                // 而这个数正是判断「减得太快还是刚好」的依据,差 0.05 就是差一个档。
                val perWeek = delta / span * 7
                sb.append("平均每周 ${if (perWeek >= 0) "+" else "−"}${round2(kotlin.math.abs(perWeek))} kg\n")
            }
        }

        sb.append("\n体重的日常波动主要来自水分，看趋势不要看单日。\n")
        return sb.toString()
    }

    private fun round1(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r == r.toInt().toDouble()) "${r.toInt()}.0" else r.toString()
    }

    private fun round2(v: Double): String {
        val r = (v * 100).roundToInt() / 100.0
        return if (r == r.toInt().toDouble()) "${r.toInt()}.0" else r.toString()
    }
}
