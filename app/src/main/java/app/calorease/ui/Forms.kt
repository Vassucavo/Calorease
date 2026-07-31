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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.calorease.data.Profile
import app.calorease.data.WeightEntry
import app.calorease.logic.Checked
import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import app.calorease.logic.Validate
import app.calorease.ui.theme.LocalColors

/**
 * 各种小表单。
 *
 * 全部遵守同一条规矩:**先跑校验,拿到 Checked.Valid 之后才调回调**。
 * 校验函数是纯函数,拿不到可变状态,所以「校验失败但数据已经被改了」
 * (错误档案第 10 条)这件事在结构上做不到。
 */

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

/** 改一条已经录进去的食物 */
@Composable
fun BoxScope.EditFoodSheet(
    initialName: String,
    initialKcal: Int,
    initialProtein: Int,
    showProtein: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf(initialKcal.toString()) }
    var protein by remember { mutableStateOf(if (initialProtein > 0) initialProtein.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet("修改这一条", onDismiss) {
        Column {
            Field("名称", name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("热量", kcal, { kcal = it }, modifier = Modifier.weight(1f), numeric = true)
                if (showProtein) {
                    Field("蛋白质（g）", protein, { protein = it }, modifier = Modifier.weight(1f), numeric = true)
                }
            }
            FieldError(error)
            SolidButton("保存", modifier = Modifier.padding(top = 12.dp), onClick = {
                when (val v = Validate.food(name, kcal, protein)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> {
                        val (n, k, p) = v.value
                        onSave(n, k, p)
                        onDismiss()
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
    onDismiss: () -> Unit,
    onSave: (WeightEntry, replaceDate: String?) -> Unit,
) {
    var kg by remember { mutableStateOf(existing?.kg?.f1() ?: "") }
    var bf by remember { mutableStateOf(existing?.bf?.f1() ?: "") }
    // 默认就是今天,所以不需要一个「今天」按钮 —— 什么都不动就已经是今天
    var date by remember { mutableStateOf(existing?.date ?: Dates.today()) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheet(
        if (existing == null) "记录体重" else "修改记录",
        onDismiss,
        dimmed = pickingDate,
    ) {
        Column {
            Field("体重（kg）", kg, { kg = it }, decimal = true, placeholder = "70.0")
            Field("体脂率（%，可留空）", bf, { bf = it }, decimal = true, placeholder = "留空也行")

            DateRow(label = "日期", value = date) { pickingDate = true }

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

    // 月历叠在这个面板上面,选完就收 —— 和「添加餐食 → 食物详情」是同一套层级
    if (pickingDate) {
        DatePickSheet(
            value = date,
            onDismiss = { pickingDate = false },
            onPick = {
                date = it
                pickingDate = false
            },
        )
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
