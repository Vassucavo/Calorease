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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class Tab(val label: String) {
    Today("今天"),
    Weight("体重"),
    Tune("校准"),
    Logs("记录"),
    Settings("设置"),
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

    RenderSheet(
        sheet = sheet,
        state = state,
        repo = repo,
        store = store,
        firstRun = state.isFirstRun,
        onClose = { sheet = null },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Dimens.screenPadding,
                        end = Dimens.screenPadding,
                        top = Dimens.screenPadding,
                        // 今日页右下角有添加餐食的按钮,底部多留一截别让它盖住最后一条
                        bottom = if (tab == Tab.Today) 88.dp else 24.dp,
                    ),
            ) {
                when (tab) {
                    Tab.Today -> TodayScreen(
                        state = state,
                        onStepDay = { repo.stepDay(it) },
                        onEditWatchActive = { sheet = Sheet.WatchActive },
                        onAddBurn = { sheet = Sheet.AddBurn },
                        onEditBurn = { sheet = Sheet.EditBurn(it) },
                        onDeleteBurn = { repo.deleteBurn(it) },
                        onEditFood = { sheet = Sheet.EditFood(it) },
                        onDeleteFood = { repo.deleteFood(it) },
                    )

                    Tab.Weight -> WeightScreen(
                        state = state,
                        onAdd = { sheet = Sheet.AddWeight },
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
                        onDeleteMine = { repo.deleteMine(it) },
                        onExport = { exportLauncher.launch(Backup.fileName(Dates.today())) },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }

            if (tab == Tab.Today) {
                AddFab(
                    onClick = { sheet = Sheet.AddFood },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Dimens.screenPadding, bottom = 20.dp),
                )
            }
        }

        TabBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
private fun RenderSheet(
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
            onAdd = { name, kcal, protein -> repo.addFood(name, kcal, protein) },
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
                initialName = f.name,
                initialKcal = f.kcal,
                initialProtein = f.protein,
                showProtein = state.profile?.showProtein == true,
                onDismiss = onClose,
                onSave = { name, kcal, protein -> repo.updateFood(f.id, name, kcal, protein) },
            )
        }

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

/** 添加餐食。只在今日页出现 —— 其他页面加餐食没有意义 */
@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.intake)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
    ) {
        Text(
            "添加餐食",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (c.isDark) Color(0xFF1A1206) else Color.White,
        )
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.sheet),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.tabBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.tabBarHeight)
                        .clickable { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) c.burn else c.muted,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}
