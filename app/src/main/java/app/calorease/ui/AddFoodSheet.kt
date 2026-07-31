package app.calorease.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Food
import app.calorease.data.Foods
import app.calorease.data.Profile
import app.calorease.logic.Checked
import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import app.calorease.logic.Validate
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.NumberStyle

private val GRAM_PRESETS = listOf(50.0, 100.0, 150.0, 200.0, 300.0)
private val PORTION_PRESETS = listOf(0.5, 1.0, 1.5, 2.0)

private enum class AddTab(val label: String) {
    Search("搜索"),
    Quick("快速录入"),
    Label("营养标签"),
}

/**
 * 添加餐食面板。三种录入方式:
 *
 *   搜索     —— 从 208 条内置库和「我的食物」里找,选中后按克数/份数换算
 *   快速录入 —— 配合「拍照问 AI 再手填」的流程,带一个复制提示词的按钮
 *   营养标签 —— 照着包装上的每份数值填,再乘份数
 *
 * 后两种录入的东西会存进「我的食物」,下次一键复用。
 */
@Composable
fun BoxScope.AddFoodSheet(
    profile: Profile?,
    mine: List<Food>,
    curDate: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, kcal: Int, protein: Int) -> Unit,
    onRemember: (Food) -> Unit,
    onTouch: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(AddTab.Search) }
    var picked by remember { mutableStateOf<Food?>(null) }

    BottomSheet(
        title = "添加餐食",
        onDismiss = onDismiss,
        // 搜索页要用带吸顶头的列表,自己管滚动;其余分页交给浮层滚
        scrollable = picked != null || tab != AddTab.Search,
    ) {
        val p = picked
        if (p != null) {
            PickedDetail(
                food = p,
                showProtein = profile?.showProtein == true,
                curDate = curDate,
                onBack = { picked = null },
                onCommit = { kcal, protein ->
                    onAdd(p.name, kcal, protein)
                    onTouch(p.name)
                    onDismiss()
                },
            )
        } else {
            SheetTabs(
                options = AddTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { tab = AddTab.entries[it] },
            )
            when (tab) {
                AddTab.Search -> SearchTab(mine = mine, onPick = { picked = it })
                AddTab.Quick -> QuickTab(
                    showProtein = profile?.showProtein == true,
                    onCommit = { food, kcal, protein, save ->
                        if (save) onRemember(food)
                        onAdd(food.name, kcal, protein)
                        onDismiss()
                    },
                )
                AddTab.Label -> LabelTab(
                    showProtein = profile?.showProtein == true,
                    onCommit = { food, kcal, protein, save ->
                        if (save) onRemember(food)
                        onAdd(food.name, kcal, protein)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** 选中一条食物之后:快捷份量 + 自由输入 + 实时预览 */
@Composable
private fun PickedDetail(
    food: Food,
    showProtein: Boolean,
    curDate: String,
    onBack: () -> Unit,
    onCommit: (kcal: Int, protein: Int) -> Unit,
) {
    val c = LocalColors.current
    val isGram = food.isPerHundredGrams
    var amount by remember(food) { mutableStateOf(if (isGram) 100.0 else 1.0) }
    var text by remember(food) { mutableStateOf(if (isGram) "100" else "1") }

    val kcal = Nutrition.scale(food.kcal.toDouble(), amount, isGram)
    val protein = Nutrition.scale(food.protein, amount, isGram)

    Column {
        Card(modifier = Modifier.padding(bottom = 14.dp)) {
            Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                if (isGram) "每 100g 约 ${food.kcal} 千卡" else "每份约 ${food.kcal} 千卡",
                fontSize = 12.sp,
                color = c.muted,
                modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
            )

            QuickAmounts(
                presets = if (isGram) GRAM_PRESETS else PORTION_PRESETS,
                selected = amount,
                unitLabel = if (isGram) "g" else "份",
                onPick = {
                    // 值和输入框一起更新。错误档案第 9 条的坑(改了值忘了刷新高亮)
                    // 在这里不成立 —— 高亮是从 amount 当场算的,没有第二份状态。
                    amount = it
                    text = if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString()
                },
            )

            Field(
                label = if (isGram) "重量（g）" else "份数",
                value = text,
                onChange = {
                    text = it
                    amount = it.trim().toDoubleOrNull()?.takeIf { v -> v > 0 } ?: 0.0
                },
                decimal = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Bottom,
            ) {
                Text("热量", fontSize = 13.sp, color = c.muted)
                Text(
                    kcal.grouped(),
                    style = NumberStyle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.intake,
                )
            }
            if (showProtein) StatRow("蛋白质", "${protein}g", last = true)
        }

        SolidButton(
            "加入" + if (curDate == Dates.today()) "今天" else Dates.short(curDate),
            onClick = { onCommit(kcal, protein) },
            background = c.intake,
        )
        GhostButton("返回列表", onClick = onBack, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * 搜索。搜索框空着时铺开整个内置食物库(按分类分组),自建过的排在最前面。
 *
 * 搜索框是**吸顶**的:列表往上滚时它停在顶部不动。滚动开始之后,
 * 它下面会淡入一条「上实下透」的渐变,让滚过去的内容自然消失在它底下 ——
 * 网页版靠 position:sticky 加一个 opacity 从 0 淡入的 ::before 实现,
 * 这里用 stickyHeader 加一层随滚动状态淡入的渐变底。
 *
 * 网页版当初特意做成「不滚动时完全透明」,是因为固定色值的渐变没法和
 * 半透明面板精确对色,一有色差就会出现接缝(错误档案第 11 条)。
 * 让它平时消失,就绕开了这个问题 —— 这里沿用同一招。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTab(mine: List<Food>, onPick: (Food) -> Unit) {
    val c = LocalColors.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val groups = Foods.grouped(context, query, mine)

    val listState = rememberLazyListState()
    val stuck by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val fade by animateFloatAsState(if (stuck) 1f else 0f, label = "stick")

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (fade <= 0f) return@drawBehind
                        drawRect(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to c.panelBg.copy(alpha = c.panelBg.alpha * fade),
                                    0.56f to c.panelBg.copy(alpha = c.panelBg.alpha * fade),
                                    1f to Color.Transparent,
                                )
                            )
                        )
                    }
                    .padding(bottom = 8.dp),
            ) {
                Field(label = "", value = query, onChange = { query = it }, placeholder = "搜索食物…")
            }
        }

        if (groups.isEmpty()) {
            item { EmptyHint("没找到。用“快速录入”自己填一条，填完会存起来。") }
        } else {
            groups.forEach { g ->
                item(key = "head-" + g.title) { GroupHead(g.title) }
                items(g.items, key = { it.id ?: (g.title + it.name) }) { f ->
                    PickRow(f, isMine = g.fromMine, onPick = onPick)
                }
            }
        }
    }
}

/** `.grouphead` —— 11sp、字距 .06em、muted、600,上 14 下 6 */
@Composable
private fun GroupHead(title: String) {
    Text(
        title,
        fontSize = 11.sp,
        letterSpacing = 0.66.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalColors.current.muted,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/** `.pick` —— 和普通条目同尺寸,副行写计量方式,自建的多一个「已保存」标 */
@Composable
private fun PickRow(food: Food, isMine: Boolean, onPick: (Food) -> Unit) {
    val c = LocalColors.current
    ItemRow(
        name = food.name,
        sub = (if (food.isPerHundredGrams) "每100g" else "每份") + if (isMine) " · 已保存" else "",
        trailing = food.kcal.grouped(),
        trailingColor = c.intake,
        onTap = { onPick(food) },
    )
}

/** 快速录入。配合「拍照问 AI 再手填」的流程 */
@Composable
private fun QuickTab(
    showProtein: Boolean,
    onCommit: (Food, kcal: Int, protein: Int, save: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var perHundred by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    Column {
        Field("吃了什么", name, { name = it }, placeholder = "食堂午餐")
        Segmented(
            options = listOf("整份", "每100g"),
            selectedIndex = if (perHundred) 1 else 0,
            onSelect = { perHundred = it == 1 },
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Field(
                if (perHundred) "每 100g 热量" else "热量",
                kcalText, { kcalText = it },
                modifier = Modifier.weight(1f),
                numeric = true,
                placeholder = "0",
            )
            if (showProtein) {
                Field(
                    "蛋白质（g）", proteinText, { proteinText = it },
                    modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
                )
            }
        }
        CheckRow(save, "存进“我的食物”，下次一键复用") { save = it }
        FieldError(error)

        SolidButton(
            "加入记录",
            background = LocalColors.current.intake,
            onClick = {
                // 校验先跑完,通过了才构造要写进去的东西
                when (val v = Validate.food(name, kcalText, proteinText)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> {
                        val (n, kcal, protein) = v.value
                        error = null
                        val food = Food(
                            name = n,
                            kcal = kcal,
                            protein = protein.toDouble(),
                            unit = if (perHundred) "g" else "x",
                        )
                        // 按 100g 录的,加进当天记录时也按 100g 算一份
                        onCommit(food, kcal, protein, save)
                    }
                }
            },
        )
        Note("拍下餐食，发给任意 AI 对话问热量，再把数字填到这里。")
        GhostButton(
            if (copied) "提示词已复制" else "复制提问用的提示词",
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                copyPrompt(context, showProtein)
                copied = true
            },
        )
    }
}

/** 营养标签。照着包装上的每份数值填,再乘份数 */
@Composable
private fun LabelTab(
    showProtein: Boolean,
    onCommit: (Food, kcal: Int, protein: Int, save: Boolean) -> Unit,
) {
    val c = LocalColors.current
    var name by remember { mutableStateOf("") }
    var perServing by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var save by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val n = servings.trim().toDoubleOrNull() ?: 0.0
    val total = ((perServing.trim().toDoubleOrNull() ?: 0.0) * n).toInt()

    Column {
        Field("产品名称", name, { name = it }, placeholder = "希腊酸奶 草莓味")
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Field(
                "每份热量", perServing, { perServing = it },
                modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
            )
            if (showProtein) {
                Field(
                    "每份蛋白质（g）", proteinText, { proteinText = it },
                    modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
                )
            }
        }
        Field("吃了几份", servings, { servings = it }, decimal = true)

        Card(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.Bottom,
            ) {
                Text("合计", fontSize = 13.sp, color = c.muted)
                Text(
                    total.grouped(),
                    style = NumberStyle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.intake,
                )
            }
        }

        CheckRow(save, "存进“我的食物”") { save = it }
        FieldError(error)

        SolidButton(
            "加入记录",
            background = c.intake,
            onClick = {
                when (val v = Validate.food(name, perServing, proteinText)) {
                    is Checked.Invalid -> error = v.message
                    is Checked.Valid -> {
                        if (n <= 0) {
                            error = "份数请填一个正数。"
                            return@SolidButton
                        }
                        val (nm, perKcal, perProtein) = v.value
                        error = null
                        // 存进「我的食物」的是每份的数值,加进记录的是乘完份数的
                        val food = Food(name = nm, kcal = perKcal, protein = perProtein.toDouble(), unit = "x")
                        onCommit(
                            food,
                            (perKcal * n).toInt(),
                            (perProtein * n).toInt(),
                            save,
                        )
                    }
                }
            },
        )
        Note("注意包装上的份量 —— 看着像一份的一袋，标签上常常写的是两三份。")
    }
}

private fun copyPrompt(context: Context, showProtein: Boolean) {
    val text = "看这张我这一餐的照片，估算每样食物的份量，告诉我总热量" +
        (if (showProtein) "和总蛋白质克数" else "") +
        "。请先列出每样食物和它的数值，再给出合计，并说明你假设的份量。"
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Calorease", text))
}
