package app.calorease

import app.calorease.data.Food
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * foods.json 放在仓库根目录,是为了能在 GitHub 网页上直接编辑。
 * 正因为它可以手改,这里守得严一点 —— 一个手误就会让打进包里的食物库出错。
 */
class FoodsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val foods: List<Food> by lazy {
        val path = System.getProperty("foods.json")
            ?: error("测试没拿到 foods.json 的路径,检查 app/build.gradle.kts 里的 systemProperty")
        json.decodeFromString<List<Food>>(File(path).readText())
    }

    @Test
    fun `食物库能解析且条目数没有意外变化`() {
        assertEquals("从网页版搬过来时是 208 条", 208, foods.size)
    }

    @Test
    fun `每条都有名字和非负热量`() {
        foods.forEach {
            assertTrue("食物名不能为空", it.name.isNotBlank())
            assertTrue("${it.name} 的热量是负数", it.kcal >= 0)
            assertTrue("${it.name} 的蛋白质是负数", it.protein >= 0)
            assertTrue("${it.name} 的分类为空", it.cat.isNotBlank())
        }
    }

    @Test
    fun `计量方式只有 g 和 x 两种`() {
        val units = foods.map { it.unit }.toSet()
        assertEquals(setOf("g", "x"), units)
        assertEquals("按 100g 计的", 162, foods.count { it.unit == "g" })
        assertEquals("按份计的", 46, foods.count { it.unit == "x" })
    }

    @Test
    fun `没有重名的食物`() {
        val dupes = foods.groupBy { it.name.trim() }.filterValues { it.size > 1 }.keys
        assertTrue("这些名字出现了不止一次:$dupes", dupes.isEmpty())
    }

    @Test
    fun `分类没有因为手误多出新的一个`() {
        assertEquals(
            listOf(
                "主食", "肉类", "水产", "蛋豆", "乳制品", "蔬菜", "水果",
                "坚果油脂", "饮料", "零食", "调味", "外食·中餐", "外食·西餐",
            ),
            foods.map { it.cat }.distinct(),
        )
    }

    @Test
    fun `按 100g 计的条目热量在合理范围内`() {
        // 每 100g 超过 900 千卡在物理上不可能(纯脂肪也就 900)
        foods.filter { it.unit == "g" }.forEach {
            assertTrue("${it.name} 每 100g ${it.kcal} 千卡,超过纯脂肪了", it.kcal <= 900)
        }
    }

    @Test
    fun `蛋白质换算成热量不会超过总热量`() {
        // 1g 蛋白质约 4 千卡。留 15% 余量给参考值本身的误差。
        foods.filter { it.kcal > 0 }.forEach {
            val fromProtein = it.protein * 4
            assertTrue(
                "${it.name}:${it.protein}g 蛋白质约 $fromProtein 千卡,但总热量只有 ${it.kcal}",
                fromProtein <= it.kcal * 1.15,
            )
        }
    }
}
