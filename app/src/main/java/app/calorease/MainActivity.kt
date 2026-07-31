package app.calorease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calorease.ui.theme.CaloreaseTheme
import app.calorease.ui.theme.Dimens
import app.calorease.ui.theme.LocalColors

/**
 * 目前只是把主题立起来,界面在后续提交里逐页搬。
 *
 * 注意这里没有 WebView —— 原生重写不保留网页壳。用户还没录入过数据,
 * 所以也不需要从 localStorage 迁移的那套桥接。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统栏交给 Compose 处理内边距。这里可以放心用 insets ——
        // 错误档案第 5 条说的是网页版里 env(safe-area-inset-*) 会产生
        // 一条填充空白,那是 WebView 的问题,原生层的 WindowInsets 是准的。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CaloreaseTheme {
                Placeholder()
            }
        }
    }
}

@Composable
private fun Placeholder() {
    val c = LocalColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Calorease", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("原生版施工中", color = c.muted, fontSize = 13.sp)
    }
}
