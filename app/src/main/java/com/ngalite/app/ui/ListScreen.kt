package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface ListUiState {
    data object Loading : ListUiState
    data class Success(
        val topics: List<Topic>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val loadMoreError: String? = null,
        /** 加载代次：每次 load() 自增，用于区分全新加载与返回页面时的状态恢复 */
        val generation: Long = 0L
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
    private val previewSemaphore = Semaphore(4)
    private val previewJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val previewCache = LinkedHashMap<String, List<String>>()

    /** 代次计数器：每次 load() 自增，用于丢弃已取消协程的结果 */
    private var generation = 0L

    /** switchForum 防抖时间戳，避免快速连续切换导致状态错乱 */
    private var lastSwitchTime = 0L
    private val switchDebounceMs = 200L

    /**
     * 根据 fid 加载对应板块。用于从社区页进入帖子列表时被动初始化，
     * 避免旧版在 init 中自动加载导致的启动逻辑耦合。
     */
    fun loadForum(targetFid: String) {
        if (targetFid == fid && _state.value !is ListUiState.Loading) return
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < switchDebounceMs) return
        lastSwitchTime = now

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ForumRepository.ensureLoadedAsync(NgaApp.instance)
                }
                val forum = ForumRepository.allForums.firstOrNull { it.fid == targetFid }
                if (forum == null) {
                    _state.value = ListUiState.Error("板块不存在")
                    return@launch
                }
                if (!forum.requiresLogin()) {
                    lastAccessibleForum = forum
                }
                fid = forum.fid
                _currentForum.value = forum
                load()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = ListUiState.Error(t.message ?: "加载失败")
            }
        }
    }

    fun switchForum(forum: Forum) {
        if (forum.fid == fid) return
        loadForum(forum.fid)
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
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
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
                val newTopics = NgaParser.parseTopicList(html)
                    .distinctBy { it.tid }
                    .map { topic ->
                        previewCache[topic.tid]?.let { topic.copy(previewImages = it) } ?: topic
                    }
                if (myGen != generation) return@launch
                topics = newTopics
                hasMore = newTopics.isNotEmpty()
                _state.value = ListUiState.Success(topics = topics, hasMore = hasMore, generation = myGen)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (myGen != generation) return@launch
                _state.value = ListUiState.Error(t.message ?: "未知错误")
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
                hasMore = hasMore,
                generation = myGen
            )
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = nextPage) }
                if (myGen != generation) return@launch
                // 过滤掉已存在的 tid（NGA 置顶帖在每页都会重复出现），避免 LazyColumn 重复 key 崩溃
                val existingTids = snapshot.mapTo(mutableSetOf()) { it.tid }
                val newTopics = NgaParser.parseTopicList(html)
                    .filter { it.tid !in existingTids }
                    .map { topic ->
                        previewCache[topic.tid]?.let { topic.copy(previewImages = it) } ?: topic
                    }
                if (myGen != generation) return@launch
                topics = snapshot + newTopics
                currentPage = nextPage
                hasMore = newTopics.isNotEmpty()
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = topics,
                    isLoadingMore = false,
                    hasMore = hasMore,
                    generation = myGen
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (myGen != generation) return@launch
                isLoadingMore = false
                _state.value = ListUiState.Success(
                    topics = topics,
                    isLoadingMore = false,
                    hasMore = hasMore,
                    loadMoreError = t.message ?: "加载更多失败",
                    generation = myGen
                )
            }
        }
    }

    /** 帖子列表项可见时，异步加载主帖前 4 张图片作为缩略图预览 */
    fun requestTopicPreview(tid: String) {
        if (previewCache.containsKey(tid)) {
            updateTopicPreview(tid, previewCache.getValue(tid))
            return
        }
        if (previewJobs.containsKey(tid)) return

        val myGeneration = generation
        previewJobs[tid] = viewModelScope.launch {
            try {
                val images = withContext(Dispatchers.IO) {
                    previewSemaphore.withPermit {
                        NgaParser.parseMainPostImages(NgaApi.fetchThread(tid))
                    }
                }
                if (myGeneration != generation) return@launch
                previewCache[tid] = images
                while (previewCache.size > 200) {
                    previewCache.remove(previewCache.keys.first())
                }
                updateTopicPreview(tid, images)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // 单个帖子缩略图加载失败不应影响整个帖子列表
            } finally {
                if (myGeneration == generation) previewJobs.remove(tid)
            }
        }
    }

    private fun updateTopicPreview(tid: String, images: List<String>) {
        val index = topics.indexOfFirst { it.tid == tid }
        if (index < 0 || topics[index].previewImages == images) return
        topics = topics.toMutableList().also { list ->
            list[index] = list[index].copy(previewImages = images)
        }
        val current = _state.value
        if (current is ListUiState.Success) {
            _state.value = current.copy(topics = topics)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadsScreen(
    fid: String,
    onTopicClick: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: ListViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val currentForum by vm.currentForum.collectAsState()
    var isFavorite by remember(currentForum.fid) { mutableStateOf(FavoriteStore.isFavorite(currentForum.fid)) }
    var showLoginDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    /** 进入页面时根据 fid 加载板块 */
    LaunchedEffect(fid) {
        vm.loadForum(fid)
    }

    // 切换板块/重试等全新加载后滚动到顶部，避免保持旧滚动位置导致立即触发 loadMore 或显示异常；
    // 从详情页返回时 generation 不变，不重置滚动位置，保留用户浏览进度
    var scrolledToTopGen by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(state) {
        val s = state
        if (s is ListUiState.Success && s.generation != scrolledToTopGen) {
            scrolledToTopGen = s.generation
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
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        val topSpacing = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
        when (val s = state) {
            is ListUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is ListUiState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.loadForum(fid) }) {
                    Text("重试")
                }
            }

            is ListUiState.LoginRequired -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(MaterialTheme.colorScheme.surface),
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
                if (onBack != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text("返回社区") }
                }
            }

            is ListUiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF3F3F3)),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topSpacing + 12.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                        Text(
                            currentForum.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                FavoriteStore.toggle(currentForum.fid)
                                isFavorite = FavoriteStore.isFavorite(currentForum.fid)
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                items(s.topics, key = { it.tid }) { topic ->
                    TopicItem(
                        topic = topic,
                        onClick = { onTopicClick(topic.tid) },
                        onPreviewNeeded = { vm.requestTopicPreview(topic.tid) }
                    )
                }

                if (s.topics.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "该板块暂无帖子",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                s.loadMoreError?.let { message ->
                    item(key = "load_more_error") {
                        TextButton(
                            onClick = vm::loadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$message，点击重试")
                        }
                    }
                }
            }
        }

        if (state !is ListUiState.Success && onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(padding)
                    .padding(top = topSpacing, start = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }

        if (showLoginDialog) {
            LoginDialog(onDismiss = {
                showLoginDialog = false
                if (CookieStore.isLogin()) {
                    vm.load()
                } else if (onBack != null) {
                    onBack()
                } else {
                    vm.revertFromLoginRequired()
                }
            })
        }
    }
}

@Composable
private fun TopicItem(
    topic: Topic,
    onClick: () -> Unit,
    onPreviewNeeded: () -> Unit
) {
    var previewImageUrl by remember(topic.tid) { mutableStateOf<String?>(null) }
    LaunchedEffect(topic.tid) { onPreviewNeeded() }
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                topic.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (topic.previewImages.isNotEmpty()) {
                TopicPreviewGrid(
                    images = topic.previewImages,
                    onImageClick = { previewImageUrl = it },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 回复数徽标
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
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
                    overflow = TextOverflow.Ellipsis,
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

    previewImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { previewImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "帖子图片预览",
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun TopicPreviewGrid(
    images: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val previews = images.take(4)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (previews.size == 1) {
            TopicPreviewImage(
                url = previews.first(),
                onClick = { onImageClick(previews.first()) },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        } else {
            previews.chunked(2).forEach { rowImages ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowImages.forEach { url ->
                        TopicPreviewImage(
                            url = url,
                            onClick = { onImageClick(url) },
                            modifier = Modifier.weight(1f).aspectRatio(1.35f)
                        )
                    }
                    if (rowImages.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicPreviewImage(
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = url,
        contentDescription = "主帖图片",
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}
