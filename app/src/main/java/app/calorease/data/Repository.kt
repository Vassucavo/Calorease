package app.calorease.data

import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全部应用状态的唯一来源。界面只读 [state],改动一律走这里的方法。
 *
 * 每个方法都遵守同一条规矩:**先算出新的完整状态,再一次性写下去**。
 * 不存在「改了一半」的中间态,也就不会出现错误档案第 9 条那种
 * 「更新了值却忘了刷新 UI」—— 状态只有一个,发出去 UI 就跟着变。
 */
class Repository(private val store: Store) {

    data class AppState(
        val profile: Profile? = null,
        val mine: List<Food> = emptyList(),
        val history: Map<String, HistoryEntry> = emptyMap(),
        val weights: List<WeightEntry> = emptyList(),
        /** 当前正在看的日期,不一定是今天(可以翻到过去补录) */
        val curDate: String = Dates.today(),
        val day: DayLog = DayLog(date = Dates.today()),
    ) {
        val isFirstRun: Boolean get() = profile == null || !profile.isComplete
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun load() {
        val today = Dates.today()
        _state.value = AppState(
            profile = store.loadProfile(),
            mine = store.loadMine(),
            history = store.loadHistory(),
            weights = store.loadWeights().sortedBy { it.date },
            curDate = today,
            day = store.loadDay(today),
        )
    }

    /** 应用回到前台时如果已经跨天了,把视图挪到新的今天 */
    fun refreshDateIfStale() {
        val today = Dates.today()
        val s = _state.value
        if (s.curDate != today && s.curDate == Dates.shift(today, -1)) {
            openDate(today)
        }
    }

    // ---------- 日期 ----------

    fun openDate(date: String) {
        _state.value = _state.value.copy(curDate = date, day = store.loadDay(date))
    }

    fun stepDay(delta: Long) = openDate(Dates.shift(_state.value.curDate, delta))

    fun jumpToToday() {
        if (_state.value.curDate != Dates.today()) openDate(Dates.today())
    }

    // ---------- 当天记录 ----------

    private fun commitDay(day: DayLog) {
        val s = _state.value
        val history = s.history.toMutableMap()

        if (day.isEmpty) {
            // 错误档案第 12 条:删空的一天要连同历史条目一起清掉,
            // 否则会留下一条全 0 的记录,把校准的覆盖率拉低。
            store.dropDay(day.date)
            history.remove(day.date)
        } else {
            store.saveDay(day)
            history[day.date] = HistoryEntry(
                intake = day.eaten,
                burned = Nutrition.burned(s.profile, day),
                protein = day.protein,
                watchActive = day.watchActive,
            )
        }
        store.saveHistory(history)
        _state.value = s.copy(day = day, history = history)
    }

    /** [amount]/[unit] 是吃了多少,今日页那行副文字要用。算不出来就传 null */
    fun addFood(name: String, kcal: Int, protein: Int, amount: Double? = null, unit: String? = null) {
        val s = _state.value
        val entry = FoodEntry(
            id = newId(),
            name = name.trim(),
            kcal = kcal,
            protein = protein,
            ts = System.currentTimeMillis(),
            amount = amount,
            unit = unit,
        )
        commitDay(s.day.copy(food = s.day.food + entry))
    }

    fun updateFood(id: String, name: String, kcal: Int, protein: Int) {
        val s = _state.value
        commitDay(
            s.day.copy(
                food = s.day.food.map {
                    if (it.id == id) it.copy(name = name.trim(), kcal = kcal, protein = protein) else it
                }
            )
        )
    }

    fun deleteFood(id: String) {
        val s = _state.value
        commitDay(s.day.copy(food = s.day.food.filterNot { it.id == id }))
    }

    fun setWatchActive(kcal: Int) {
        commitDay(_state.value.day.copy(watchActive = kcal))
    }

    fun addBurn(label: String, kcal: Int) {
        val s = _state.value
        commitDay(s.day.copy(burn = s.day.burn + BurnEntry(newId(), label, kcal)))
    }

    fun updateBurn(id: String, label: String, kcal: Int) {
        val s = _state.value
        commitDay(
            s.day.copy(burn = s.day.burn.map { if (it.id == id) it.copy(label = label, kcal = kcal) else it })
        )
    }

    fun deleteBurn(id: String) {
        val s = _state.value
        commitDay(s.day.copy(burn = s.day.burn.filterNot { it.id == id }))
    }

    // ---------- 身体数据与偏好 ----------

    fun saveProfile(p: Profile) {
        store.saveProfile(p)
        _state.value = _state.value.copy(profile = p)
    }

    private fun mutateProfile(block: (Profile) -> Profile) {
        val p = _state.value.profile ?: Profile()
        saveProfile(block(p))
    }

    fun setTarget(value: Int, netMode: Boolean) =
        mutateProfile { it.copy(target = value, targetMode = if (netMode) "net" else "intake") }

    fun toggleProtein() = mutateProfile { it.copy(showProtein = !it.showProtein) }

    fun setGlass(on: Boolean) = mutateProfile { it.copy(glass = on) }

    /**
     * 切「活动消耗已含运动」。
     *
     * 它会改变每一天的总消耗,所以历史汇总里存的 burned 也跟着过时了 ——
     * 这里把所有存过的日子重算一遍写回去,不然校准页会拿着两套口径的数
     * 一起做回归,算出来的目标是错的。
     */
    fun toggleActiveIncludesWorkouts() {
        mutateProfile { it.copy(activeIncludesWorkouts = !it.activeIncludesWorkouts) }
        val s = _state.value
        val p = s.profile
        val history = s.history.mapValues { (date, h) ->
            h.copy(burned = Nutrition.burned(p, store.loadDay(date)))
        }
        store.saveHistory(history)
        _state.value = _state.value.copy(history = history)
    }

    // ---------- 体重 ----------

    /**
     * 保存一条体重记录。[entry] 必须是已经通过 Validate.weight 校验的 ——
     * 这个方法不做校验,也不该做:校验一旦和写入放在同一个方法里,
     * 就迟早会有人在校验之前先动数据(错误档案第 10 条)。
     *
     * [replaceDate] 是修改旧记录时那条记录原来的日期。改了日期的话,
     * 旧日期那条要一起删掉,否则会变成两条。
     */
    fun saveWeight(entry: WeightEntry, replaceDate: String? = null) {
        val s = _state.value
        val next = s.weights
            .filterNot { it.date == entry.date || (replaceDate != null && it.date == replaceDate) }
            .plus(entry)
            .sortedBy { it.date }
        store.saveWeights(next)

        // 只有最新那条才回写身体数据里的体重 —— 它决定基础代谢。
        // 补录历史记录不该改变今天的基础代谢。
        var profile = s.profile
        if (profile != null && next.isNotEmpty() && next.last().date == entry.date) {
            profile = profile.copy(weightKg = Math.round(entry.kg).toInt())
            store.saveProfile(profile)
        }
        _state.value = s.copy(weights = next, profile = profile)
    }

    fun deleteWeight(date: String) {
        val s = _state.value
        val next = s.weights.filterNot { it.date == date }
        store.saveWeights(next)
        _state.value = s.copy(weights = next)
    }

    // ---------- 我的食物 ----------

    /** 自己录入过的食物存起来,下次一键复用。同名的覆盖,不新增一条 */
    fun rememberFood(food: Food) {
        val s = _state.value
        val key = food.name.trim().lowercase()
        val existing = s.mine.firstOrNull { it.name.trim().lowercase() == key }
        val rec = food.copy(
            id = existing?.id ?: newId(),
            used = System.currentTimeMillis(),
        )
        val next = if (existing != null) {
            s.mine.map { if (it.id == existing.id) rec else it }
        } else {
            listOf(rec) + s.mine
        }
        store.saveMine(next)
        _state.value = s.copy(mine = next)
    }

    /** 用过一次就更新时间戳,「最近常吃」才排得对 */
    fun touchFood(name: String) {
        val s = _state.value
        val key = name.trim().lowercase()
        if (s.mine.none { it.name.trim().lowercase() == key }) return
        val next = s.mine.map {
            if (it.name.trim().lowercase() == key) it.copy(used = System.currentTimeMillis()) else it
        }
        store.saveMine(next)
        _state.value = s.copy(mine = next)
    }

    fun deleteMine(id: String) {
        val s = _state.value
        val next = s.mine.filterNot { it.id == id }
        store.saveMine(next)
        _state.value = s.copy(mine = next)
    }

    companion object {
        private var counter = 0L

        /** 短随机 id,够用就行 —— 只需要在一台手机的一份数据里唯一 */
        fun newId(): String =
            (System.currentTimeMillis().toString(36) + (counter++).toString(36)).takeLast(9)
    }
}
