package app.calorease.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.FoodEntry
import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import app.calorease.logic.Checked
import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import app.calorease.logic.Validate
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle

/**
 * 各种小表单。
 *
 * 全部遵守同一条规矩:**先跑校验,拿到 Checked.Valid 之后才调回调**。
 * 校验函数是纯函数,拿不到可变状态,所以「校验失败但数据已经被改了」
 * (错误档案第 10 条)这件事在结构上做不到。
 */

/**
 * 删除前的确认。
 *
 * 每一条删除都要过这里。这些记录没有回收站、没有撤销 —— 列表里那个叉离
 * 整行的点击区只有几毫米,手指一偏就没了,而它删掉的可能是两周前补录的
 * 一条体重,重建不回来。
 *
 * [what] 是被删的东西本身(食物名、日期),直接写在正文里,这样按下确认前
 * 你看到的是「删除 米饭」而不是「删除这一条」。
 */
@Composable
fun BoxScope.ConfirmDeleteSheet(
    what: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BottomSheet("删除确认", onDismiss) {
        Column {
            Note("将删除“$what”，此操作无法撤销。")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                DangerButton("删除", onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 活动消耗 —— 手表上「活动 / Move」那个数 */
@Composable
fun BoxScope.WatchActiveSheet(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet("活动消耗", onDismiss) {
        Column {
            Field("千卡", text, { text = it }, numeric = true, placeholder = "0")
            FieldError(error)
            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                when (val v = Validate.watchActive(text)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> { onSave(v.value); onDismiss() }
                }
            })
        }
    }
}

/** 一条手动运动记录 */
@Composable
fun BoxScope.BurnSheet(
    initialLabel: String = "",
    initialKcal: Int = 0,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var kcal by remember { mutableStateOf(if (initialKcal > 0) initialKcal.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet(if (initialLabel.isEmpty()) "添加运动" else "修改运动", onDismiss) {
        Column {
            Field("做了什么", label, { label = it }, placeholder = "跑步 30 分钟")
            Field("消耗（千卡）", kcal, { kcal = it }, numeric = true, placeholder = "0")
            Note("手表已经统计过的活动别再填一遍。这里适合记手表没戴、或者它没识别出来的运动。")
            FieldError(error)
            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                when (val v = Validate.burn(label, kcal)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> { onSave(v.value.first, v.value.second); onDismiss() }
                }
            })
        }
    }
}

/**
 * 改一条已经录进去的食物。
 *
 * 录入时如果留下了份量([FoodEntry.amount] / [FoodEntry.unit]),这里就**按份量改**:
 * 改克数或份数,热量自己跟着算。这才是记错时真正要改的东西 —— 你记得的是
 * 「吃了 200g 不是 150g」,不是「应该是 216 千卡不是 162 千卡」。
 *
 * 每单位的基准是从原来那条反推的:每 100g 热量 = 原热量 ÷ 原克数 × 100。
 * 没有份量的条目(手填的、或者更早版本记的)退回原来的样子,直接改热量 ——
 * 没有基准就换算不出来,硬编一个只会算错。
 */
@Composable
fun BoxScope.EditFoodSheet(
    entry: FoodEntry,
    showProtein: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, kcal: Int, protein: Int, amount: Double?) -> Unit,
) {
    val c = LocalColors.current
    // 基准:原来那条按每 100g(或每份)折算回去是多少
    val basis = entry.amount?.takeIf { it > 0 }
    val perGram = if (entry.unit == "g") "g" else "x"
    val perKcal = basis?.let { if (perGram == "g") entry.kcal / it * 100.0 else entry.kcal / it }
    val perProtein = basis?.let { if (perGram == "g") entry.protein / it * 100.0 else entry.protein / it }

    var name by remember { mutableStateOf(entry.name) }
    var amountText by remember {
        mutableStateOf(basis?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "")
    }
    var kcalText by remember { mutableStateOf(entry.kcal.toString()) }
    var proteinText by remember { mutableStateOf(if (entry.protein > 0) entry.protein.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    val amount = amountText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val previewKcal = if (perKcal != null && amount != null) {
        Nutrition.scale(perKcal, amount, perGram == "g")
    } else entry.kcal
    val previewProtein = if (perProtein != null && amount != null) {
        Nutrition.scale(perProtein, amount, perGram == "g")
    } else entry.protein

    BottomSheet("修改这一条", onDismiss) {
        Column {
            Field("名称", name, { name = it })

            if (perKcal != null) {
                Field(
                    if (perGram == "g") "重量（g）" else "份数",
                    amountText, { amountText = it },
                    decimal = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("热量", fontSize = 13.sp, color = c.muted)
                    Text(
                        previewKcal.grouped(),
                        style = NumberStyle,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.intake,
                    )
                }
                if (showProtein) StatRow("蛋白质", "${previewProtein}g", last = true)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("热量", kcalText, { kcalText = it }, modifier = Modifier.weight(1f), numeric = true)
                    if (showProtein) {
                        Field(
                            "蛋白质（g）", proteinText, { proteinText = it },
                            modifier = Modifier.weight(1f), numeric = true,
                        )
                    }
                }
            }

            FieldError(error)
            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                if (perKcal != null) {
                    if (amount == null) {
                        error = if (perGram == "g") "请输入重量。" else "请输入份数。"
                        return@SolidButton
                    }
                    // 名称仍然要过校验(不能是空的),数值这边是算出来的,不用再验
                    when (val v = Validate.food(name, previewKcal.toString(), previewProtein.toString())) {
                        is Checked.Invalid -> error = v.message
                        is Checked.Valid -> {
                            val (n, k, p) = v.value
                            onSave(n, k, p, amount)
                            onDismiss()
                        }
                    }
                } else {
                    when (val v = Validate.food(name, kcalText, proteinText)) {
                        is Checked.Invalid -> error = v.message
                        is Checked.Valid -> {
                            val (n, k, p) = v.value
                            onSave(n, k, p, null)
                            onDismiss()
                        }
                    }
                }
            })
        }
    }
}

/**
 * 记录 / 修改体重。
 *
 * [existing] 有值时是修改模式,它原来的日期会作为 replaceDate 传出去 ——
 * 改了日期的话旧那条要一起删掉,否则会变成两条。
 */
@Composable
fun BoxScope.WeightSheet(
    existing: WeightEntry?,
    /**
     * 选中的日期。提到 App 那一层管,因为月历是二级浮层 —— 它得画在模糊层
     * 外面,拿不到这个面板内部的状态。
     */
    date: String,
    onPickDate: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (WeightEntry, replaceDate: String?) -> Unit,
) {
    var kg by remember { mutableStateOf(existing?.kg?.f1() ?: "") }
    var bf by remember { mutableStateOf(existing?.bf?.f1() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet(if (existing == null) "记录体重" else "修改记录", onDismiss) {
        Column {
            Field("体重（kg）", kg, { kg = it }, decimal = true, placeholder = "70.0")
            Field("体脂率（%，可留空）", bf, { bf = it }, decimal = true, placeholder = "留空也行")

            DateRow(label = "日期", value = date, onClick = onPickDate)

            FieldError(error)

            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                // 校验全过了才动数据结构(错误档案第 10 条)
                when (val v = Validate.weight(kg, date, bf)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> {
                        onSave(v.value, existing?.date)
                        onDismiss()
                    }
                }
            })
        }
    }
}

/** 每日热量目标。两种模式,按结余时可以填负数 */
@Composable
fun BoxScope.TargetSheet(profile: Profile?, onDismiss: () -> Unit, onSave: (Int, Boolean) -> Unit) {
    var netMode by remember { mutableStateOf(profile?.isNetMode == true) }
    var text by remember { mutableStateOf(if ((profile?.target ?: 0) != 0) profile!!.target.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet("每日热量目标", onDismiss) {
        Column {
            Segmented(
                options = listOf("按摄入", "按结余"),
                selectedIndex = if (netMode) 1 else 0,
                onSelect = { netMode = it == 1; error = null },
            )
            Field(
                if (netMode) "每天结余（千卡，减重填负数）" else "每天摄入（千卡）",
                text, { text = it },
                // 按结余时要能打出负号。数字键盘在不少输入法上没有减号键,
                // 所以这一档退回普通键盘 —— 宁可键盘丑一点,也不能让人填不进去。
                decimal = !netMode,
                placeholder = if (netMode) "-500" else "1800",
            )
            Note(
                if (netMode) {
                    "结余 = 摄入 − 消耗。减重填 −500，增重填 +300。可吃额度会随当天运动量浮动 —— " +
                        "动得多就能多吃一点。绝对值上限 1500。"
                } else {
                    "固定每天吃多少，不随运动量变化。下限：女 1200 / 男 1500 —— " +
                        "低于这个数就很难吃够蛋白质和微量营养素了。"
                }
            )
            Note("留空 = 取消目标，今日页会改成显示当天结余。")
            FieldError(error)
            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                when (val v = Validate.target(text, netMode, profile)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> { onSave(v.value, netMode); onDismiss() }
                }
            })
        }
    }
}

/** 身体数据。首次启动时也是这个表单,只是不能取消 */
@Composable
fun BoxScope.ProfileSheet(
    profile: Profile?,
    firstRun: Boolean,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    val c = LocalColors.current
    var female by remember { mutableStateOf(profile?.isFemale == true) }
    var age by remember { mutableStateOf(profile?.age?.takeIf { it > 0 }?.toString() ?: "") }
    var height by remember { mutableStateOf(profile?.heightCm?.takeIf { it > 0 }?.toString() ?: "") }
    var weight by remember { mutableStateOf(profile?.weightKg?.takeIf { it > 0 }?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    // 首次启动时关不掉 —— 没有身体数据就算不出基础代谢,整个应用没法用
    BottomSheet("身体数据", onDismiss = { if (!firstRun) onDismiss() }) {
        Column {
            if (firstRun) {
                Note("这四个数用来算基础代谢（Mifflin-St Jeor）。只存在这台手机上，不会上传到任何地方。")
            }
            Segmented(
                options = listOf("男", "女"),
                selectedIndex = if (female) 1 else 0,
                onSelect = { female = it == 1 },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("年龄", age, { age = it }, modifier = Modifier.weight(1f), numeric = true)
                Field("身高（cm）", height, { height = it }, modifier = Modifier.weight(1f), decimal = true)
            }
            Field("体重（kg）", weight, { weight = it }, decimal = true)

            // 填全了就实时把基础代谢显示出来,不用保存完才知道算出多少
            val preview = Validate.profile(
                if (female) "female" else "male", age, height, weight, profile,
            )
            if (preview is Checked.Valid) {
                Card {
                    StatRow("基础代谢", Nutrition.bmr(preview.value).grouped(), valueColor = c.burn, last = true)
                }
                Note("这只是静息消耗。加上活动消耗和手动运动，才是当天的总消耗。")
            }

            FieldError(error)
            // 身体数据这一版在网页版用的是深墨底的 .btn,不是表单通用的 .btn.teal
            InkButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                when (val v = Validate.profile(if (female) "female" else "male", age, height, weight, profile)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> { onSave(v.value); onDismiss() }
                }
            })
        }
    }
}
