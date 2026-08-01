package app.calorease.logic

import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 所有校验都在这里,而且一律是纯函数:输入字符串,输出「校验过的值」或者「错误说明」。
 *
 * 这是针对错误档案第 10 条的结构性修法。当初的 bug 是修改体重记录时
 * 先把旧记录删掉、再调用带校验的保存函数,校验失败时数据已经被改了。
 * 现在校验函数根本拿不到可变状态 —— 它只能返回一个 Valid 或者 Invalid,
 * 调用方拿到 Valid 才有东西可写。想在校验前改数据都做不到。
 */
sealed interface Checked<out T> {
    data class Valid<T>(val value: T) : Checked<T>
    data class Invalid(val message: String) : Checked<Nothing>
}

private fun invalid(msg: String) = Checked.Invalid(msg)

object Validate {

    /**
     * 一条体重记录。
     * kg 必填,20–400;日期必须是合法的过去或今天;体脂率可选,填了就要在 3–70 之间。
     */
    fun weight(kgText: String, dateKey: String, bfText: String): Checked<WeightEntry> {
        val kgRaw = kgText.trim().replace(',', '.').toDoubleOrNull()
        if (kgRaw == null || kgRaw < 20 || kgRaw > 400) {
            return invalid("体重请填 20 到 400 之间。")
        }
        val kg = (kgRaw * 10).roundToInt() / 10.0

        if (!Dates.isValidKey(dateKey)) return invalid("请选择日期。")
        if (Dates.between(dateKey, Dates.today()) < 0) return invalid("不能记录未来的日期。")

        var bf: Double? = null
        if (bfText.trim().isNotEmpty()) {
            val raw = bfText.trim().replace(',', '.').toDoubleOrNull()
            if (raw == null || raw < 3 || raw > 70) {
                return invalid("体脂率请填 3 到 70 之间，或留空。")
            }
            bf = (raw * 10).roundToInt() / 10.0
        }

        return Checked.Valid(WeightEntry(date = dateKey, kg = kg, bf = bf))
    }

    /**
     * 每日热量目标。
     *
     * intake 模式必须为正,而且不低于性别下限。
     * net    模式可以是负数(减重填 −500,增重填 +300),绝对值上限 1500。
     * 留空 = 取消目标,返回 0。
     */
    fun target(raw: String, netMode: Boolean, profile: Profile?): Checked<Int> {
        val text = raw.trim()
        if (text.isEmpty()) return Checked.Valid(0)

        val v = text.replace('−', '-').replace('，', ',').replace(",", "")
            .toDoubleOrNull()?.roundToInt()
            ?: return invalid("请输入数字。")

        if (netMode) {
            if (abs(v) > 1500) return invalid("结余绝对值请不要超过 1500。")
        } else {
            if (v <= 0) return invalid("按摄入设目标时须为正数。设缺口请切到“按结余”。")
            val floor = Nutrition.floorFor(profile)
            if (v < floor) {
                return invalid("目标不建议低于 $floor 千卡，再低就很难吃够蛋白质和微量营养素。")
            }
        }
        return Checked.Valid(v)
    }

    /** 身体数据 */
    fun profile(
        sex: String,
        ageText: String,
        heightCmText: String,
        weightKgText: String,
        existing: Profile?,
    ): Checked<Profile> {
        val age = ageText.trim().toIntOrNull()
        if (age == null || age < 10 || age > 120) return invalid("年龄请填 10 到 120 之间。")

        val cm = heightCmText.trim().replace(',', '.').toDoubleOrNull()
        if (cm == null || cm < 80 || cm > 250) return invalid("身高请填 80 到 250 cm 之间。")

        val kg = weightKgText.trim().replace(',', '.').toDoubleOrNull()
        if (kg == null || kg < 20 || kg > 400) return invalid("体重请填 20 到 400 kg 之间。")

        val base = existing ?: Profile()
        return Checked.Valid(
            base.copy(
                sex = if (sex == "female") "female" else "male",
                age = age,
                heightCm = cm.roundToInt(),
                weightKg = kg.roundToInt(),
            )
        )
    }

    /** 一条手动运动记录 */
    fun burn(label: String, kcalText: String): Checked<Pair<String, Int>> {
        val name = label.trim()
        if (name.isEmpty()) return invalid("请输入运动名称。")
        val kcal = kcalText.trim().toDoubleOrNull()?.roundToInt()
        if (kcal == null || kcal <= 0) return invalid("消耗请填一个正数。")
        if (kcal > 5000) return invalid("单条运动请不要超过 5000 千卡。")
        return Checked.Valid(name to kcal)
    }

    /** 当天的活动消耗 */
    fun watchActive(raw: String): Checked<Int> {
        val text = raw.trim()
        if (text.isEmpty()) return Checked.Valid(0)
        val v = text.toDoubleOrNull()?.roundToInt() ?: return invalid("请输入数字。")
        if (v < 0) return invalid("活动消耗不能为负数。")
        if (v > 10000) return invalid("活动消耗请不要超过 10000 千卡。")
        return Checked.Valid(v)
    }

    /** 自己录入的一条食物 */
    fun food(name: String, kcalText: String, proteinText: String): Checked<Triple<String, Int, Int>> {
        val n = name.trim()
        if (n.isEmpty()) return invalid("请输入名称。")
        val kcal = kcalText.trim().toDoubleOrNull()?.roundToInt()
        if (kcal == null || kcal < 0) return invalid("热量请填 0 或以上的数字。")
        if (kcal > 20000) return invalid("热量请不要超过 20000 千卡。")
        val protein = if (proteinText.trim().isEmpty()) 0 else
            proteinText.trim().replace(',', '.').toDoubleOrNull()?.roundToInt()
                ?: return invalid("蛋白质请填数字，或留空。")
        if (protein < 0) return invalid("蛋白质不能为负数。")
        return Checked.Valid(Triple(n, kcal, protein))
    }
}
