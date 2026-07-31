package app.calorease.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 内置食物库。来源是仓库根目录的 foods.json,构建时复制进 assets。
 *
 * 只读一次,之后常驻内存 —— 208 条撑死几十 KB,没必要反复解析。
 */
object Foods {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<Food>? = null

    fun all(context: Context): List<Food> {
        cache?.let { return it }
        val loaded = runCatching {
            context.assets.open("foods.json").bufferedReader().use { it.readText() }
                .let { json.decodeFromString<List<Food>>(it) }
        }.getOrElse {
            // 食物库读不出来不该让应用起不来 —— 搜索会是空的,但记账照常
            emptyList()
        }
        cache = loaded
        return loaded
    }

    /**
     * 搜索。内置库和「我的食物」一起搜,自建的排前面 ——
     * 自己录过的通常更准,也更可能是想找的那条。
     */
    fun search(context: Context, query: String, mine: List<Food>): List<Food> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hit = { f: Food -> f.name.lowercase().contains(q) || f.cat.lowercase().contains(q) }
        // 名字以关键词开头的排在包含关键词的前面
        val starts = { f: Food -> if (f.name.lowercase().startsWith(q)) 0 else 1 }
        return (mine.filter(hit).sortedBy(starts) + all(context).filter(hit).sortedBy(starts))
            .take(60)
    }

    /** 搜索框空着时显示最近常吃的几条 */
    fun recent(mine: List<Food>, limit: Int = 5): List<Food> =
        mine.sortedByDescending { it.used ?: 0L }.take(limit)
}
