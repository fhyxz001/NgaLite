package com.ngalite.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.UpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onLoginClick: () -> Unit = {},
) {
    val context = LocalContext.current
    var showLogin by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf(CookieStore.get()) }
    var logged by remember { mutableStateOf(CookieStore.isLogin()) }
    var loggedAccount by remember { mutableStateOf(CookieStore.getAccountName()) }

    fun refreshLoginState() {
        logged = CookieStore.isLogin()
        loggedAccount = CookieStore.getAccountName()
    }

    /** 退出登录：清空 Cookie 与账号信息 */
    fun logout() {
        CookieStore.clear()
        refreshLoginState()
    }

    // ---- 检查更新相关状态 ----
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateManager.UpdateResult?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** 获取当前应用版本名（缓存结果避免每次重组都查询 PackageManager） */
    val currentVersionName = remember {
        try {
            val pm = context.packageManager
            pm.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /** 触发检查更新 */
    fun triggerCheckUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = UpdateManager.checkUpdate(currentVersionName)
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
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ==========================================
            // 账号管理
            // ==========================================
            SectionTitle(text = "账号管理")

            Spacer(Modifier.height(8.dp))

            if (logged) {
                // ---- 已登录 ----
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "NGA 账号",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                loggedAccount.ifBlank { "已登录" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        OutlinedButton(
                            onClick = { logout() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("退出", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                // ---- 未登录 ----
                SettingsCard {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Login,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "登录 NGA 账号",
                        subtitle = "账号密码登录，自动处理验证码",
                        onClick = { showLogin = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.ContentPaste,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "粘贴 Cookie 登录",
                        subtitle = "从浏览器复制 Cookie 字符串粘贴",
                        onClick = { showCookieDialog = true }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ==========================================
            // 通用
            // ==========================================
            SectionTitle(text = "通用")

            Spacer(Modifier.height(8.dp))

            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Update,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "检查更新",
                    subtitle = "当前版本 v${currentVersionName}",
                    onClick = { triggerCheckUpdate() },
                    trailing = {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ==========================================
            // 关于
            // ==========================================
            SectionTitle(text = "关于")

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    coil.compose.AsyncImage(
                        model = "file:///android_asset/logo.jpg",
                        contentDescription = "NgaLite",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "NgaLite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "v${currentVersionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "轻量级 NGA 论坛客户端",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 登录对话框 ----
    if (showLogin) {
        LoginDialog(onDismiss = {
            showLogin = false
            refreshLoginState()
        })
    }

    // ---- 粘贴 Cookie 对话框 ----
    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text("粘贴 Cookie", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "从浏览器复制 Cookie 字符串粘贴到下方：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("Cookie") },
                        singleLine = false,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CookieStore.save(cookieInput)
                    refreshLoginState()
                    showCookieDialog = false
                }) { Text("保存", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) { Text("取消") }
            }
        )
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
                            "当前版本：v${currentVersionName}",
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
                        textAlign = TextAlign.Center
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

// ==============================================================================
// 子组件
// ==============================================================================

/** 分组标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** 分组的卡片容器 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        content()
    }
}

/** 设置行：图标 + 标题/副标题 + 可选的尾部内容 + 右箭头 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (trailing != null) {
            trailing()
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 设置行之间的分隔线 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 66.dp)
    )
}
