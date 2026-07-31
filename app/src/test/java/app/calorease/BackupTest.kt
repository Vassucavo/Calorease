package app.calorease

import app.calorease.data.Backup
import app.calorease.data.BurnEntry
import app.calorease.data.DayLog
import app.calorease.data.Food
import app.calorease.data.FoodEntry
import app.calorease.data.HistoryEntry
import app.calorease.data.Profile
import app.calorease.data.Repository
import app.calorease.data.Store
import app.calorease.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 备份是这个应用唯一的数据出口和入口 —— 不联网,没有云端兜底。
 * 所以这里测得细一点:导出再导回来必须一条不差。
 *
 * Store 只依赖 java.io.File,不碰任何安卓 API,所以能直接在 JVM 上测。
 */
class BackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore() = Store(tmp.newFolder())

    private fun seed(store: Store) {
        store.saveProfile(
            Profile(
                sex = "male", age = 30, heightCm = 175, weightKg = 70,
                target = 1800, targetMode = "intake", showProtein = true,
            )
        )
        store.saveMine(
            listOf(
                Food(name = "食堂午餐", kcal = 650, protein = 28.0, unit = "x", id = "m1", used = 100L),
                Food(name = "自制沙拉", kcal = 120, protein = 6.0, unit = "g", id = "m2", used = 200L),
            )
        )
        store.saveWeights(
            listOf(
                WeightEntry("2026-05-01", 70.5, bf = 20.5),
                WeightEntry("2026-05-15", 69.8),
            )
        )
        store.saveHistory(
            mapOf(
                "2026-05-01" to HistoryEntry(intake = 1900, burned = 2200, protein = 95, watchActive = 400),
                "2026-05-02" to HistoryEntry(intake = 2100, burned = 2150, protein = 88, watchActive = 320),
            )
        )
        store.saveDay(
            DayLog(
                date = "2026-05-01",
                food = listOf(FoodEntry("f1", "米饭", 200, 4, 1L), FoodEntry("f2", "鸡胸肉", 165, 31, 2L)),
                watchActive = 400,
                burn = listOf(BurnEntry("b1", "跑步", 300)),
            )
        )
        store.saveDay(DayLog(date = "2026-05-02", food = listOf(FoodEntry("f3", "面条", 350, 12, 3L))))
    }

    @Test
    fun `导出再导回来 一条不差`() {
        val src = newStore()
        seed(src)
        val text = Backup.export(src)

        val dst = newStore()
        val parsed = Backup.parse(text)
        assertNotNull("导出的东西必须能被自己解析回来", parsed)
        Backup.apply(dst, parsed!!, Backup.Mode.Replace)

        assertEquals(src.loadProfile(), dst.loadProfile())
        assertEquals(src.loadWeights(), dst.loadWeights())
        assertEquals(src.loadHistory(), dst.loadHistory())
        assertEquals(
            src.loadMine().map { it.name }.toSet(),
            dst.loadMine().map { it.name }.toSet(),
        )
        assertEquals(src.loadDay("2026-05-01"), dst.loadDay("2026-05-01"))
        assertEquals(src.loadDay("2026-05-02"), dst.loadDay("2026-05-02"))
    }

    @Test
    fun `导出带上了每一天的明细 不只是汇总`() {
        val src = newStore()
        seed(src)
        val b = Backup.parse(Backup.export(src))!!
        assertEquals(2, b.days.size)
        assertEquals("米饭", b.days.getValue("2026-05-01").food.first().name)
        assertEquals(300, b.days.getValue("2026-05-01").burn.first().kcal)
    }

    @Test
    fun `覆盖模式会清掉本机原有的数据`() {
        val src = newStore()
        seed(src)
        val text = Backup.export(src)

        val dst = newStore()
        dst.saveWeights(listOf(WeightEntry("2020-01-01", 99.0)))
        dst.saveDay(DayLog(date = "2020-01-01", food = listOf(FoodEntry("x", "旧记录", 500, 0))))

        Backup.apply(dst, Backup.parse(text)!!, Backup.Mode.Replace)

        assertFalse(
            "覆盖之后不该还留着本机原来的体重",
            dst.loadWeights().any { it.date == "2020-01-01" },
        )
        assertTrue("覆盖之后旧的那天应该没了", dst.loadDay("2020-01-01").isEmpty)
    }

    @Test
    fun `合并模式两边都留 冲突时以备份为准`() {
        val src = newStore()
        seed(src)
        val text = Backup.export(src)

        val dst = newStore()
        dst.saveWeights(
            listOf(
                WeightEntry("2020-01-01", 99.0),          // 备份里没有,要保留
                WeightEntry("2026-05-01", 88.8),          // 两边都有,以备份为准
            )
        )

        Backup.apply(dst, Backup.parse(text)!!, Backup.Mode.Merge)

        val byDate = dst.loadWeights().associateBy { it.date }
        assertEquals("本机独有的记录要留着", 99.0, byDate.getValue("2020-01-01").kg, 1e-9)
        assertEquals("冲突的以备份为准", 70.5, byDate.getValue("2026-05-01").kg, 1e-9)
        assertEquals("备份独有的也要进来", 69.8, byDate.getValue("2026-05-15").kg, 1e-9)
    }

    @Test
    fun `合并时同名的自建食物不会变成两条`() {
        val src = newStore()
        src.saveMine(listOf(Food(name = "食堂午餐", kcal = 650, unit = "x", id = "a")))
        src.saveProfile(Profile(age = 30, heightCm = 175, weightKg = 70))
        val text = Backup.export(src)

        val dst = newStore()
        dst.saveMine(listOf(Food(name = "食堂午餐", kcal = 700, unit = "x", id = "b")))

        Backup.apply(dst, Backup.parse(text)!!, Backup.Mode.Merge)

        assertEquals(1, dst.loadMine().size)
        assertEquals("冲突时以备份为准", 650, dst.loadMine().first().kcal)
    }

    @Test
    fun `认不出来的文件一律拒绝`() {
        assertNull(Backup.parse("这不是 JSON"))
        assertNull(Backup.parse(""))
        assertNull(Backup.parse("{}"))
        assertNull("光是合法 JSON 不够", Backup.parse("""{"hello":1}"""))
        assertNull("没有身体数据也没有历史,不像是我们导出的", Backup.parse("""{"v":4,"weights":[]}"""))
    }

    @Test
    fun `旧版网页壳导出的备份也能导进来`() {
        // 字段名和嵌套结构都没改过,所以 v8 网页版的备份可以直接用
        val legacy = """
            {
              "v": 4,
              "exported": "2026-07-30T12:00:00.000Z",
              "profile": {"sex":"female","units":"metric","age":28,"heightCm":162,
                          "weightKg":55,"showProtein":false,"target":1500,"targetMode":"intake"},
              "mine": [{"name":"燕麦","kcal":380,"protein":13,"unit":"g","id":"z1","used":1}],
              "history": {"2026-07-01":{"in":1480,"out":1900,"p":70,"act":250}},
              "weights": [{"date":"2026-07-01","kg":55.4,"bf":26.5}],
              "days": {"2026-07-01":{"date":"2026-07-01",
                        "food":[{"id":"q1","name":"燕麦粥","kcal":300,"protein":10,"ts":1}],
                        "watchActive":250,"burn":[]}}
            }
        """.trimIndent()

        val parsed = Backup.parse(legacy)
        assertNotNull(parsed)

        val store = newStore()
        Backup.apply(store, parsed!!, Backup.Mode.Replace)

        assertEquals(1500, store.loadProfile()!!.target)
        assertTrue(store.loadProfile()!!.isFemale)
        assertEquals(1480, store.loadHistory().getValue("2026-07-01").intake)
        assertEquals(26.5, store.loadWeights().first().bf!!, 1e-9)
        assertEquals("燕麦粥", store.loadDay("2026-07-01").food.first().name)
    }

    @Test
    fun `存储层写进去再读出来是同一份`() {
        val store = newStore()
        seed(store)
        assertEquals(2, store.loadWeights().size)
        assertEquals(365, store.loadDay("2026-05-01").eaten)
        assertEquals(listOf("2026-05-01", "2026-05-02"), store.allDayKeys())

        store.dropDay("2026-05-01")
        assertTrue(store.loadDay("2026-05-01").isEmpty)
        assertEquals(listOf("2026-05-02"), store.allDayKeys())
    }

    @Test
    fun `读不出来的文件退回默认值 不抛异常`() {
        val dir = tmp.newFolder()
        java.io.File(dir, "cl.profile.json").writeText("{ 坏掉的 json")
        java.io.File(dir, "cl.weights.json").writeText("不是 JSON")
        val store = Store(dir)

        assertNull(store.loadProfile())
        assertTrue(store.loadWeights().isEmpty())
    }

    // ---------- Repository 的两条关键行为 ----------

    @Test
    fun `删空当天时历史条目一起清掉`() {
        val store = newStore()
        store.saveProfile(Profile(age = 30, heightCm = 175, weightKg = 70))
        val repo = Repository(store)
        repo.load()

        repo.addFood("米饭", 200, 4)
        assertTrue(repo.state.value.history.containsKey(repo.state.value.curDate))

        val id = repo.state.value.day.food.first().id
        repo.deleteFood(id)

        val date = repo.state.value.curDate
        assertFalse("删空之后不该留下全 0 的历史条目", repo.state.value.history.containsKey(date))
        assertTrue(store.loadDay(date).isEmpty)
    }

    @Test
    fun `只有最新那条体重才回写身体数据`() {
        val store = newStore()
        store.saveProfile(Profile(age = 30, heightCm = 175, weightKg = 70))
        val repo = Repository(store)
        repo.load()

        repo.saveWeight(WeightEntry("2026-03-01", 68.0))
        assertEquals(68, repo.state.value.profile!!.weightKg)

        repo.saveWeight(WeightEntry("2026-01-01", 75.0))
        assertEquals("补录历史不该改变今天的基础代谢", 68, repo.state.value.profile!!.weightKg)

        repo.saveWeight(WeightEntry("2026-06-01", 66.0))
        assertEquals(66, repo.state.value.profile!!.weightKg)
    }

    @Test
    fun `改体重记录的日期不会变成两条`() {
        val store = newStore()
        val repo = Repository(store)
        repo.load()

        repo.saveWeight(WeightEntry("2026-03-01", 70.0))
        assertEquals(1, repo.state.value.weights.size)

        // 把那条从 3 月 1 日挪到 3 月 3 日
        repo.saveWeight(WeightEntry("2026-03-03", 70.0), replaceDate = "2026-03-01")
        assertEquals(1, repo.state.value.weights.size)
        assertEquals("2026-03-03", repo.state.value.weights.first().date)
    }
}
