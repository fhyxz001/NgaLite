package com.ngalite.app.ui

import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ngalite.app.data.AppearanceStore
import com.ngalite.app.data.BaseConfig
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.FontScale
import com.ngalite.app.data.ThemeMode
import com.ngalite.app.data.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onLoginClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val themeMode by AppearanceStore.themeMode.collectAsState()
    val fontScale by AppearanceStore.fontScale.collectAsState()

    var showLogin by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf(CookieStore.get()) }
    var logged by remember { mutableStateOf(CookieStore.isLogin()) }
    var loggedAccount by remember { mutableStateOf(CookieStore.getAccountName()) }

    var baseDomain by remember { mutableStateOf(BaseConfig.domain()) }
    var showDomainDialog by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateManager.UpdateResult?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun refreshLoginState() {
        logged = CookieStore.isLogin()
        loggedAccount = CookieStore.getAccountName()
    }

    fun logout() {
        CookieStore.clear()
        refreshLoginState()
    }

    val currentVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    fun triggerCheckUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = UpdateManager.checkUpdate(currentVersionName)
            updateResult = result
            isCheckingUpdate = false
        }
    }

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
                downloadError = e.message ?: "下载失败，请稍后重试"
            }
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page)
    ) {
        // 顶部标题栏
        Surface(color = ForumColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 10.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ForumColors.TextPrimary
                )
            }
        }
        HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 账号 Hero 卡片
            AccountHeroCard(
                logged = logged,
                accountName = loggedAccount,
                onLoginPassword = { showLogin = true },
                onLoginWeb = onLoginClick,
                onPasteCookie = { showCookieDialog = true },
                onLogout = { logout() }
            )

            // 外观与阅读
            SettingsSectionHeader("外观与阅读")
            SettingsGroupCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBadge(icon = Icons.Default.DarkMode, tint = ForumColors.Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "主题模式",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForumColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedSelectorRow(
                        items = ThemeMode.entries,
                        selectedItem = themeMode,
                        labelOf = { it.label },
                        onSelect = { AppearanceStore.setThemeMode(it) }
                    )
                }

                SettingsDivider()

                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIconBadge(icon = Icons.Default.FormatSize, tint = ForumColors.Link)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "阅读字号",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForumColors.TextPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedSelectorRow(
                        items = FontScale.entries,
                        selectedItem = fontScale,
                        labelOf = { it.label },
                        onSelect = { AppearanceStore.setFontScale(it) }
                    )
                }
            }

            // 通用配置
            SettingsSectionHeader("通用设置")
            SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Default.Language,
                    iconTint = ForumColors.Link,
                    title = "服务器域名",
                    subtitle = "https://$baseDomain",
                    onClick = { showDomainDialog = true }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Update,
                    iconTint = ForumColors.Accent,
                    title = "检查更新",
                    subtitle = "当前版本 v$currentVersionName",
                    onClick = { triggerCheckUpdate() },
                    trailing = {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ForumColors.Accent
                            )
                        }
                    }
                )
            }

            // 关于
            SettingsSectionHeader("关于")
            SettingsGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = "file:///android_asset/logo.png",
                        contentDescription = "NgaLite",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NgaLite",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ForumColors.TextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            BadgePill(
                                text = "v$currentVersionName",
                                containerColor = ForumColors.AccentSoft,
                                contentColor = ForumColors.Accent
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "轻量、现代、纯净的第三方 NGA 论坛客户端",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForumColors.Meta
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLogin) {
        LoginDialog(onDismiss = {
            showLogin = false
            refreshLoginState()
        })
    }

    if (showDomainDialog) {
        SingleChoiceDialog(
            title = "选择服务器域名",
            options = BaseConfig.DOMAINS,
            selected = baseDomain,
            labelOf = { "https://$it" },
            onSelect = { domain ->
                if (domain != baseDomain) {
                    baseDomain = domain
                    BaseConfig.setDomain(domain)
                    CookieStore.clear()
                    refreshLoginState()
                }
                showDomainDialog = false
            },
            onDismiss = { showDomainDialog = false }
        )
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("粘贴 Cookie") },
            text = {
                Column {
                    Text(
                        text = "请在下方粘贴从浏览器复制的 NGA Cookie 字符串。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForumColors.Meta
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("Cookie 字符串") },
                        singleLine = false,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
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

    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("检查更新") },
            text = {
                Column {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.hasUpdate) ForumColors.Accent else ForumColors.TextPrimary
                    )
                    if (result.hasUpdate) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "当前版本：v$currentVersionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForumColors.Meta
                        )
                        Text(
                            text = "最新版本：v${result.latestVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForumColors.Meta
                        )
                    }
                }
            },
            confirmButton = {
                if (result.hasUpdate && result.downloadUrl != null) {
                    TextButton(onClick = { startDownload(result.downloadUrl!!) }) {
                        Text("立即更新", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) { Text("关闭") }
            }
        )
    }

    if (isDownloading) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(20.dp),
            title = { Text("正在下载更新") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = ForumColors.Accent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$downloadProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForumColors.Meta,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    downloadError?.let { error ->
        AlertDialog(
            onDismissRequest = { downloadError = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("下载失败") },
            text = {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForumColors.Hot
                )
            },
            confirmButton = {
                TextButton(onClick = { downloadError = null }) { Text("确定") }
            }
        )
    }
}

@Composable
private fun AccountHeroCard(
    logged: Boolean,
    accountName: String,
    onLoginPassword: () -> Unit,
    onLoginWeb: () -> Unit,
    onPasteCookie: () -> Unit,
    onLogout: () -> Unit
) {
    if (logged) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ForumColors.Surface,
            border = BorderStroke(1.dp, ForumColors.Accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ForumColors.Accent, Color(0xFFF59E0B))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = accountName.ifBlank { "NGA 会员" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForumColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        BadgePill(
                            text = "已登录",
                            containerColor = ForumColors.AccentSoft,
                            contentColor = ForumColors.Accent
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("退出", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    } else {
        SettingsGroupCard {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Login,
                iconTint = ForumColors.Accent,
                title = "账号密码登录",
                onClick = onLoginPassword
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Default.Public,
                iconTint = ForumColors.Link,
                title = "网页快捷登录",
                onClick = onLoginWeb
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Default.ContentPaste,
                iconTint = Color(0xFF0D9488),
                title = "粘贴 Cookie 登录",
                onClick = onPasteCookie
            )
        }
    }
}

@Composable
private fun <T> SegmentedSelectorRow(
    items: List<T>,
    selectedItem: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ForumColors.SurfaceMuted,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                val bg by animateColorAsState(
                    targetValue = if (selected) ForumColors.Surface else ForumColors.SurfaceMuted,
                    animationSpec = tween(160),
                    label = "segBg"
                )
                Surface(
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = bg,
                    shadowElevation = if (selected) 1.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelOf(item),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) ForumColors.Accent else ForumColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    subtitle: String? = null,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ForumColors.Meta
                    )
                    Spacer(Modifier.height(6.dp))
                }
                options.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        onClick = { onSelect(option) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ForumColors.AccentSoft else ForumColors.SurfaceMuted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = labelOf(option),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ForumColors.Accent else ForumColors.TextPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ForumColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.5.dp)
                .height(13.dp)
                .clip(CircleShape)
                .background(ForumColors.Accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ForumColors.TextPrimary
        )
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsIconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ForumColors.TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ForumColors.Meta
                )
            }
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ForumColors.Floor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = ForumColors.Divider,
        modifier = Modifier.padding(start = 62.dp)
    )
}
