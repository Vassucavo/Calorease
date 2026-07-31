package app.calorease.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.data.Repository
import app.calorease.logic.Dates
import app.calorease.logic.Nutrition
import app.calorease.ui.theme.LocalColors
import kotlin.math.abs
import kotlin.math.max

/**
 * 今日页。布局和网页版一一对应:
 *   日期条 → 主卡片(还能吃 / 结余 + 摄入消耗对比条) → 消耗明细 → 摄入明细
 */
@Composable
fun TodayScreen(
    state: Repository.AppState,
    onStepDay: (Long) -> Unit,
    onEditWatchActive: () -> Unit,
    onAddBurn: () -> Unit,
    onEditBurn: (String) -> Unit,
    onDeleteBurn: (String) -> Unit,
    onEditFood: (String) -> Unit,
    onDeleteFood: (String) -> Unit,
) {
    val c = LocalColors.current
    val day = state.day
    val profile = state.profile

    val eaten = day.eaten
    val burned = Nutrition.burned(profile, day)
    val net = eaten - burned
    val span = max(max(eaten, burned), 1)
    val target = profile?.target ?: 0
    val allowance = Nutrition.allowance(profile, day)
    val isToday = state.curDate == Dates.today()

    Column {
        DateNav(
            dateKey = state.curDate,
            isToday = isToday,
            onStep = onStepDay,
        )

        Card {
            if (target != 0) {
                val left = allowance - eaten
                Eyebrow(if (left >= 0) "今天还能吃" else "已超出目标")
                BigNumber(
                    abs(left).grouped(),
                    color = if (left >= 0) c.burn else c.warn,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                val detail = if (profile!!.isNetMode) {
                    val sign = if (target >= 0) "+" else "−"
                    "目标结余 $sign${abs(target).grouped()} · 按今天消耗 ${burned.grouped()} 算，" +
                        "可吃 ${allowance.grouped()}，已摄入 ${eaten.grouped()}"
                } else {
                    "目标 ${allowance.grouped()}，已摄入 ${eaten.grouped()}"
                }
                Note(detail, modifier = Modifier.padding(bottom = 16.dp))
            } else {
                Eyebrow(if (net >= 0) "盈余" else "缺口")
                BigNumber(
                    (if (net >= 0) "+" else "−") + abs(net).grouped(),
                    color = if (net >= 0) c.intake else c.burn,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            MeasureBar("摄入", eaten, eaten.toFloat() / span, c.intake)
            Spacer(Modifier.height(11.dp))
            MeasureBar("消耗", burned, burned.toFloat() / span, c.burn)

            if (target != 0) {
                Note("今日结余 " + (if (net >= 0) "+" else "−") + abs(net).grouped())
            }
            if (profile?.showProtein == true) {
                Note("蛋白质 ${day.protein}g")
            }
        }

        // ---------- 消耗 ----------
        SectionHeader("消耗", burned.grouped(), c.burn)

        ItemRow(
            name = "基础代谢",
            sub = "呼吸、思考、消化、细胞修复，全天",
            trailing = Nutrition.bmr(profile).grouped(),
            trailingColor = c.burn,
        )

        ItemRow(
            name = "活动消耗",
            sub = "填“活动 / Move”那个数，不要填总计",
            trailing = if (day.watchActive != 0) day.watchActive.grouped() else "填写",
            trailingColor = if (day.watchActive != 0) c.burn else c.muted,
            onTap = onEditWatchActive,
        )

        // 手表口径里已经含了运动时,这些条目照常显示但不进总数(见 Nutrition.burned)。
        // 不显示成 0、也不隐藏 —— 你还是该看得见那天做了什么。
        val counted = profile?.activeIncludesWorkouts != true
        day.burn.forEach { b ->
            ItemRow(
                name = b.label,
                sub = if (counted) null else "已含在活动消耗里",
                trailing = b.kcal.grouped(),
                trailingColor = if (counted) c.burn else c.muted,
                onTap = { onEditBurn(b.id) },
                onDelete = { onDeleteBurn(b.id) },
            )
        }

        GhostButton("添加运动", onClick = onAddBurn, modifier = Modifier.padding(top = 6.dp))

        // ---------- 摄入 ----------
        SectionHeader("摄入", eaten.grouped(), c.intake)

        if (day.food.isEmpty()) {
            EmptyHint("还没有记录。点下方“添加餐食”开始。")
        } else {
            day.food.forEach { f ->
                // 副行写「吃了多少」。份量是录入时一起存下来的;手动改过的条目
                // 算不出份量,那就只剩蛋白质,两样都没有就不占这一行。
                val bits = listOfNotNull(
                    f.portion,
                    if (profile?.showProtein == true && f.protein > 0) "${f.protein}g 蛋白质" else null,
                )
                ItemRow(
                    name = f.name,
                    sub = bits.joinToString(" · ").takeIf { it.isNotEmpty() },
                    trailing = f.kcal.grouped(),
                    trailingColor = c.intake,
                    onTap = { onEditFood(f.id) },
                    onDelete = { onDeleteFood(f.id) },
                )
            }
        }
    }
}

/** 日期切换条。不能翻到未来 —— 右箭头在今天时是禁用的 */
@Composable
private fun DateNav(dateKey: String, isToday: Boolean, onStep: (Long) -> Unit) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArrowButton(Icons.ChevronLeft, enabled = true) { onStep(-1) }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Dates.full(dateKey),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = c.ink,
            )
            Text(
                if (isToday) "今天" else "点两侧箭头切换日期",
                fontSize = 11.sp,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
        }
        ArrowButton(Icons.ChevronRight, enabled = !isToday) { onStep(1) }
    }
}

/** 36×36 圆角 9 的方形按钮,里面一个 17px 的描边箭头 —— 和网页版的 .datenav button 一致 */
@Composable
private fun ArrowButton(icon: VectorIcon, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.32f)
            .clip(RoundedCornerShape(9.dp))
            .background(c.rowBg)
            .border(1.dp, c.surfaceBorder, RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon, color = c.ink, size = 17.dp, strokeWidth = 2f)
    }
}

