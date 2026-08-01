package app.calorease.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** 浮层内容的左右内边距。这个面板把它下放给各屏自己加,所以要有个常量 */
private val SheetPad = 18.dp

/** 底部按钮区留的空,以及内容淡出带的高度 */
private val SheetTail = 22.dp

private enum class AddTab(val label: String) {
    Search("搜索"),
    Quick("快速录入"),
    Label("营养标签"),
}

/**
 * 「快速录入」的表单状态。
 *
 * 提到面板这一层,是因为提交按钮固定在面板底部、在分页内容之外 ——
 * 按钮要读得到表单才提交得了。
 */
private class QuickForm {
    var name by mutableStateOf("")
    var kcalText by mutableStateOf("")
    var proteinText by mutableStateOf("")
    var gramsText by mutableStateOf("")
    var perHundred by mutableStateOf(false)
    var save by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}

/** 「营养标签」的表单状态 */
private class LabelForm {
    var name by mutableStateOf("")
    var perServing by mutableStateOf("")
    var proteinText by mutableStateOf("")
    var servings by mutableStateOf("1")
    var save by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}

/**
 * 添加餐食面板。三种录入方式:
 *
 *   搜索     —— 从内置库和「我的食物」里找,选中后弹出食物详情再定份量
 *   快速录入 —— 自己填名字和热量,按整份或者按每 100g × 克数
 *   营养标签 —— 照着包装上的每份数值填,再乘份数
 *
 * 后两种录入的东西会存进「我的食物」,下次一键复用。
 *
 * 面板高度**固定**成「营养标签」那页的自然高度:三页内容长短差很多,不固定
 * 的话一切分页面板就忽上忽下地跳。做法是先量一遍那一页,再把量出来的高度
 * 扣到真正显示的那一页上,见 [MatchHeight]。
 */
@Composable
fun BoxScope.AddFoodSheet(
    profile: Profile?,
    mine: List<Food>,
    onDismiss: () -> Unit,
    onAdd: (name: String, kcal: Int, protein: Int, amount: Double?, unit: String?) -> Unit,
    onRemember: (Food) -> Unit,
    /**
     * 选中一条食物。详情面板**不在这里画** —— 它是二级浮层,必须留在
     * 模糊层外面(见 App),不然连它自己也会被一起糊掉。
     */
    onPick: (Food) -> Unit,
) {
    var tab by remember { mutableStateOf(AddTab.Search) }
    val showProtein = profile?.showProtein == true
    val quick = remember { QuickForm() }
    val label = remember { LabelForm() }

    BottomSheet(
        title = "添加餐食",
        onDismiss = onDismiss,
        // 每一屏都自己管滚动:搜索页要让搜索框固定、只滚下面的列表
        scrollable = false,
        // 左右内边距下放给各屏自己加 —— 搜索列表要自己带缩进,而淡出要覆盖整宽
        contentPadding = 0.dp,
        contentBottomPadding = 0.dp,
        // 底部有固定按钮,浮层统一的淡出会把按钮一起削掉,这里自己只淡出内容区
        fadeBottom = 0.dp,
    ) {
        MatchHeight(
            // 量的是「营养标签」那一页 —— 三页里最长的
            probe = {
                AddFoodBody(
                    tab = AddTab.Label,
                    showProtein = showProtein,
                    quick = remember { QuickForm() },
                    label = remember { LabelForm() },
                    mine = mine,
                    fill = false,
                    onSelectTab = {},
                    onPick = {},
                    onCommit = { _, _, _, _, _ -> },
                )
            },
        ) {
            AddFoodBody(
                tab = tab,
                showProtein = showProtein,
                quick = quick,
                label = label,
                mine = mine,
                fill = true,
                onSelectTab = { tab = it },
                onPick = onPick,
                onCommit = { food, kcal, protein, amount, save ->
                    if (save) onRemember(food)
                    onAdd(food.name, kcal, protein, amount, food.unit)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * 高度对齐:先量 [probe] 的自然高度,再让 [content] 按这个高度摆出来。
 *
 * 两次 subcompose 用不同的槽位,所以量身的那份有自己独立的状态,不会和真正
 * 显示的那份串味。它只被测量、不被摆放,画不出来也拿不到焦点。
 *
 * **量身用的约束只能放开下限,不能放开上限。** 里面有 verticalScroll,
 * 而可滚动容器一旦被无限高度测量,Compose 是直接抛异常的
 * (「Vertically scrollable component was measured with an infinity maximum
 * height constraints」),不是退化成某个默认值 —— 我第一版写的
 * maxHeight = Infinity,结果点开「添加餐食」当场闪退。
 *
 * 用父级给的上限去量本来也够:量出来的高度反正还要按这个上限夹一次,
 * 上限之外的部分本就用不上。
 */
@Composable
private fun MatchHeight(
    probe: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val natural = subcompose(0, probe).maxOfOrNull { it.measure(loose).height } ?: 0
        val target =
            if (constraints.hasBoundedHeight) natural.coerceAtMost(constraints.maxHeight)
            else natural
        val exact = constraints.copy(minHeight = target, maxHeight = target)
        val body = subcompose(1, content).map { it.measure(exact) }
        layout(constraints.maxWidth, target) { body.forEach { it.place(0, 0) } }
    }
}

/**
 * 面板正文:顶部三个分页 + 当前这页的内容 + 底部固定的提交按钮。
 *
 * [fill] 为 true 时按父级给的高度铺满(真正显示的那份),为 false 时按内容
 * 自然高度长(量身用的那份)。量身那份不能铺满,也不能给中间那段挂 weight ——
 * 那样量出来的永远是「父级还剩多少」,不是这一页本身有多长。
 */
@Composable
private fun AddFoodBody(
    tab: AddTab,
    showProtein: Boolean,
    quick: QuickForm,
    label: LabelForm,
    mine: List<Food>,
    fill: Boolean,
    onSelectTab: (AddTab) -> Unit,
    onPick: (Food) -> Unit,
    onCommit: (Food, kcal: Int, protein: Int, amount: Double?, save: Boolean) -> Unit,
) {
    Column(modifier = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = SheetPad)) {
            SheetTabs(
                options = AddTab.entries.map { it.label },
                selectedIndex = tab.ordinal,
                onSelect = { onSelectTab(AddTab.entries[it]) },
            )
        }

        // 量身的那份不挂滚动也不挂 weight:它要报的是「这一页有多长」,
        // 挂了 weight 报的就成了「父级还剩多少」,挂了滚动则多担一份风险 ——
        // 可滚动容器一旦被无限高度测量,Compose 是直接抛异常的。
        val scrollState = rememberScrollState()
        val body = if (fill) {
            Modifier.weight(1f).fadeOutBottom(SheetTail).verticalScroll(scrollState)
        } else {
            Modifier
        }

        when (tab) {
            AddTab.Search -> SearchTab(mine = mine, onPick = onPick, fill = fill)

            AddTab.Quick -> Column(modifier = body.padding(horizontal = SheetPad)) {
                QuickTab(showProtein, quick)
            }

            AddTab.Label -> Column(modifier = body.padding(horizontal = SheetPad)) {
                LabelTab(showProtein, label)
            }
        }

        // 搜索页没有可提交的东西,那一页的空间全给食物列表。
        //
        // 「存入我的食物」这个勾选放在这里而不是各自的表单里,是因为它在两页里
        // 是同一件事,位置就该固定。留在表单里的话,「整份」比「每100g」少一个
        // 克数框,这一行就会跟着往上跳一格。
        if (tab != AddTab.Search) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SheetPad)
                    .padding(bottom = SheetTail),
            ) {
                if (tab == AddTab.Quick) {
                    CheckRow(quick.save, "存入“我的食物”") { quick.save = it }
                    FieldError(quick.error)
                } else {
                    CheckRow(label.save, "存入“我的食物”") { label.save = it }
                    FieldError(label.error)
                }
                SolidButton(
                    "加入记录",
                    background = LocalColors.current.intake,
                    onClick = {
                        if (tab == AddTab.Quick) submitQuick(quick, onCommit)
                        else submitLabel(label, onCommit)
                    },
                )
            }
        }
    }
}

/**
 * 搜索。搜索框空着时铺开整个内置食物库(按分类分组),自建过的排在最前面。
 *
 * 搜索框**完全不动**:它在列表外面,不是 stickyHeader。之前用吸顶头,滚到
 * 列表末尾时它会跟着一起被弹性拉动 —— 看着就不像固定的。
 *
 * 列表上下都会淡出,而且是把内容自身的 alpha 推到 0(见 [fadeOutBottom]),
 * 不是拿一条渐变色带盖住它 —— 色带的颜色只能靠算,和半透明面板对不准就穿帮。
 */
@Composable
private fun ColumnScope.SearchTab(mine: List<Food>, onPick: (Food) -> Unit, fill: Boolean) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val groups = Foods.grouped(context, query, mine)

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    // 顶端只在滚起来之后才淡出 —— 停在最上面时第一条不该是灰的
    val fade by animateFloatAsState(if (scrolled) 1f else 0f, label = "topFade")

    Column(modifier = Modifier.padding(horizontal = SheetPad)) {
        Field(label = "", value = query, onChange = { query = it }, placeholder = "搜索食物…")
    }

    // 量身那一趟不摆列表 —— 惰性列表被无限高度测量会直接抛异常,而这一页
    // 本来就不参与定高(高度是照「营养标签」那页定的)。
    if (!fill) return

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .fadeEdges(top = 16.dp * fade, bottom = SheetTail),
        // 左右内边距给列表自己,而不是给外面的容器 —— 淡出要整宽,条目要缩进
        contentPadding = PaddingValues(start = SheetPad, end = SheetPad, bottom = 8.dp),
    ) {
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

/**
 * `.pick` —— 一条可选的食物。
 *
 * 排成**一行**:名称、计量方式、热量。之前计量方式是单独一行副文字,每条就要
 * 占两行高;面板定高之后,一屏能看见的食物少了将近一半。
 */
@Composable
private fun PickRow(food: Food, isMine: Boolean, onPick: (Food) -> Unit) {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .glassSurface(10.dp, c.rowBg)
            .clickable { onPick(food) }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            food.name,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = c.ink,
        )
        Text(
            (if (food.isPerHundredGrams) "每100g" else "每份") + if (isMine) " · 已保存" else "",
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = c.muted,
        )
        Text(
            food.kcal.grouped(),
            style = NumberStyle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.intake,
        )
    }
}

/**
 * 选中一条食物之后弹出来的详情面板。
 *
 * 它**叠在**添加餐食面板上,后者被糊掉、压暗退到后面 —— 所以这里不需要
 * 「返回列表」:点右上角的叉就回到上一级,和层级关系正好对得上。
 */
@Composable
fun BoxScope.PickedSheet(
    food: Food,
    showProtein: Boolean,
    curDate: String,
    onDismiss: () -> Unit,
    onCommit: (kcal: Int, protein: Int, amount: Double) -> Unit,
) {
    val c = LocalColors.current
    val isGram = food.isPerHundredGrams
    var amount by remember(food) { mutableStateOf(if (isGram) 100.0 else 1.0) }
    var text by remember(food) { mutableStateOf(if (isGram) "100" else "1") }

    val kcal = Nutrition.scale(food.kcal.toDouble(), amount, isGram)
    val protein = Nutrition.scale(food.protein, amount, isGram)

    BottomSheet(food.name, onDismiss) {
        Column {
            QuickAmounts(
                presets = if (isGram) GRAM_PRESETS else PORTION_PRESETS,
                selected = amount,
                unitLabel = if (isGram) "g" else "份",
                onPick = {
                    // 值和输入框一起更新。高亮是从 amount 当场算的,没有第二份状态
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
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

            SolidButton(
                "加入" + if (curDate == Dates.today()) "今天" else Dates.short(curDate),
                onClick = { onCommit(kcal, protein, amount) },
                background = c.intake,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 快速录入。自己填名字和热量,按整份或者按每 100g × 克数 */
@Composable
private fun QuickTab(showProtein: Boolean, f: QuickForm) {
    Field("吃了什么", f.name, { f.name = it }, placeholder = "食堂午餐")
    Segmented(
        options = listOf("整份", "每100g"),
        selectedIndex = if (f.perHundred) 1 else 0,
        onSelect = { f.perHundred = it == 1 },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(
            if (f.perHundred) "每 100g 热量" else "热量",
            f.kcalText, { f.kcalText = it },
            modifier = Modifier.weight(1f),
            numeric = true,
            placeholder = "0",
        )
        if (showProtein) {
            Field(
                if (f.perHundred) "每 100g 蛋白质（g）" else "蛋白质（g）",
                f.proteinText, { f.proteinText = it },
                modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
            )
        }
    }
    // 按 100g 录的必须知道吃了多少克,否则算不出这一餐的热量。之前没有这个框,
    // 直接把「每 100g 的数」当成一份加进去了 —— 吃 200g 就少算一半。
    if (f.perHundred) {
        Field("吃了多少（g）", f.gramsText, { f.gramsText = it }, decimal = true, placeholder = "200")
    }
    // 「存入我的食物」和错误提示都在面板底部的固定区里,见 AddFoodBody
}

/** 营养标签。照着包装上的每份数值填,再乘份数 */
@Composable
private fun LabelTab(showProtein: Boolean, f: LabelForm) {
    val c = LocalColors.current
    val n = f.servings.trim().toDoubleOrNull() ?: 0.0
    val total = ((f.perServing.trim().toDoubleOrNull() ?: 0.0) * n).toInt()

    Field("产品名称", f.name, { f.name = it }, placeholder = "希腊酸奶 草莓味")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(
            "每份热量", f.perServing, { f.perServing = it },
            modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
        )
        if (showProtein) {
            Field(
                "每份蛋白质（g）", f.proteinText, { f.proteinText = it },
                modifier = Modifier.weight(1f), numeric = true, placeholder = "0",
            )
        }
    }
    Field("吃了几份", f.servings, { f.servings = it }, decimal = true)

    Card(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
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
    // 「存入我的食物」和错误提示都在面板底部的固定区里,见 AddFoodBody
}

/**
 * 提交「快速录入」。
 *
 * 校验先跑完,拿到 Valid 之后才构造要写进去的东西 —— 校验函数是纯的,
 * 想在校验前改数据都做不到(错误档案第 10 条)。
 */
private fun submitQuick(
    f: QuickForm,
    onCommit: (Food, kcal: Int, protein: Int, amount: Double?, save: Boolean) -> Unit,
) {
    when (val v = Validate.food(f.name, f.kcalText, f.proteinText)) {
        is Checked.Invalid -> f.error = v.message
        is Checked.Valid -> {
            val (n, kcal, protein) = v.value
            if (f.perHundred) {
                val grams = f.gramsText.trim().replace(',', '.').toDoubleOrNull()
                if (grams == null || grams <= 0) {
                    f.error = "吃了多少克请填一个正数。"
                    return
                }
                f.error = null
                // 存进「我的食物」的是每 100g 的数值,加进记录的是按克数换算过的
                val food = Food(name = n, kcal = kcal, protein = protein.toDouble(), unit = "g")
                onCommit(
                    food,
                    Nutrition.scale(kcal.toDouble(), grams, true),
                    Nutrition.scale(protein.toDouble(), grams, true),
                    grams,
                    f.save,
                )
            } else {
                f.error = null
                val food = Food(name = n, kcal = kcal, protein = protein.toDouble(), unit = "x")
                onCommit(food, kcal, protein, 1.0, f.save)
            }
        }
    }
}

/** 提交「营养标签」 */
private fun submitLabel(
    f: LabelForm,
    onCommit: (Food, kcal: Int, protein: Int, amount: Double?, save: Boolean) -> Unit,
) {
    when (val v = Validate.food(f.name, f.perServing, f.proteinText)) {
        is Checked.Invalid -> f.error = v.message
        is Checked.Valid -> {
            val n = f.servings.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
            if (n <= 0) {
                f.error = "份数请填一个正数。"
                return
            }
            val (nm, perKcal, perProtein) = v.value
            f.error = null
            // 存进「我的食物」的是每份的数值,加进记录的是乘完份数的
            val food = Food(name = nm, kcal = perKcal, protein = perProtein.toDouble(), unit = "x")
            onCommit(food, (perKcal * n).toInt(), (perProtein * n).toInt(), n, f.save)
        }
    }
}
