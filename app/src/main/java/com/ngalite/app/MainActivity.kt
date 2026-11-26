package com.ngalite.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngalite.app.data.AppearanceStore
import com.ngalite.app.data.ShortcutHelper
import com.ngalite.app.ui.NavGraph
import com.ngalite.app.ui.NgaTheme
import com.ngalite.app.ui.shouldUseDarkTheme

class MainActivity : ComponentActivity() {

    /** 从快捷方式启动时携带的 fid，null 表示正常启动 */
    private var pendingFid by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingFid = ShortcutHelper.extractFid(intent)
        setContent {
            val themeMode by AppearanceStore.themeMode.collectAsState()
            val fontScale by AppearanceStore.fontScale.collectAsState()
            val isDark = shouldUseDarkTheme(themeMode)

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            NgaTheme(themeMode = themeMode, fontScale = fontScale) {
                NavGraph(initialFid = pendingFid)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingFid = ShortcutHelper.extractFid(intent)
    }
}
