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

    BackHandler(enabled = tab != Tab.Today) { tab = Tab.Today }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Dimens.screenPadding,
                    end = Dimens.screenPadding,
                    top = Dimens.screenPadding,
                    bottom = 24.dp,
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

                Tab.Weight -> ComingSoon("体重", "趋势线和体脂记录还在搬")
                Tab.Tune -> ComingSoon("校准", "三道守卫的逻辑已经写好并测过,界面还在搬")
                Tab.Logs -> ComingSoon("记录", "历史列表还在搬")
                Tab.Settings -> ComingSoon("设置", "身体数据、目标、备份还在搬")
            }
        }

        TabBar(current = tab, onSelect = { tab = it })
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
