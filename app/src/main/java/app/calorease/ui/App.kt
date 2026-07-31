package app.calorease.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Repository
import app.calorease.ui.theme.Dimens
import app.calorease.ui.theme.LocalColors

enum class Tab(val label: String) {
    Today("今天"),
    Weight("体重"),
    Tune("校准"),
    Logs("记录"),
    Settings("设置"),
}

/**
 * 返回键。
 *
 * 错误档案第 8 条那个「异步查询 + 失败即退出」的坑,在原生层根本不存在 ——
 * BackHandler 是同步的,enabled 由当前 Compose 状态直接决定,中间没有
 * 可以悄悄失败的环节,也没有跨语言的桥。当初那套 setCanGoBack 推状态的
 * 方案是为了绕开 WebView 的限制,现在可以整个丢掉。
 *
 * 层级不变:先关浮层,再回今日页,再交给系统退出。
 */
@Composable
fun App(
    state: Repository.AppState,
    repo: Repository,
) {
    val c = LocalColors.current
    var tab by remember { mutableStateOf(Tab.Today) }
    var showAdd by remember { mutableStateOf(false) }

    // 浮层自己吃掉返回键(Dialog 会处理),这里只管标签页那一级
    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }

    if (showAdd) {
        AddFoodSheet(
            profile = state.profile,
            mine = state.mine,
            curDate = state.curDate,
            onDismiss = { showAdd = false },
            onAdd = { name, kcal, protein -> repo.addFood(name, kcal, protein) },
            onRemember = { repo.rememberFood(it) },
            onTouch = { repo.touchFood(it) },
        )
    }

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
                        onEditWatchActive = { /* 面板下一步接上 */ },
                        onAddBurn = { },
                        onEditBurn = { },
                        onDeleteBurn = { repo.deleteBurn(it) },
                        onEditFood = { },
                        onDeleteFood = { repo.deleteFood(it) },
                    )

                    Tab.Weight -> WeightScreen(
                        state = state,
                        onAdd = { /* 表单面板下一步接上 */ },
                        onEdit = { },
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

                    Tab.Settings -> ComingSoon("设置", "身体数据、目标、备份还在搬")
                }
            }

            if (tab == Tab.Today) {
                AddFab(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Dimens.screenPadding, bottom = 20.dp),
                )
            }
        }

        TabBar(current = tab, onSelect = { tab = it })
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

@Composable
private fun ComingSoon(title: String, detail: String) {
    val c = LocalColors.current
    Column {
        SectionHeader(title)
        EmptyHint(detail)
    }
}
