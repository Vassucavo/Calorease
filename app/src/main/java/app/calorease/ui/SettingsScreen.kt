package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/**
 * 设置页:身体数据、选项、我的食物、备份。
 *
 * 备份那一段的文案是刻意写重的 —— 数据只在这台手机上,没有云端兜底,
 * 用户必须清楚这一点。
 */
@Composable
fun SettingsScreen(
    state: Repository.AppState,
    onEditProfile: () -> Unit,
    onEditTarget: () -> Unit,
    onToggleProtein: () -> Unit,
    onToggleGlass: () -> Unit,
    onDeleteMine: (String) -> Unit,
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
            name = "玻璃质感",
            sub = "半透明的面和柔和的光。关掉会省一点电",
            onTap = onToggleGlass,
            action = { Toggle(on = p?.glass ?: true) },
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

        SectionHeader("我的食物", "${state.mine.size} 条")
        if (state.mine.isEmpty()) {
            EmptyHint("自己录入过的食物会存在这里,下次一键复用。")
        } else {
            state.mine.take(40).forEach { f ->
                ItemRow(
                    name = f.name,
                    sub = (if (f.isPerHundredGrams) "每 100g · " else "每份 · ") +
                        (if (f.protein > 0) "${f.protein.f1()}g 蛋白质" else "点击可删除"),
                    trailing = f.kcal.grouped(),
                    trailingColor = c.intake,
                    onDelete = { f.id?.let(onDeleteMine) },
                )
            }
            if (state.mine.size > 40) {
                Note("只显示最近 40 条,共 ${state.mine.size} 条。")
            }
        }

        SectionHeader("备份")
        Note(
            "所有数据只存在这台手机上,不会上传到任何地方。正因如此,卸载应用或清除数据就等于全部丢失 —— " +
                "定期导出一份留着。正常的版本更新不会影响数据。"
        )
        GhostButton("导出数据", onClick = onExport, modifier = Modifier.padding(top = 10.dp))
        GhostButton("从备份恢复", onClick = onImport, modifier = Modifier.padding(top = 8.dp))
        Note("恢复时可以选「覆盖」或「合并」。合并保留两边记录,冲突时以备份为准。")

        SectionHeader("关于")
        Note(
            "食物表、Mifflin-St Jeor 公式、7700 这个常数、手表的活动卡路里 —— 全都是估算值。" +
                "这个应用的价值不在于任何单个数字有多准,而在于连续记录几周后,校准能把系统性误差抵消掉。" +
                "看趋势,不要看单日。\n\n" +
                "要针对个人健康状况设定目标,应当咨询医生或注册营养师。"
        )
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
fun RestoreSheet(
    dayCount: Int,
    weightCount: Int,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
) {
    BottomSheet("恢复备份", onDismiss) {
        Column {
            Note("备份里有 $dayCount 天的记录、$weightCount 条体重。")
            Note("覆盖:清空当前数据,完全用备份替换。")
            Note("合并:保留现有记录,补进备份里有而本机没有的,重复的以备份为准。")
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
