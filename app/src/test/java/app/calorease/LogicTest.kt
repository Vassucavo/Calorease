package app.calorease

import app.calorease.data.BurnEntry
import app.calorease.data.DayLog
import app.calorease.data.FoodEntry
import app.calorease.data.HistoryEntry
import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import app.calorease.logic.Calibration
import app.calorease.logic.Checked
import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import app.calorease.logic.Validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 这批断言是从网页版的 jsdom 测试逐条搬过来的,测的是行为不是实现。
 * 原生版必须在同样的输入下给出同样的数字 —— 换了语言不等于换了业务规则。
 */
class LogicTest {

    private val male = Profile(sex = "male", weightKg = 70, heightCm = 175, age = 30)
    private val female = Profile(sex = "female", weightKg = 55, heightCm = 162, age = 28)
    private val emptyDay = DayLog(date = "2026-05-01")

    // ---------- 基础代谢(Mifflin-St Jeor) ----------

    @Test
    fun `男性基础代谢`() {
        assertEquals((10 * 70 + 6.25 * 175 - 5 * 30 + 5).roundToInt(), Nutrition.bmr(male))
    }

    @Test
    fun `女性基础代谢`() {
        assertEquals((10 * 55 + 6.25 * 162 - 5 * 28 - 161).roundToInt(), Nutrition.bmr(female))
    }

    @Test
    fun `身体数据没填全时基础代谢是 0`() {
        assertEquals(0, Nutrition.bmr(null))
        assertEquals(0, Nutrition.bmr(Profile(sex = "male", weightKg = 70, heightCm = 175, age = 0)))
        assertEquals(0, Nutrition.bmr(Profile(sex = "male", weightKg = 0, heightCm = 175, age = 30)))
    }

    // ---------- 消耗 ----------

    @Test
    fun `消耗等于基础代谢加手表活动加手动运动`() {
        val day = DayLog(
            date = "2026-05-01",
            watchActive = 400,
            burn = listOf(BurnEntry("b1", "跑步", 300)),
        )
        assertEquals(Nutrition.bmr(male) + 400 + 300, Nutrition.burned(male, day))
    }

    @Test
    fun `活动消耗已含运动时手动条目不再累加`() {
        val day = DayLog(
            date = "2026-05-01",
            watchActive = 400,
            burn = listOf(BurnEntry("b1", "跑步", 300)),
        )
        val p = male.copy(activeIncludesWorkouts = true)
        // 那 300 已经在 400 里面了,再加一遍就是同一份消耗算两次
        assertEquals(Nutrition.bmr(p) + 400, Nutrition.burned(p, day))
    }

    @Test
    fun `份量文字按计量方式给出单位`() {
        assertEquals("200g", FoodEntry("a", "米饭", 200, unit = "g", amount = 200.0).portion)
        assertEquals("1.5 份", FoodEntry("b", "酸奶", 90, unit = "x", amount = 1.5).portion)
        // 手动改过的条目没有份量,那一行就不该出现
        assertEquals(null, FoodEntry("c", "食堂午餐", 600).portion)
        assertEquals(null, FoodEntry("d", "空的", 0, unit = "g", amount = 0.0).portion)
    }

    // ---------- 目标热量的两种模式 ----------

    @Test
    fun `按摄入模式下可吃额度就是设定值`() {
        val p = male.copy(target = 1800, targetMode = "intake")
        val day = DayLog(date = "2026-05-01", watchActive = 400)
        assertEquals(1800, Nutrition.allowance(p, day))
    }

    @Test
    fun `按结余模式下可吃额度随运动量浮动`() {
        val day = DayLog(date = "2026-05-01", watchActive = 400)
        val burned = Nutrition.burned(male, day)

        val deficit = male.copy(target = -500, targetMode = "net")
        assertEquals(burned - 500, Nutrition.allowance(deficit, day))

        val surplus = male.copy(target = 300, targetMode = "net")
        assertEquals(burned + 300, Nutrition.allowance(surplus, day))
    }

    @Test
    fun `缺口大于消耗时额度夹到 0 不出负数`() {
        val p = male.copy(target = -99999, targetMode = "net")
        assertEquals(0, Nutrition.allowance(p, DayLog(date = "2026-05-01")))
    }

    @Test
    fun `没设目标时额度是 0`() {
        assertEquals(0, Nutrition.allowance(male.copy(target = 0), emptyDay))
        assertEquals(0, Nutrition.allowance(null, emptyDay))
    }

    // ---------- 校准的三道守卫 ----------

    private fun logsFor(count: Int, intake: Int = 2000, burned: Int = 2200) =
        (1..count).associate {
            "2026-01-%02d".format(it) to HistoryEntry(intake = intake, burned = burned)
        }

    @Test
    fun `守卫一 少于两条体重记录就拒绝出数`() {
        val r = Calibration.run(listOf(WeightEntry("2026-01-01", 70.0)), emptyMap())
        assertTrue(r is Calibration.Result.NeedTwo)
        assertEquals(1, (r as Calibration.Result.NeedTwo).have)

        assertTrue(Calibration.run(emptyList(), emptyMap()) is Calibration.Result.NeedTwo)
    }

    @Test
    fun `守卫二 首尾跨度不足 14 天就拒绝出数`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-10", 69.0)),
            logsFor(10),
        )
        assertTrue(r is Calibration.Result.TooShort)
        assertEquals(9, (r as Calibration.Result.TooShort).span)
    }

    @Test
    fun `守卫二的边界 正好 14 天要能通过`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-15", 70.0)),
            logsFor(14, intake = 2000, burned = 2100),
        )
        assertTrue("正好 14 天应该通过,不是 15 天才行", r is Calibration.Result.Ok)
    }

    @Test
    fun `守卫三 有摄入记录的天数不足六成就拒绝出数`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-29", 69.0)),
            logsFor(8),
        )
        assertTrue(r is Calibration.Result.TooSparse)
        r as Calibration.Result.TooSparse
        assertEquals(8, r.logged)
        assertEquals(28, r.span)
    }

    @Test
    fun `摄入为 0 的那天不算进覆盖率`() {
        // 28 天里 20 天有记录、8 天是 0,20/28 = 71% 能过;
        // 但如果把 0 也算进去就会误判成 100%,拿一批空数据去算平均摄入
        val logs = logsFor(20) + (21..28).associate {
            "2026-01-%02d".format(it) to HistoryEntry(intake = 0, burned = 2200)
        }
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-29", 69.0)),
            logs,
        )
        assertTrue(r is Calibration.Result.Ok)
        assertEquals("只有真正记了的 20 天参与平均", 20, (r as Calibration.Result.Ok).days)
    }

    @Test
    fun `守卫全过时反推真实每日消耗`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-29", 69.0)),
            logsFor(28),
        )
        assertTrue(r is Calibration.Result.Ok)
        r as Calibration.Result.Ok
        assertEquals(28, r.span)
        assertEquals(28, r.days)
        // 28 天掉 1kg:2000 − (−1 × 7700 ÷ 28) = 2000 + 275
        assertEquals(2275, r.realTdee)
        assertEquals(2275 - 2200, r.gap)
        assertEquals(-0.25, r.perWeek, 1e-9)
        assertEquals(2000, r.avgIntake)
        assertEquals(2200, r.avgBurned)
    }

    @Test
    fun `体重没变时真实消耗就等于平均摄入`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-29", 70.0)),
            logsFor(28),
        )
        assertEquals(2000, (r as Calibration.Result.Ok).realTdee)
    }

    @Test
    fun `增重时真实消耗低于平均摄入`() {
        val r = Calibration.run(
            listOf(WeightEntry("2026-01-01", 70.0), WeightEntry("2026-01-29", 71.0)),
            logsFor(28),
        )
        assertEquals(2000 - 275, (r as Calibration.Result.Ok).realTdee)
    }

    // ---------- 建议目标 ----------

    @Test
    fun `建议目标不会低于性别下限`() {
        val f = Calibration.suggestTarget(1600, 0.5, female)
        assertEquals(1200, f.value)
        assertTrue(f.clamped)

        val m = Calibration.suggestTarget(1800, 0.5, male)
        assertEquals(1500, m.value)
        assertTrue(m.clamped)
    }

    @Test
    fun `消耗够高时建议目标不被夹`() {
        val s = Calibration.suggestTarget(2600, 0.5, male)
        assertFalse(s.clamped)
        assertEquals((2600 - 0.5 * 7700 / 7).roundToInt(), s.value)
    }

    // ---------- 趋势线 ----------

    @Test
    fun `趋势是 7 次记录的移动平均`() {
        val w = (1..8).map { WeightEntry("2026-01-%02d".format(it), 70.0 + it) }
        val t = Calibration.trend(w)
        assertEquals(8, t.size)
        // 第一个点只有自己
        assertEquals(71.0, t[0].avg, 1e-9)
        // 第七个点是前 7 个的平均:71..77
        assertEquals((71..77).sum() / 7.0, t[6].avg, 1e-9)
        // 第八个点滑出第一个:72..78
        assertEquals((72..78).sum() / 7.0, t[7].avg, 1e-9)
    }

    @Test
    fun `趋势按日期排序 补录的历史记录会插回正确位置`() {
        val w = listOf(
            WeightEntry("2026-03-01", 68.0),
            WeightEntry("2026-01-01", 75.0),
            WeightEntry("2026-02-01", 71.0),
        )
        assertEquals(
            listOf("2026-01-01", "2026-02-01", "2026-03-01"),
            Calibration.trend(w).map { it.date },
        )
    }

    @Test
    fun `瘦体重只在填了体脂率时算得出来`() {
        val w = listOf(
            WeightEntry("2026-01-01", 80.0, bf = 25.0),
            WeightEntry("2026-01-08", 79.0),
            WeightEntry("2026-01-15", 78.0, bf = 23.0),
        )
        val t = Calibration.trend(w)
        assertEquals(60.0, t[0].lean!!, 1e-9)   // 80 × (1 − 0.25)
        assertNull(t[1].lean)
        assertEquals(78 * 0.77, t[2].lean!!, 1e-9)

        assertEquals("只保留算得出瘦体重的点", 2, Calibration.leanTrend(w).size)
    }

    // ---------- 校验必须在修改数据之前(错误档案第 10 条) ----------

    @Test
    fun `体重校验 非法输入返回错误而不是一个记录`() {
        assertTrue(Validate.weight("abc", "2026-01-01", "") is Checked.Invalid)
        assertTrue(Validate.weight("", "2026-01-01", "") is Checked.Invalid)
        assertTrue(Validate.weight("10", "2026-01-01", "") is Checked.Invalid)
        assertTrue(Validate.weight("500", "2026-01-01", "") is Checked.Invalid)
        assertTrue(Validate.weight("70", "不是日期", "") is Checked.Invalid)
        assertTrue(Validate.weight("70", "2026-01-01", "999") is Checked.Invalid)
        assertTrue(Validate.weight("70", "2026-01-01", "1") is Checked.Invalid)
    }

    @Test
    fun `体重校验 不接受未来日期`() {
        val tomorrow = Dates.shift(Dates.today(), 1)
        assertTrue(Validate.weight("70", tomorrow, "") is Checked.Invalid)
        assertTrue(Validate.weight("70", Dates.today(), "") is Checked.Valid)
    }

    @Test
    fun `体重校验 合法输入四舍五入到 0_1`() {
        val r = Validate.weight("71.24", "2026-01-01", "19.48")
        assertTrue(r is Checked.Valid)
        val v = (r as Checked.Valid).value
        assertEquals(71.2, v.kg, 1e-9)
        assertEquals(19.5, v.bf!!, 1e-9)
    }

    @Test
    fun `体重校验 体脂留空是合法的`() {
        val r = Validate.weight("71", "2026-01-01", "   ")
        assertTrue(r is Checked.Valid)
        assertNull((r as Checked.Valid).value.bf)
    }

    @Test
    fun `目标校验 按摄入必须为正且不低于下限`() {
        assertTrue(Validate.target("0", netMode = false, profile = male) is Checked.Invalid)
        assertTrue(Validate.target("-500", netMode = false, profile = male) is Checked.Invalid)
        assertTrue(Validate.target("1400", netMode = false, profile = male) is Checked.Invalid)
        assertTrue(Validate.target("1400", netMode = false, profile = female) is Checked.Valid)
        assertTrue(Validate.target("1100", netMode = false, profile = female) is Checked.Invalid)
        assertEquals(1800, (Validate.target("1800", false, male) as Checked.Valid).value)
    }

    @Test
    fun `目标校验 按结余可以是负数`() {
        assertEquals(-500, (Validate.target("-500", netMode = true, profile = male) as Checked.Valid).value)
        assertEquals(300, (Validate.target("300", netMode = true, profile = male) as Checked.Valid).value)
        // 界面上可能输入的是 U+2212 减号
        assertEquals(-500, (Validate.target("−500", netMode = true, profile = male) as Checked.Valid).value)
    }

    @Test
    fun `目标校验 结余绝对值上限 1500`() {
        assertTrue(Validate.target("1500", netMode = true, profile = male) is Checked.Valid)
        assertTrue(Validate.target("-1500", netMode = true, profile = male) is Checked.Valid)
        assertTrue(Validate.target("1600", netMode = true, profile = male) is Checked.Invalid)
        assertTrue(Validate.target("-1600", netMode = true, profile = male) is Checked.Invalid)
    }

    @Test
    fun `目标校验 留空等于取消目标`() {
        assertEquals(0, (Validate.target("", false, male) as Checked.Valid).value)
        assertEquals(0, (Validate.target("  ", true, male) as Checked.Valid).value)
    }

    @Test
    fun `运动和活动卡路里的校验`() {
        assertTrue(Validate.burn("", "300") is Checked.Invalid)
        assertTrue(Validate.burn("跑步", "0") is Checked.Invalid)
        assertTrue(Validate.burn("跑步", "-100") is Checked.Invalid)
        assertTrue(Validate.burn("跑步", "9999") is Checked.Invalid)
        assertEquals("跑步" to 300, (Validate.burn(" 跑步 ", "300") as Checked.Valid).value)

        assertEquals(0, (Validate.watchActive("") as Checked.Valid).value)
        assertTrue(Validate.watchActive("-1") is Checked.Invalid)
        assertTrue(Validate.watchActive("abc") is Checked.Invalid)
        assertEquals(450, (Validate.watchActive("450") as Checked.Valid).value)
    }

    // ---------- 一天的汇总与清空 ----------

    @Test
    fun `一天的汇总`() {
        val day = DayLog(
            date = "2026-05-01",
            food = listOf(
                FoodEntry("f1", "米饭", 200, 4),
                FoodEntry("f2", "鸡胸肉", 165, 31),
            ),
            watchActive = 300,
            burn = listOf(BurnEntry("b1", "跑步", 250)),
        )
        assertEquals(365, day.eaten)
        assertEquals(35, day.protein)
        assertEquals(250, day.manualBurn)
        assertFalse(day.isEmpty)
    }

    @Test
    fun `删空之后这一天算空 不该留下全 0 的历史条目`() {
        assertTrue(DayLog(date = "2026-05-01").isEmpty)
        assertTrue(DayLog(date = "2026-05-01", food = emptyList(), watchActive = 0).isEmpty)
        // 只要还剩手表活动数据就不算空
        assertFalse(DayLog(date = "2026-05-01", watchActive = 300).isEmpty)
    }

    // ---------- 克数换算 ----------

    @Test
    fun `按 100g 计的食物换算`() {
        // 米饭 116 kcal/100g
        assertEquals(116, Nutrition.scale(116.0, 100.0, perHundredGrams = true))
        assertEquals(58, Nutrition.scale(116.0, 50.0, perHundredGrams = true))
        assertEquals(174, Nutrition.scale(116.0, 150.0, perHundredGrams = true))
        assertEquals(348, Nutrition.scale(116.0, 300.0, perHundredGrams = true))
    }

    @Test
    fun `按份计的食物换算`() {
        // 可颂 270 kcal/个
        assertEquals(270, Nutrition.scale(270.0, 1.0, perHundredGrams = false))
        assertEquals(135, Nutrition.scale(270.0, 0.5, perHundredGrams = false))
        assertEquals(405, Nutrition.scale(270.0, 1.5, perHundredGrams = false))
    }

    // ---------- 日期 ----------

    @Test
    fun `日期键的加减与跨度`() {
        assertEquals("2026-02-01", Dates.shift("2026-01-31", 1))
        assertEquals("2026-01-31", Dates.shift("2026-02-01", -1))
        assertEquals("2027-01-01", Dates.shift("2026-12-31", 1))
        assertEquals(28, Dates.between("2026-01-01", "2026-01-29"))
        assertEquals(-1, Dates.between("2026-01-02", "2026-01-01"))
        // 闰年
        assertEquals("2028-02-29", Dates.shift("2028-02-28", 1))
        assertEquals(366, Dates.between("2028-01-01", "2029-01-01"))
    }

    @Test
    fun `日期键按字典序排序等于按时间排序`() {
        val keys = listOf("2026-10-01", "2026-02-11", "2026-02-09", "2025-12-31")
        assertEquals(
            listOf("2025-12-31", "2026-02-09", "2026-02-11", "2026-10-01"),
            keys.sorted(),
        )
    }

    @Test
    fun `非法日期键会被识别出来`() {
        assertTrue(Dates.isValidKey("2026-07-31"))
        assertFalse(Dates.isValidKey("2026-7-31"))
        assertFalse(Dates.isValidKey("2026-13-01"))
        assertFalse(Dates.isValidKey("2026-02-30"))
        assertFalse(Dates.isValidKey(""))
    }
}
