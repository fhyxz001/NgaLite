package com.ngalite.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ngalite.app.data.ShortcutHelper
import com.ngalite.app.ui.NavGraph
import com.ngalite.app.ui.NgaTheme

class MainActivity : ComponentActivity() {

    /** 从快捷方式启动时携带的 fid，null 表示正常启动 */
    private var pendingFid: String? = null

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
        // 快捷方式启动时若 Activity 已存在，通过新 Intent 传递 fid
        setIntent(intent)
    }
}
