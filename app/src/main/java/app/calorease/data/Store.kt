package app.calorease.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

/**
 * 存储层。键名沿用网页版 localStorage 的那五种:
 *
 *     cl.profile / cl.mine / cl.history / cl.weights / cl.day.YYYY-MM-DD
 *
 * 只是落到 filesDir 下的同名 .json 文件。保持键名一致有两个好处:
 * 导出的备份文件在新旧两版之间可以互相导入,以后要排查问题也能直接
 * 拿旧数据当样本。
 *
 * 写入一律先写临时文件再改名,中途被杀进程不会留下半个 JSON。
 */
class Store(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun fileFor(key: String) = File(dir, "$key.json")

    private inline fun <reified T> read(key: String, fallback: T): T {
        val f = fileFor(key)
        if (!f.exists()) return fallback
        return runCatching { json.decodeFromString(serializer<T>(), f.readText()) }
            .getOrElse { fallback }
    }

    // 序列化器显式传进去。写成单参数的 json.encodeToString(value) 时,
    // 在 reified 泛型里编译器会去匹配 encodeToString(serializer, value) 那个重载,
    // 把 value 当成序列化器,报一串看不懂的类型错误。
    private inline fun <reified T> write(key: String, value: T): Boolean = runCatching {
        if (!dir.exists()) dir.mkdirs()
        val target = fileFor(key)
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(serializer<T>(), value))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }.isSuccess

    private fun remove(key: String) {
        runCatching { fileFor(key).delete() }
    }

    // ---------- 身体数据 ----------

    fun loadProfile(): Profile? = read<Profile?>(K_PROFILE, null)
    fun saveProfile(p: Profile) = write(K_PROFILE, p)

    // ---------- 我的食物 ----------

    fun loadMine(): List<Food> = read(K_MINE, emptyList())
    fun saveMine(list: List<Food>) = write(K_MINE, list)

    // ---------- 历史汇总 ----------

    fun loadHistory(): Map<String, HistoryEntry> = read(K_HISTORY, emptyMap())
    fun saveHistory(h: Map<String, HistoryEntry>) = write(K_HISTORY, h)

    // ---------- 体重 ----------

    fun loadWeights(): List<WeightEntry> = read(K_WEIGHTS, emptyList())
    fun saveWeights(list: List<WeightEntry>) = write(K_WEIGHTS, list)

    // ---------- 单日记录 ----------

    fun loadDay(date: String): DayLog = read(dayKey(date), DayLog(date = date))
    fun saveDay(day: DayLog) = write(dayKey(day.date), day)
    fun dropDay(date: String) = remove(dayKey(date))

    /** 备份/恢复要用:列出所有存着的日期 */
    fun allDayKeys(): List<String> =
        (dir.listFiles() ?: emptyArray())
            .mapNotNull { f ->
                f.name.removeSuffix(".json").takeIf { it.startsWith("$K_DAY_PREFIX.") }
            }
            .map { it.removePrefix("$K_DAY_PREFIX.") }
            .sorted()

    /** 「覆盖」模式恢复备份时要先清干净 */
    fun clearAll() {
        (dir.listFiles() ?: emptyArray())
            .filter { it.name.startsWith("cl.") }
            .forEach { it.delete() }
    }

    companion object {
        private const val K_PROFILE = "cl.profile"
        private const val K_MINE = "cl.mine"
        private const val K_HISTORY = "cl.history"
        private const val K_WEIGHTS = "cl.weights"
        private const val K_DAY_PREFIX = "cl.day"

        fun dayKey(date: String) = "$K_DAY_PREFIX.$date"
    }
}
