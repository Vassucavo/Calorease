package app.calorease.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 数据结构和网页壳版本(v8)的 localStorage 完全一致 —— 字段名一个都没改。
 *
 * 这样做的理由:导出的备份文件在新旧两版之间可以互相导入,
 * 以后要是需要拿旧数据做样本调试也不用写转换层。
 *
 * 存储位置从 localStorage 的 cl.* 键换成 filesDir 下的同名 JSON 文件,
 * 见 Store.kt。
 */

/** 身体数据与偏好,对应 cl.profile */
@Serializable
data class Profile(
    val sex: String = "male",                 // "male" / "female"
    val units: String = "metric",             // "metric" / "imperial"
    val age: Int = 0,
    val heightCm: Int = 0,
    val weightKg: Int = 0,
    // 英制界面上原样填的值,只为了切回英制时不丢失用户输入的精度
    val heightIn: String? = null,
    val weightLb: String? = null,
    val showProtein: Boolean = false,
    /** 目标热量。intake 模式必须为正;net 模式可以是负数(缺口) */
    val target: Int = 0,
    /** "intake" = 每天固定吃多少;"net" = 每天结余多少 */
    val targetMode: String = "intake",
    /** 玻璃质感主题开关 */
    val glass: Boolean = false,
) {
    val isFemale get() = sex == "female"
    val isNetMode get() = targetMode == "net"
    /** 身体数据填全了才算完成初始设置 */
    val isComplete get() = age > 0 && heightCm > 0 && weightKg > 0
}

/** 一条吃进去的东西,存在当天的 day.food 里 */
@Serializable
data class FoodEntry(
    val id: String,
    val name: String,
    val kcal: Int,
    val protein: Int = 0,
    val ts: Long = 0L,
)

/** 一条手动运动记录,存在当天的 day.burn 里 */
@Serializable
data class BurnEntry(
    val id: String,
    val label: String,
    val kcal: Int,
)

/** 某一天的完整记录,对应 cl.day.YYYY-MM-DD */
@Serializable
data class DayLog(
    val date: String,
    val food: List<FoodEntry> = emptyList(),
    /** 手表当天的活动卡路里 */
    val watchActive: Int = 0,
    val burn: List<BurnEntry> = emptyList(),
) {
    val eaten: Int get() = food.sumOf { it.kcal }
    val protein: Int get() = food.sumOf { it.protein }
    val manualBurn: Int get() = burn.sumOf { it.kcal }
    /** 一天里什么都没有 —— persist 时要连历史条目一起清掉,不能留全 0 残留 */
    val isEmpty: Boolean get() = food.isEmpty() && watchActive == 0 && burn.isEmpty()
}

/** 历史里每天的汇总,对应 cl.history 的一个值。字段名短是为了省 localStorage 空间,沿用 */
@Serializable
data class HistoryEntry(
    @SerialName("in") val intake: Int,
    @SerialName("out") val burned: Int,
    @SerialName("p") val protein: Int = 0,
    @SerialName("act") val watchActive: Int = 0,
)

/** 一条体重记录,存在 cl.weights 里。bf = 体脂率,可选 */
@Serializable
data class WeightEntry(
    val date: String,
    val kg: Double,
    val bf: Double? = null,
) {
    /** 瘦体重 = 体重 × (1 − 体脂率)。没填体脂时算不出来 */
    val leanKg: Double? get() = bf?.let { kg * (1 - it / 100.0) }
}

/** 食物库里的一条,来自仓库根目录的 foods.json,或用户自建的 cl.mine */
@Serializable
data class Food(
    val cat: String = "",
    val name: String,
    val kcal: Int,
    val protein: Double = 0.0,
    /** "g" = 每 100g 的数值;"x" = 每份的数值 */
    val unit: String = "g",
    val id: String? = null,
    /** 最近一次使用的时间戳,只有自建食物有,用来排「最近常吃」 */
    val used: Long? = null,
) {
    val isPerHundredGrams get() = unit == "g"
}
