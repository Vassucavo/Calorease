package app.calorease.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.calorease.data.Food
import app.calorease.logic.Checked
import app.calorease.logic.Validate
import app.calorease.ui.theme.LocalColors

/** 列表和底部按钮之间的距离,同时也是列表淡出带的高度 */
private val Tail = 22.dp

/**
 * 「我的食物」列表面板。从设置页那一行的右箭头进来。
 *
 * 每条都能点开改 —— 录的时候手一抖填错一个数,以后每次复用都是错的,
 * 而这个列表原本只能删了重录。底部固定一个「添加食物」,不用先假装吃一次
 * 才能把常吃的东西存进来。
 *
 * 编辑/添加的面板**不在这里画**:它是二级浮层,得留在模糊层外面(见 App)。
 */
@Composable
fun BoxScope.MineSheet(
    mine: List<Food>,
    onDismiss: () -> Unit,
    onEdit: (Food) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val c = LocalColors.current

    BottomSheet(
        title = "我的食物",
        onDismiss = onDismiss,
        // 列表自己滚、自己淡出,底部那个按钮要一直在,不能跟着一起被削掉
        scrollable = false,
        contentBottomPadding = 0.dp,
        fadeBottom = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    // fill = false:条目少的时候面板就该矮,不要撑满整屏
                    .weight(1f, fill = false)
                    .fadeOutBottom(Tail)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (mine.isEmpty()) {
                    EmptyHint("还没有存过食物。用下面的「添加食物」录一条，或者在记录餐食时勾上「存入我的食物」。")
                } else {
                    mine.forEach { f ->
                        ItemRow(
                            name = f.name,
                            sub = (if (f.isPerHundredGrams) "每 100g · " else "每份 · ") +
                                (if (f.protein > 0) "${f.protein.f1()}g 蛋白质 · " else "") + "点击可编辑",
                            trailing = f.kcal.grouped(),
                            trailingColor = c.intake,
                            onTap = { onEdit(f) },
                            onDelete = { f.id?.let(onDelete) },
                        )
                    }
                }
            }

            SolidButton(
                "添加食物",
                onClick = onAdd,
                modifier = Modifier.padding(top = Tail),
            )
        }
    }
}

/**
 * 改一条已保存的食物,或者新录一条([food] 的 id 为空时)。
 *
 * 填的是**基准值**(每 100g 或者每份是多少),不是某一餐吃了多少 ——
 * 这一条是模板,加进记录的时候才乘份量。所以字段和「营养标签」那页一样。
 */
@Composable
fun BoxScope.MineEditSheet(
    food: Food,
    showProtein: Boolean,
    onDismiss: () -> Unit,
    onSave: (Food) -> Unit,
) {
    val isNew = food.id == null
    var name by remember(food) { mutableStateOf(food.name) }
    var perHundred by remember(food) { mutableStateOf(food.isPerHundredGrams) }
    var kcalText by remember(food) { mutableStateOf(if (food.kcal > 0) food.kcal.toString() else "") }
    var proteinText by remember(food) {
        mutableStateOf(if (food.protein > 0) food.protein.f1() else "")
    }
    var error by remember(food) { mutableStateOf<String?>(null) }

    BottomSheet(if (isNew) "添加食物" else "修改食物", onDismiss) {
        Column {
            Field("名称", name, { name = it }, placeholder = "希腊酸奶 草莓味")
            Segmented(
                options = listOf("每份", "每100g"),
                selectedIndex = if (perHundred) 1 else 0,
                onSelect = { perHundred = it == 1 },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(
                    if (perHundred) "每 100g 热量" else "每份热量",
                    kcalText, { kcalText = it },
                    modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
                )
                if (showProtein) {
                    Field(
                        if (perHundred) "每 100g 蛋白质（g）" else "每份蛋白质（g）",
                        proteinText, { proteinText = it },
                        modifier = Modifier.weight(1f), decimal = true, placeholder = "0",
                    )
                }
            }
            FieldError(error)

            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                // 校验先跑完,拿到 Valid 才构造要写回去的东西(错误档案第 10 条)
                when (val v = Validate.food(name, kcalText, proteinText)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> {
                        val (n, kcal, _) = v.value
                        // 蛋白质在库里是 Double,校验函数取整了,这里按原文重新解析一次
                        val protein = proteinText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                        onSave(
                            food.copy(
                                name = n,
                                kcal = kcal,
                                protein = protein,
                                unit = if (perHundred) "g" else "x",
                            )
                        )
                    }
                }
            })
        }
    }
}
