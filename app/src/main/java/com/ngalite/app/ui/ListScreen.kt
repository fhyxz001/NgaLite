package com.ngalite.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Topic
import com.ngalite.app.data.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ListUiState {
    data object Loading : ListUiState
    data class Success(
        val topics: List<Topic>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true
    ) : ListUiState

    data class Error(val message: String) : ListUiState
}

data class Forum(val fid: String, val name: String)

private val FORUMS = listOf(
    Forum("-7955747", "晴风村"),
    Forum("-7", "网事杂谈"),
    Forum("-447601", "二次元国家地理"),
    Forum("422", "炉石传说"),
    Forum("840", "游戏王大师决斗"),
    Forum("510558", "洛克王国世界"),
)

class ListViewModel : ViewModel() {
    private var fid = FORUMS.first().fid
    private val _state = MutableStateFlow<ListUiState>(ListUiState.Loading)
    val state: StateFlow<ListUiState> = _state

    private var currentPage = 1
    private var hasMore = true
    private var isLoadingMore = false
    private val allTopics = mutableListOf<Topic>()

    private val _currentForum = MutableStateFlow(FORUMS.first())
    val currentForum: StateFlow<Forum> = _currentForum

    /** 本次会话是否已检查过剪贴板，避免导航返回后重复弹窗 */
    var hasCheckedClipboard: Boolean = false
        private set

    fun markClipboardChecked() {
        hasCheckedClipboard = true
    }

    init { load() }

    fun switchForum(forum: Forum) {
        if (forum.fid == fid) return
        fid = forum.fid
        _currentForum.value = forum
        load()
    }

    fun load() {
        currentPage = 1
        hasMore = true
        allTopics.clear()
        viewModelScope.launch {
            _state.value = ListUiState.Loading
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = 1) }
                val topics = NgaParser.parseTopicList(html)
                allTopics.addAll(topics)
                hasMore = topics.isNotEmpty()
                _state.value = ListUiState.Success(topics = allTopics.toList())
            } catch (e: Exception) {
                _state.value = ListUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        viewModelScope.launch {
            _state.value = ListUiState.Success(
                topics = allTopics.toList(),
                isLoadingMore = true,
                hasMore = hasMore
            )
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = nextPage) }
                val topics = NgaParser.parseTopicList(html)
                allTopics.addAll(topics)
                currentPage = nextPage
                hasMore = topics.isNotEmpty()
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = allTopics.toList(),
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = allTopics.toList(),
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onTopicClick: (String) -> Unit,
    vm: ListViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val currentForum by vm.currentForum.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var showForumMenu by remember { mutableStateOf(false) }
    var clipboardTid by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

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

    // 进入应用时检查剪贴板是否包含 NGA 帖子链接
    LaunchedEffect(Unit) {
        if (vm.hasCheckedClipboard) return@LaunchedEffect
        vm.markClipboardChecked()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return@LaunchedEffect
        val regex = Regex("""https?://bbs\.nga\.cn/read\.php\?\S*?tid=(\d+)""")
        val match = regex.find(text) ?: return@LaunchedEffect
        clipboardTid = match.groupValues[1]
    }

    // 从后台切换回前台时再次检查剪贴板
    val lastDetectedTid = remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return@LifecycleEventObserver
                val regex = Regex("""https?://bbs\.nga\.cn/read\.php\?\S*?tid=(\d+)""")
                val match = regex.find(text) ?: return@LifecycleEventObserver
                val tid = match.groupValues[1]
                if (tid != lastDetectedTid.value) {
                    lastDetectedTid.value = tid
                    clipboardTid = tid
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 检测是否滚动到底部
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && totalItems > 0
        }
    }

    // 触发加载更多
    val currentState = state
    if (shouldLoadMore && currentState is ListUiState.Success && currentState.hasMore && !currentState.isLoadingMore) {
        LaunchedEffect(Unit) {
            vm.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(
                        onClick = { showForumMenu = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            currentForum.name,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "切换板块",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        DropdownMenu(
                            expanded = showForumMenu,
                            onDismissRequest = { showForumMenu = false }
                        ) {
                            FORUMS.forEach { forum ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            forum.name,
                                            fontWeight = if (forum.fid == currentForum.fid) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showForumMenu = false
                                        vm.switchForum(forum)
                                    },
                                    leadingIcon = {
                                        if (forum.fid == currentForum.fid) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { triggerCheckUpdate() }, enabled = !isCheckingUpdate) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = "检查更新")
                        }
                    }
                    IconButton(onClick = { showLogin = true }) {
                        Icon(Icons.Default.Login, contentDescription = "登录")
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
        when (val s = state) {
            is ListUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is ListUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.load() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重试")
                }
            }

            is ListUiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(s.topics, key = { it.tid }) { topic ->
                    TopicItem(topic) { onTopicClick(topic.tid) }
                }

                // 底部加载指示器
                if (s.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!s.hasMore) {
                    item(key = "no_more") {
                        Text(
                            "没有更多了",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    if (showLogin) {
        LoginDialog(onDismiss = { showLogin = false })
    }

    clipboardTid?.let { tid ->
        AlertDialog(
            onDismissRequest = { clipboardTid = null },
            title = { Text("检测到剪贴板链接", style = MaterialTheme.typography.titleLarge) },
            text = { Text("剪贴板中包含 NGA 帖子链接，是否立即查看？") },
            confirmButton = {
                TextButton(onClick = {
                    clipboardTid = null
                    onTopicClick(tid)
                }) { Text("我要看", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { clipboardTid = null }) { Text("取消") }
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

@Composable
private fun TopicItem(topic: Topic, onClick: () -> Unit) {
    val replies = topic.replies.toIntOrNull() ?: 0
    // 热度分级：回复数越高配色越醒目
    val isHot = replies >= 100
    val isWarm = replies in 30..99
    val badgeColor = when {
        isHot -> MaterialTheme.colorScheme.error
        isWarm -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val badgeContainer = when {
        isHot -> MaterialTheme.colorScheme.errorContainer
        isWarm -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                topic.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 回复数徽标
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (replies > 0) topic.replies else "0",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor
                    )
                }
                Text(
                    topic.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    topic.replyTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
