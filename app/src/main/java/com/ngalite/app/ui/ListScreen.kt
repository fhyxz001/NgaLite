package com.ngalite.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ngalite.app.NgaApp
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.FavoriteStore
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumRepository
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
    data class LoginRequired(val forumName: String) : ListUiState
}

private val LOGIN_REQUIRED_FIDS = setOf("-7", "-7955747")
private fun Forum.requiresLogin(): Boolean = fid in LOGIN_REQUIRED_FIDS

class ListViewModel : ViewModel() {
    private var fid = ""
    private val _state = MutableStateFlow<ListUiState>(ListUiState.Loading)
    val state: StateFlow<ListUiState> = _state

    private var currentPage = 1
    private var hasMore = true
    private var isLoadingMore = false
    private var topics: List<Topic> = emptyList()

    private val _currentForum = MutableStateFlow(Forum("", "加载中"))
    val currentForum: StateFlow<Forum> = _currentForum
    private var lastAccessibleForum: Forum? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var loadMoreJob: kotlinx.coroutines.Job? = null

    /** 代次计数器：每次 load() 自增，用于丢弃已取消协程的结果 */
    private var generation = 0L

    /** switchForum 防抖时间戳，避免快速连续切换导致状态错乱 */
    private var lastSwitchTime = 0L
    private val switchDebounceMs = 200L

    init {
        viewModelScope.launch {
            // 在 IO 线程加载板块数据，避免阻塞主线程
            ForumRepository.ensureLoadedAsync(NgaApp.instance)
            val all = ForumRepository.allForums
            if (all.isEmpty()) {
                _state.value = ListUiState.Error("板块数据加载失败")
                return@launch
            }
            // 优先使用收藏的第一个板块，无收藏时使用列表第一个
            val favoriteFids = FavoriteStore.getFavorites()
            val first = if (favoriteFids.isNotEmpty()) {
                all.firstOrNull { it.fid in favoriteFids } ?: all.first()
            } else {
                all.first()
            }
            fid = first.fid
            _currentForum.value = first
            lastAccessibleForum = all.firstOrNull { !it.requiresLogin() }
            load()
        }
    }

    fun switchForum(forum: Forum) {
        if (forum.fid == fid) return
        // 防抖：快速连续切换时只执行最后一次，避免叠加触发 load 与导航竞态
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < switchDebounceMs) return
        lastSwitchTime = now
        if (!forum.requiresLogin()) {
            lastAccessibleForum = forum
        }
        fid = forum.fid
        _currentForum.value = forum
        load()
    }

    /** 取消登录后回到上次无需登录的板块 */
    fun revertFromLoginRequired() {
        if (_currentForum.value.requiresLogin() && lastAccessibleForum != null) {
            fid = lastAccessibleForum!!.fid
            _currentForum.value = lastAccessibleForum!!
        }
        load()
    }

    fun load() {
        currentPage = 1
        hasMore = true
        topics = emptyList()
        loadJob?.cancel()
        loadMoreJob?.cancel()
        isLoadingMore = false
        val forum = _currentForum.value
        if (forum.requiresLogin() && !CookieStore.isLogin()) {
            _state.value = ListUiState.LoginRequired(forum.name)
            return
        }
        // 在协程外设置 Loading 状态，避免被旧协程 catch 块覆盖
        _state.value = ListUiState.Loading
        val myGen = ++generation
        loadJob = viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = 1) }
                if (myGen != generation) return@launch
                // 按 tid 去重，避免 NGA 单页返回重复帖子导致 LazyColumn 重复 key 崩溃
                val newTopics = NgaParser.parseTopicList(html).distinctBy { it.tid }
                if (myGen != generation) return@launch
                topics = newTopics
                hasMore = newTopics.isNotEmpty()
                _state.value = ListUiState.Success(topics = topics)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (myGen != generation) return@launch
                _state.value = ListUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        val myGen = generation
        loadMoreJob?.cancel()
        isLoadingMore = true
        val nextPage = currentPage + 1
        val snapshot = topics
        loadMoreJob = viewModelScope.launch {
            _state.value = ListUiState.Success(
                topics = snapshot,
                isLoadingMore = true,
                hasMore = hasMore
            )
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = nextPage) }
                if (myGen != generation) return@launch
                // 过滤掉已存在的 tid（NGA 置顶帖在每页都会重复出现），避免 LazyColumn 重复 key 崩溃
                val existingTids = snapshot.mapTo(mutableSetOf()) { it.tid }
                val newTopics = NgaParser.parseTopicList(html).filter { it.tid !in existingTids }
                if (myGen != generation) return@launch
                topics = snapshot + newTopics
                currentPage = nextPage
                hasMore = newTopics.isNotEmpty()
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = topics,
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (myGen != generation) return@launch
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = topics,
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
    onSettingsClick: () -> Unit,
    onForumSelectClick: () -> Unit,
    vm: ListViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val currentForum by vm.currentForum.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    /** 读取剪贴板中的 NGA 帖子链接并跳转 */
    fun readClipboardAndOpen() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // primaryClip 可能为 null 或 itemCount 为 0，直接 getItemAt(0) 会越界崩溃
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        } else ""
        val regex = Regex("""https?://bbs\.nga\.cn/read\.php\?\S*?tid=(\d+)""")
        val match = regex.find(text)
        if (match != null) {
            onTopicClick(match.groupValues[1])
        } else {
            Toast.makeText(context, "剪贴板中没有 NGA 帖子链接", Toast.LENGTH_SHORT).show()
        }
    }

    // 切换板块后滚动到顶部，避免保持旧滚动位置导致立即触发 loadMore 或显示异常
    LaunchedEffect(currentForum) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
    }

    // 滚动到底部时自动加载更多
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            lastVisible to totalItems
        }
            .filter { (last, total) -> last >= total - 3 && total > 0 }
            .drop(1) // 跳过初始值
            .collect {
                val s = vm.state.value
                if (s is ListUiState.Success && s.hasMore && !s.isLoadingMore) {
                    vm.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(
                        onClick = onForumSelectClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            currentForum.name,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "选择板块",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { readClipboardAndOpen() }) {
                        coil.compose.AsyncImage(
                            model = "file:///android_asset/in.png",
                            contentDescription = "读取剪贴板链接",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        coil.compose.AsyncImage(
                            model = "file:///android_asset/set.png",
                            contentDescription = "设置",
                            modifier = Modifier.size(24.dp)
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
        when (val s = state) {
            is ListUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is ListUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
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
                    Text("重试")
                }
            }

            is ListUiState.LoginRequired -> Column(
                Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "访问 ${s.forumName} 需要登录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showLoginDialog = true }) {
                    Text("登录")
                }
            }

            is ListUiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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

        if (showLoginDialog) {
            LoginDialog(onDismiss = {
                showLoginDialog = false
                if (CookieStore.isLogin()) vm.load() else vm.revertFromLoginRequired()
            })
        }
    }
}

@Composable
private fun TopicItem(topic: Topic, onClick: () -> Unit) {
    // 缓存热度计算结果，避免滚动时重复计算
    val colorScheme = MaterialTheme.colorScheme
    val (replies, badgeColor, badgeContainer) = remember(topic.replies, colorScheme) {
        val count = topic.replies.toIntOrNull() ?: 0
        val color = when {
            count >= 100 -> colorScheme.error
            count >= 30 -> colorScheme.tertiary
            else -> colorScheme.secondary
        }
        val container = when {
            count >= 100 -> colorScheme.errorContainer
            count >= 30 -> colorScheme.tertiaryContainer
            else -> colorScheme.secondaryContainer
        }
        Triple(count, color, container)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
