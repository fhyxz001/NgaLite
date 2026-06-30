package com.ngalite.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.UpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showLogin by remember { mutableStateOf(false) }
    // 登录态展示：进入页面与登录对话框关闭后刷新
    var loggedAccount by remember { mutableStateOf(CookieStore.getAccountName()) }
    var logged by remember { mutableStateOf(CookieStore.isLogin()) }

    /** 退出登录：清空 Cookie 与账号信息 */
    fun logout() {
        CookieStore.clear()
        logged = false
        loggedAccount = ""
    }

    // ---- 检查更新相关状态 ----
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateManager.UpdateResult?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** 获取当前应用版本名 */
    fun currentVersionName(): String = try {
        val pm = context.packageManager
        pm.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    /** 触发检查更新 */
    fun triggerCheckUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = UpdateManager.checkUpdate(currentVersionName())
            updateResult = result
            isCheckingUpdate = false
        }
    }

    /** 确认更新后开始下载 */
    fun startDownload(url: String) {
        updateResult = null
        isDownloading = true
        downloadProgress = 0
        downloadError = null
        scope.launch {
            try {
                val file = UpdateManager.downloadApk(context, url) { progress ->
                    downloadProgress = progress
                }
                isDownloading = false
                UpdateManager.installApk(context, file)
            } catch (e: Exception) {
                isDownloading = false
                downloadError = e.message ?: "下载失败，挂梯子试试"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 检查更新
            Card(
                onClick = { triggerCheckUpdate() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val updateBitmap = remember { BitmapFactory.decodeStream(context.assets.open("update.png")) }
                    Image(
                        bitmap = updateBitmap.asImageBitmap(),
                        contentDescription = "检查更新",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "检查更新",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "当前版本：v${currentVersionName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "v${currentVersionName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 登录 NGA 账号
            Card(
                onClick = { showLogin = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cookieBitmap = remember { BitmapFactory.decodeStream(context.assets.open("cookie.png")) }
                    Image(
                        bitmap = cookieBitmap.asImageBitmap(),
                        contentDescription = "登录 NGA 账号",
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录 NGA 账号",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (logged) {
                                val name = loggedAccount
                                if (name.isNotBlank()) "已登录：$name" else "已登录"
                            } else {
                                "账号密码登录，或粘贴 Cookie 兜底"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (logged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    if (logged) {
                        TextButton(onClick = { logout() }) {
                            Text("退出登录", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    if (showLogin) {
        LoginDialog(onDismiss = {
            showLogin = false
            // 登录对话框关闭后刷新登录态展示
            logged = CookieStore.isLogin()
            loggedAccount = CookieStore.getAccountName()
        })
    }

    // ---- 检查更新结果对话框 ----
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text("检查更新", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.hasUpdate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (result.hasUpdate) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前版本：v${currentVersionName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "最新版本：v${result.latestVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                if (result.hasUpdate && result.downloadUrl != null) {
                    TextButton(onClick = {
                        val url = result.downloadUrl!!
                        startDownload(url)
                    }) { Text("立即更新", fontWeight = FontWeight.SemiBold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) { Text("关闭") }
            }
        )
    }

    // ---- 下载进度对话框 ----
    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在下载更新", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$downloadProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // ---- 下载失败提示对话框 ----
    downloadError?.let { error ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            title = { Text("下载失败", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) { Text("知道了") }
            }
        )
    }
}
