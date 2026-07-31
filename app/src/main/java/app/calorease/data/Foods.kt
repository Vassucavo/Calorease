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

    /** 列表里的一段:一个组标题加它下面的条目 */
    data class Group(val title: String, val items: List<Food>, val fromMine: Boolean = false)

    /**
     * 搜索结果的分组。和网页版 drawList() 一致:
     *
     * - 搜索框**空着**时:先「最近常吃」(自建的按使用时间倒序取 5 条),
     *   后面接**整个内置库按分类铺开** —— 不是什么都不显示。
     *   这一点我第一版做错了,空搜索时只给了一句空提示。
     * - 有关键词时:先「我的食物」里名字命中的,再内置库里**名字或分类**
     *   命中的,同样按分类分组。分类也参与匹配,所以搜「主食」能出一整类。
     */
    fun grouped(context: Context, query: String, mine: List<Food>): List<Group> {
        val q = query.trim().lowercase()
        val out = mutableListOf<Group>()

        if (q.isEmpty()) {
            val recent = mine.sortedByDescending { it.used ?: 0L }.take(5)
            if (recent.isNotEmpty()) out += Group("最近常吃", recent, fromMine = true)
        } else {
            val hits = mine.filter { it.name.lowercase().contains(q) }
            if (hits.isNotEmpty()) out += Group("我的食物", hits, fromMine = true)
        }

        val matched = all(context).filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.cat.lowercase().contains(q)
        }
        // 按分类分组,顺序沿用 foods.json 里的出现顺序
        matched.groupBy { it.cat }.forEach { (cat, items) -> out += Group(cat, items) }

        return out
    }
}
