package com.ngalite.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngalite.app.data.ShortcutHelper
import com.ngalite.app.ui.NavGraph
import com.ngalite.app.ui.NgaTheme

class MainActivity : ComponentActivity() {

    /** 从快捷方式启动时携带的 fid，null 表示正常启动 */
    private var pendingFid by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingFid = ShortcutHelper.extractFid(intent)
        setContent {
            NgaTheme {
                NavGraph(initialFid = pendingFid)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 快捷方式启动时若 Activity 已存在，通过新 Intent 更新 pendingFid，
        // Compose 会感知状态变化并重新触发 NavGraph 中的导航逻辑
        setIntent(intent)
        pendingFid = ShortcutHelper.extractFid(intent)
    }
}
