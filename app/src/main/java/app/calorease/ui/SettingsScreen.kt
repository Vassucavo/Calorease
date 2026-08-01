package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Repository
import app.calorease.logic.Nutrition
import app.calorease.ui.theme.LocalColors
import kotlin.math.abs

/** 设置页:身体数据、选项、我的食物、备份 */
@Composable
fun SettingsScreen(
    state: Repository.AppState,
    onEditProfile: () -> Unit,
    onEditTarget: () -> Unit,
    onToggleProtein: () -> Unit,
    onToggleActiveIncludes: () -> Unit,
    onOpenMine: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val c = LocalColors.current
    val p = state.profile

    Column {
        SectionHeader("身体数据")
        ItemRow(
            name = "基础代谢",
            sub = if (p != null && p.isComplete) {
                "${if (p.isFemale) "女" else "男"} · ${p.age}岁 · ${p.heightCm}cm · ${p.weightKg}kg"
            } else "还没填",
            trailing = Nutrition.bmr(p).grouped(),
            trailingColor = c.burn,
        )
        GhostButton("修改身体数据", onClick = onEditProfile, modifier = Modifier.padding(top = 6.dp))

        SectionHeader("选项")
        ItemRow(
            name = "记录蛋白质",
            sub = "每样食物多一个可选字段",
            onTap = onToggleProtein,
            action = { Toggle(on = p?.showProtein == true) },
        )
        ItemRow(
            name = "活动消耗已包含运动",
            onTap = onToggleActiveIncludes,
            action = { Toggle(on = p?.activeIncludesWorkouts == true) },
        )
        ItemRow(
            name = "每日热量目标",
            sub = when {
                p == null || p.target == 0 -> "未设置 —— 校准页可以帮你算"
                p.isNetMode -> "每天结余 ${if (p.target >= 0) "+" else "−"}${abs(p.target).grouped()}"
                else -> "每天摄入 ${p.target.grouped()}"
            },
            onTap = onEditTarget,
            action = {
                Text("修改", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.burn)
            },
        )

        // 点右边那个箭头开一个面板,不在页面上就地展开 —— 录得多了以后
        // 这一段能有几十条,摊开会把「备份」挤到很下面。
        // 箭头朝右:它表示「进到另一块地方去」,不是「往下展开」。
        SectionHeader("我的食物")
        ItemRow(
            name = "已保存的食物",
            onTap = onOpenMine,
            action = {
                Text("${state.mine.size} 条", fontSize = 13.sp, color = c.muted)
                StrokeIcon(Icons.ChevronRight, color = c.muted, size = 18.dp)
            },
        )

        SectionHeader("备份")
        GhostButton("导出数据", onClick = onExport, modifier = Modifier.padding(top = 4.dp))
        GhostButton("从备份恢复", onClick = onImport, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * 开关。尺寸照网页版的 `.toggle`:48×28、圆角 14,滑块 22 直径、边距 3,
 * 关闭时轨道是分隔线色,打开时是墨绿。滑块移动带 .18s 的缓动。
 * 只显示状态,点击由整行接管。
 */
@Composable
private fun Toggle(on: Boolean) {
    val c = LocalColors.current
    val knobOffset by animateDpAsState(if (on) 23.dp else 3.dp, label = "toggle")
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) c.burn else c.line),
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset, top = 3.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
        )
    }
}

/** 恢复备份时选覆盖还是合并 */
@Composable
fun BoxScope.RestoreSheet(
    dayCount: Int,
    weightCount: Int,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
) {
    BottomSheet("恢复备份", onDismiss) {
        Column {
            Note("备份里有 $dayCount 天的记录、$weightCount 条体重。")
            Note("覆盖：清空当前数据，完全用备份替换。")
            Note("合并：保留现有记录，补进备份里有而本机没有的，重复的以备份为准。")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                SolidButton("合并", onClick = onMerge, modifier = Modifier.weight(1f))
                // .btn.danger 是纸色底 + 警示色的字和边框,不是实心红
                DangerButton("覆盖", onClick = onReplace, modifier = Modifier.weight(1f))
            }
        }
    }
}
