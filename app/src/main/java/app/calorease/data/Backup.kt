package app.calorease.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * 备份文件。格式和网页壳版本(v8)导出的完全一致 —— 字段名、嵌套结构、
 * 版本号都没改,所以两版的备份可以互相导入。
 *
 * 整个应用不联网,备份是唯一的数据出口和入口:换手机、清数据、
 * 手滑删了记录,都靠它。
 */
@Serializable
data class BackupFile(
    val v: Int = 4,
    val exported: String = "",
    val profile: Profile? = null,
    val mine: List<Food> = emptyList(),
    val history: Map<String, HistoryEntry> = emptyMap(),
    val weights: List<WeightEntry> = emptyList(),
    val days: Map<String, DayLog> = emptyMap(),
)

object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun fileName(today: String) = "calorease-$today.json"

    fun export(store: Store): String {
        val history = store.loadHistory()
        return json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                v = 4,
                exported = Instant.now().toString(),
                profile = store.loadProfile(),
                mine = store.loadMine(),
                history = history,
                weights = store.loadWeights(),
                days = history.keys.associateWith { store.loadDay(it) },
            ),
        )
    }

    /**
     * 解析一个备份文件。认不出来就返回 null —— 调用方据此提示
     * 「这个文件不是本应用的备份」,而不是把一堆空数据写进去。
     */
    fun parse(text: String): BackupFile? {
        val parsed = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull()
            ?: return null
        // 光是合法 JSON 不够:必须至少带上身体数据或历史,才像是我们导出的东西
        if (parsed.profile == null && parsed.history.isEmpty()) return null
        return parsed
    }

    enum class Mode {
        /** 清空当前数据,完全用备份替换 */
        Replace,

        /** 保留现有记录,补进备份里有而本机没有的;重复的以备份为准 */
        Merge,
    }

    fun apply(store: Store, backup: BackupFile, mode: Mode) {
        if (mode == Mode.Replace) store.clearAll()

        backup.profile?.let { store.saveProfile(it) }

        val mine = if (mode == Mode.Replace) backup.mine else {
            val existing = store.loadMine()
            val byName = existing.associateBy { it.name.trim().lowercase() }.toMutableMap()
            backup.mine.forEach { byName[it.name.trim().lowercase()] = it }
            byName.values.toList()
        }
        store.saveMine(mine)

        val weights = if (mode == Mode.Replace) backup.weights else {
            val byDate = store.loadWeights().associateBy { it.date }.toMutableMap()
            backup.weights.forEach { byDate[it.date] = it }
            byDate.values.sortedBy { it.date }
        }
        store.saveWeights(weights)

        val history = if (mode == Mode.Replace) backup.history else {
            store.loadHistory() + backup.history
        }
        store.saveHistory(history)

        backup.days.forEach { (_, day) -> store.saveDay(day) }
    }
}
