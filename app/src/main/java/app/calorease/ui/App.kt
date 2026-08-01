package app.calorease.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Backup
import app.calorease.data.BackupFile
import app.calorease.data.Repository
import app.calorease.data.Store
import app.calorease.logic.Dates
import app.calorease.ui.theme.Dimens
import app.calorease.ui.theme.LocalColors
import app.calorease.ui.theme.pageBackground

/**
 * 五个标签页。每个带一个图标、一个底栏文字,以及页面顶部的「眉标 / 大标题」。
 * 这些文字和网页版 render() 里那张 T 表一一对应。
 */
/** 固定按钮那一段的淡出高度。按钮高约 44,加上上下留白差不多是这个数 */
private val PinnedFade = 72.dp

enum class Tab(val label: String, val icon: VectorIcon, val eyebrow: String, val title: String) {
    Today("今天", Icons.Today, "Calorease", ""),           // 标题是当天日期,运行时填
    Weight("体重", Icons.Weight, "体重", "趋势"),
    Tune("校准", Icons.Tune, "校准", "你身体给出的真实数字"),
    Logs("记录", Icons.Logs, "记录", "历史"),
    Settings("设置", Icons.Settings, "设置", "偏好与备份"),
}

/** 当前打开的是哪个浮层。同时只会有一个 */
private sealed interface Sheet {
    data object AddFood : Sheet
    data object WatchActive : Sheet
    data object AddBurn : Sheet
    data class EditBurn(val id: String) : Sheet
    data class EditFood(val id: String) : Sheet
    data object AddWeight : Sheet
    data class EditWeight(val date: String) : Sheet
    data object Target : Sheet
    data object EditProfile : Sheet
    data class Restore(val backup: BackupFile) : Sheet
    data object MineList : Sheet
}

/**
 * 返回键。
 *
 * 错误档案第 8 条那个「异步查询 + 失败即退出」的坑,在原生层根本不存在 ——
 * BackHandler 是同步的,enabled 由当前 Compose 状态直接决定,中间没有
 * 可以悄悄失败的环节,也没有跨语言的桥。当初那套 setCanGoBack 推状态的
 * 方案是为了绕开 WebView 的限制,现在可以整个丢掉。
 *
 * 层级不变:先关浮层(Dialog 自己吃掉),再回今日页,再交给系统退出。
 */
@Composable
fun App(
    state: Repository.AppState,
    repo: Repository,
    store: Store,
) {
    val c = LocalColors.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Today) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }

    // ---------- 备份走系统文件选择器,不需要任何存储权限 ----------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Backup.export(store).toByteArray())
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val parsed = text?.let { Backup.parse(it) }
            // 认不出来就什么都不做 —— 宁可不恢复,也不能把一堆空数据写进去
            if (parsed != null) sheet = Sheet.Restore(parsed)
        }
    }

    val overlayOpen = sheet != null || state.isFirstRun

    Box(modifier = Modifier.fillMaxSize().pageBackground(c)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 浮层打开时把页面整体模糊 —— 这是磨砂玻璃真正的来源。
            // 网页版靠 backdrop-filter,原生这边只能反过来做:把背后的内容
            // 自己糊掉,再让半透明的浮层压在上面。
            // 半径要够大,不然透出来的是「能认出字的模糊」而不是柔焦色块。
            // Android 12 以下这行是空操作,那些机器上退化成半透明纯色。
            .blur(if (overlayOpen) PageBlur else 0.dp),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 今天和体重两页底部各有一个固定按钮。内容要多留出它的高度,
            // 否则最后一条会被压在按钮底下 —— 摄入的最后一条、全部记录的最后一条
            // 都是这么丢的。
            val pinned = tab == Tab.Today || tab == Tab.Weight
            val scroll = rememberScrollState()
            val scrolled by remember { derivedStateOf { scroll.value > 0 } }
            // 顶栏固定之后,内容要从它底下淡出去,不然是齐刷刷地被切断。
            // 没滚的时候不淡 —— 停在最上面时第一行不该是灰的。
            val topFade by animateDpAsState(if (scrolled) 18.dp else 0.dp, label = "topFade")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                // 顶栏吸顶:它在滚动区外面,页面怎么滚它都不动
                PageHeader(
                    eyebrow = tab.eyebrow,
                    title = if (tab == Tab.Today) Dates.full(state.curDate) else tab.title,
                    modifier = Modifier.padding(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = Dimens.screenPadding,
                    ),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 上下两头都淡出。用的是 alpha 遮罩而不是一条渐变色带 ——
                        // 页面底色是四层渐变叠出来的,没有哪个固定色值能和它对上,
                        // 拿色带盖必然穿帮。淡出露出来的就是页面本身,永远同色。
                        .fadeEdges(
                            top = topFade,
                            bottom = if (pinned) PinnedFade else 0.dp,
                        )
                        .verticalScroll(scroll)
                        .padding(
                            start = Dimens.screenPadding,
                            end = Dimens.screenPadding,
                            bottom = if (pinned) 84.dp else 24.dp,
                        ),
                ) {
                    Column {
                        when (tab) {
                            Tab.Today -> TodayScreen(
                                state = state,
                                onStepDay = { repo.stepDay(it) },
                                onJumpToday = { repo.jumpToToday() },
                                onEditWatchActive = { sheet = Sheet.WatchActive },
                                onAddBurn = { sheet = Sheet.AddBurn },
                                onEditBurn = { sheet = Sheet.EditBurn(it) },
                                onDeleteBurn = { repo.deleteBurn(it) },
                                onEditFood = { sheet = Sheet.EditFood(it) },
                                onDeleteFood = { repo.deleteFood(it) },
                            )

                            Tab.Weight -> WeightScreen(
                                state = state,
                                onEdit = { sheet = Sheet.EditWeight(it) },
                                onDelete = { repo.deleteWeight(it) },
                            )

                            Tab.Tune -> TuneScreen(
                                state = state,
                                onApplyTarget = { value ->
                                    repo.setTarget(value, netMode = false)
                                    tab = Tab.Today
                                },
                            )

                            Tab.Logs -> LogsScreen(
                                state = state,
                                onOpenDay = { key ->
                                    repo.openDate(key)
                                    tab = Tab.Today
                                },
                            )

                            Tab.Settings -> SettingsScreen(
                                state = state,
                                onEditProfile = { sheet = Sheet.EditProfile },
                                onEditTarget = { sheet = Sheet.Target },
                                onToggleProtein = { repo.toggleProtein() },
                                onToggleActiveIncludes = { repo.toggleActiveIncludesWorkouts() },
                                onOpenMine = { sheet = Sheet.MineList },
                                onExport = { exportLauncher.launch(Backup.fileName(Dates.today())) },
                                onImport = { importLauncher.launch(arrayOf("*/*")) },
                            )
                        }
                    }
                }
            }

            // 整宽的按钮,贴在标签栏上方 —— 网页版的 .fab 就是这个样子,
            // 不是右下角的小药丸。
            val pinnedModifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = 14.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = Color(0x3818241E),
                )
            when (tab) {
                Tab.Today -> InkButton(
                    "添加餐食",
                    onClick = { sheet = Sheet.AddFood },
                    modifier = pinnedModifier,
                )
                Tab.Weight -> SolidButton(
                    "记录体重",
                    onClick = { sheet = Sheet.AddWeight },
                    modifier = pinnedModifier,
                )
                else -> Unit
            }
        }

        TabBar(current = tab, onSelect = { tab = it })
    }

        RenderSheet(
            sheet = sheet,
            state = state,
            repo = repo,
            store = store,
            firstRun = state.isFirstRun,
            onClose = { sheet = null },
        )
    }
}

@Composable
private fun BoxScope.RenderSheet(
    sheet: Sheet?,
    state: Repository.AppState,
    repo: Repository,
    store: Store,
    firstRun: Boolean,
    onClose: () -> Unit,
) {
    // 身体数据没填全就先把这个表单顶上来 —— 没有它算不出基础代谢,整个应用没法用。
    // 这个表单关不掉,填完保存才会消失。
    if (firstRun) {
        ProfileSheet(
            profile = state.profile,
            firstRun = true,
            onDismiss = { },
            onSave = { repo.saveProfile(it) },
        )
        return
    }

    when (sheet) {
        null -> Unit

        Sheet.AddFood -> AddFoodSheet(
            profile = state.profile,
            mine = state.mine,
            curDate = state.curDate,
            onDismiss = onClose,
            onAdd = { name, kcal, protein, amount, unit ->
                repo.addFood(name, kcal, protein, amount, unit)
            },
            onRemember = { repo.rememberFood(it) },
            onTouch = { repo.touchFood(it) },
        )

        Sheet.WatchActive -> WatchActiveSheet(
            current = state.day.watchActive,
            onDismiss = onClose,
            onSave = { repo.setWatchActive(it) },
        )

        Sheet.AddBurn -> BurnSheet(
            onDismiss = onClose,
            onSave = { label, kcal -> repo.addBurn(label, kcal) },
        )

        is Sheet.EditBurn -> {
            val b = state.day.burn.firstOrNull { it.id == sheet.id }
            if (b == null) onClose() else BurnSheet(
                initialLabel = b.label,
                initialKcal = b.kcal,
                onDismiss = onClose,
                onSave = { label, kcal -> repo.updateBurn(b.id, label, kcal) },
            )
        }

        is Sheet.EditFood -> {
            val f = state.day.food.firstOrNull { it.id == sheet.id }
            if (f == null) onClose() else EditFoodSheet(
                entry = f,
                showProtein = state.profile?.showProtein == true,
                onDismiss = onClose,
                onSave = { name, kcal, protein, amount ->
                    repo.updateFood(f.id, name, kcal, protein, amount)
                },
            )
        }

        Sheet.MineList -> MineSheet(
            mine = state.mine,
            showProtein = state.profile?.showProtein == true,
            onDismiss = onClose,
            onSave = { repo.updateMine(it) },
            onDelete = { repo.deleteMine(it) },
        )

        Sheet.AddWeight -> WeightSheet(
            existing = null,
            onDismiss = onClose,
            onSave = { entry, replace -> repo.saveWeight(entry, replace) },
        )

        is Sheet.EditWeight -> {
            val w = state.weights.firstOrNull { it.date == sheet.date }
            if (w == null) onClose() else WeightSheet(
                existing = w,
                onDismiss = onClose,
                onSave = { entry, replace -> repo.saveWeight(entry, replace) },
            )
        }

        Sheet.Target -> TargetSheet(
            profile = state.profile,
            onDismiss = onClose,
            onSave = { value, net -> repo.setTarget(value, net) },
        )

        Sheet.EditProfile -> ProfileSheet(
            profile = state.profile,
            firstRun = false,
            onDismiss = onClose,
            onSave = { repo.saveProfile(it) },
        )

        is Sheet.Restore -> RestoreSheet(
            dayCount = sheet.backup.days.size,
            weightCount = sheet.backup.weights.size,
            onDismiss = onClose,
            onReplace = {
                Backup.apply(store, sheet.backup, Backup.Mode.Replace)
                repo.load()
                onClose()
            },
            onMerge = {
                Backup.apply(store, sheet.backup, Backup.Mode.Merge)
                repo.load()
                onClose()
            },
        )
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.panelBg),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.panelBorder))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                val tint = if (on) c.burn else c.muted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(t) }
                        .padding(top = 8.dp, bottom = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    StrokeIcon(t.icon, color = tint, size = 21.dp)
                    Text(t.label, fontSize = 10.sp, lineHeight = 10.sp, color = tint)
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}
