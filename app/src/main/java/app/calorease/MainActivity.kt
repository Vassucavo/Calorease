package app.calorease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.calorease.data.Repository
import app.calorease.data.Store
import app.calorease.ui.App
import app.calorease.ui.theme.CaloreaseTheme

/**
 * 整个应用只有这一个 Activity。
 *
 * 没有 WebView —— 原生重写不保留网页壳。也没有任何权限:数据只在
 * filesDir 里,导出/恢复走系统文件选择器,那两个 Intent 不需要权限。
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        store = Store(filesDir)
        repo = Repository(store)
        repo.load()

        setContent {
            CaloreaseTheme {
                val r = remember { repo }
                val s = remember { store }
                val state by r.state.collectAsState()
                App(state = state, repo = r, store = s)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 应用在后台放过了午夜的话,回来要看到新的今天
        repo.refreshDateIfStale()
    }
}
