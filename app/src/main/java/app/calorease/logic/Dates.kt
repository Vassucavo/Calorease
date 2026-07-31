package app.calorease.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日期一律用 "YYYY-MM-DD" 字符串当键,和网页版一致 ——
 * 这样按字典序排序就等于按时间排序,存进 JSON 也不用管时区。
 */
object Dates {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): String = LocalDate.now().format(ISO)

    fun parse(key: String): LocalDate = LocalDate.parse(key, ISO)

    fun key(date: LocalDate): String = date.format(ISO)

    /** 从 key 往前/往后挪 n 天 */
    fun shift(key: String, n: Long): String = key(parse(key).plusDays(n))

    /** b − a,单位天。b 早于 a 时是负数 */
    fun between(a: String, b: String): Int =
        (parse(b).toEpochDay() - parse(a).toEpochDay()).toInt()

    fun isValidKey(s: String): Boolean =
        Regex("""^\d{4}-\d{2}-\d{2}$""").matches(s) && runCatching { parse(s) }.isSuccess

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 「7月31日」 */
    fun short(key: String): String = parse(key).let { "${it.monthValue}月${it.dayOfMonth}日" }

    /** 「7月31日 周五」 */
    fun full(key: String): String =
        "${short(key)} ${WEEKDAYS[parse(key).dayOfWeek.value - 1]}"
}
