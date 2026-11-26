package com.ngalite.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ngalite.app.NgaApp
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.DisplayMode
import com.ngalite.app.data.DisplayModeStore
import com.ngalite.app.data.FavoriteStore
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumRepository
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.ShortcutHelper
import com.ngalite.app.data.ThreadRecordsStore
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
import kotlin.math.abs

sealed interface ListUiState {
    data object Loading : ListUiState
    data class Success(
        val topics: List<Topic>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val loadMoreError: String? = null,
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

    private var generation = 0L
    private var lastSwitchTime = 0L
    private val switchDebounceMs = 200L

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
                ThreadRecordsStore.recordForumVisit(forum.fid)
                load()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = ListUiState.Error(t.message ?: "加载失败")
            }
        }
    }

    fun revertFromLoginRequired() {
        if (_currentForum.value.requiresLogin() && lastAccessibleForum != null) {
            fid = lastAccessibleForum!!.fid
            _currentForum.value = lastAccessibleForum!!
        }
        load()
    }

    fun refresh() {
        val current = _state.value
        if (current is ListUiState.Success) {
            load(keepContentForRefresh = true)
        } else {
            load(keepContentForRefresh = false)
        }
    }

    fun load(keepContentForRefresh: Boolean = false) {
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

        val prevSuccess = if (keepContentForRefresh) _state.value as? ListUiState.Success else null
        if (prevSuccess != null) {
            _state.value = prevSuccess.copy(isRefreshing = true, loadMoreError = null)
        } else {
            currentPage = 1
            hasMore = true
            topics = emptyList()
            _state.value = ListUiState.Loading
        }

        val myGen = ++generation
        loadJob = viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThreadList(fid, page = 1) }
                if (myGen != generation) return@launch
                val newTopics = NgaParser.parseTopicList(html)
                    .distinctBy { it.tid }
                    .map { topic ->
                        previewCache[topic.tid]?.let { topic.copy(previewImages = it) } ?: topic
                    }
                if (myGen != generation) return@launch
                currentPage = 1
                topics = newTopics
                hasMore = newTopics.isNotEmpty()
                _state.value = ListUiState.Success(
                    topics = topics,
                    isRefreshing = false,
                    hasMore = hasMore,
                    generation = myGen
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (myGen != generation) return@launch
                if (prevSuccess != null) {
                    _state.value = prevSuccess.copy(
                        isRefreshing = false,
                        loadMoreError = t.message ?: "刷新失败"
                    )
                } else {
                    _state.value = ListUiState.Error(t.message ?: "未知错误")
                }
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
    onTopicClick: (tid: String, forumName: String) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: ListViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val currentForum by vm.currentForum.collectAsState()
    val favorites by FavoriteStore.favorites.collectAsState()
    val isFavorite = currentForum.fid in favorites

    var showLoginDialog by remember { mutableStateOf(false) }
    var displayMode by rememberSaveable(fid) { mutableStateOf(DisplayModeStore.getMode(fid)) }
    var showFilterBar by rememberSaveable(fid) { mutableStateOf(false) }
    var filterQuery by rememberSaveable(fid) { mutableStateOf("") }

    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()

    val showScrollTop by remember(displayMode) {
        derivedStateOf {
            if (displayMode == DisplayMode.TEXT) {
                listState.firstVisibleItemIndex > 4
            } else {
                gridState.firstVisibleItemIndex > 4
            }
        }
    }

    LaunchedEffect(fid) {
        vm.loadForum(fid)
    }

    var scrolledToTopGen by rememberSaveable { mutableStateOf(-1L) }
    LaunchedEffect(state) {
        val s = state
        if (s is ListUiState.Success && s.generation != scrolledToTopGen) {
            scrolledToTopGen = s.generation
            if (displayMode == DisplayMode.TEXT) listState.scrollToItem(0)
            else gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, displayMode) {
        if (displayMode != DisplayMode.TEXT) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            lastVisible to totalItems
        }
            .filter { (last, total) -> last >= total - 4 && total > 0 }
            .drop(1)
            .collect {
                val s = vm.state.value
                if (s is ListUiState.Success && s.hasMore && !s.isLoadingMore && filterQuery.isBlank()) {
                    vm.loadMore()
                }
            }
    }

    LaunchedEffect(gridState, displayMode) {
        if (displayMode != DisplayMode.WATERFALL) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            lastVisible to totalItems
        }
            .filter { (last, total) -> last >= total - 5 && total > 0 }
            .drop(1)
            .collect {
                val s = vm.state.value
                if (s is ListUiState.Success && s.hasMore && !s.isLoadingMore && filterQuery.isBlank()) {
                    vm.loadMore()
                }
            }
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = ForumColors.Page,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ForumColors.Page)
        ) {
            Column(Modifier.fillMaxSize()) {
                // 吸顶现代顶栏
                ForumListTopBar(
                    forum = currentForum,
                    isFavorite = isFavorite,
                    displayMode = displayMode,
                    showFilter = showFilterBar,
                    onBack = onBack,
                    onToggleFilter = {
                        showFilterBar = !showFilterBar
                        if (!showFilterBar) filterQuery = ""
                    },
                    onToggleMode = {
                        val next = if (displayMode == DisplayMode.TEXT) DisplayMode.WATERFALL else DisplayMode.TEXT
                        displayMode = next
                        DisplayModeStore.setMode(fid, next)
                    },
                    onToggleFavorite = {
                        if (currentForum.fid.isNotBlank()) FavoriteStore.toggle(currentForum.fid)
                    },
                    onRefresh = { vm.refresh() },
                    onPinShortcut = {
                        if (currentForum.fid.isNotBlank()) {
                            val ok = ShortcutHelper.requestPinShortcut(context, currentForum)
                            if (!ok) Toast.makeText(context, "当前设备不支持桌面快捷方式", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 可折叠本地过滤条
                AnimatedVisibility(
                    visible = showFilterBar,
                    enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
                ) {
                    ForumFilterStrip(
                        query = filterQuery,
                        onQueryChange = { filterQuery = it }
                    )
                }

                // 主内容区域
                Box(modifier = Modifier.weight(1f)) {
                    when (val s = state) {
                        is ListUiState.Loading -> {
                            ThreadListSkeleton(isWaterfall = displayMode == DisplayMode.WATERFALL)
                        }

                        is ListUiState.Error -> {
                            ErrorStateView(
                                title = "板块加载失败",
                                message = s.message,
                                onRetry = { vm.loadForum(fid) },
                                secondaryActionLabel = if (onBack != null) "返回社区" else null,
                                onSecondaryAction = onBack
                            )
                        }

                        is ListUiState.LoginRequired -> {
                            EmptyStateView(
                                title = "访问「${s.forumName}」需要登录",
                                subtitle = "登录后即可正常浏览该板块内容",
                                icon = Icons.Outlined.Lock,
                                actionLabel = "立即登录",
                                onAction = { showLoginDialog = true }
                            )
                        }

                        is ListUiState.Success -> {
                            val visibleTopics = remember(s.topics, filterQuery) {
                                if (filterQuery.isBlank()) s.topics
                                else {
                                    val q = filterQuery.trim()
                                    s.topics.filter {
                                        it.title.contains(q, ignoreCase = true) ||
                                            it.author.contains(q, ignoreCase = true)
                                    }
                                }
                            }

                            PullToRefreshBox(
                                isRefreshing = s.isRefreshing,
                                onRefresh = { vm.refresh() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AnimatedContent(
                                    targetState = displayMode,
                                    transitionSpec = {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                    },
                                    label = "displayModeSwitch"
                                ) { mode ->
                                    if (mode == DisplayMode.TEXT) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 10.dp,
                                                bottom = 24.dp + navBarBottomPadding
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (visibleTopics.isEmpty()) {
                                                item(key = "empty_text") {
                                                    ListStatusHint(
                                                        if (filterQuery.isBlank()) "该板块暂无帖子"
                                                        else "没有包含「$filterQuery」的帖子"
                                                    )
                                                }
                                            }

                                            items(visibleTopics, key = { it.tid }) { topic ->
                                                ModernTopicCard(
                                                    topic = topic,
                                                    onClick = { onTopicClick(topic.tid, currentForum.name) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }

                                            if (s.isLoadingMore && filterQuery.isBlank()) {
                                                item(key = "loading_more") { ListLoadingMore() }
                                            }

                                            if (!s.hasMore && visibleTopics.isNotEmpty() && filterQuery.isBlank()) {
                                                item(key = "no_more") { ListStatusHint("— 已显示全部内容 —") }
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
                                    } else {
                                        LazyVerticalStaggeredGrid(
                                            state = gridState,
                                            columns = StaggeredGridCells.Fixed(2),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 10.dp,
                                                bottom = 24.dp + navBarBottomPadding
                                            ),
                                            verticalItemSpacing = 10.dp,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (visibleTopics.isEmpty()) {
                                                item(key = "empty_grid", span = StaggeredGridItemSpan.FullLine) {
                                                    ListStatusHint(
                                                        if (filterQuery.isBlank()) "该板块暂无帖子"
                                                        else "没有包含「$filterQuery」的帖子"
                                                    )
                                                }
                                            }

                                            itemsIndexed(visibleTopics, key = { _, topic -> topic.tid }) { _, topic ->
                                                WaterfallTopicCard(
                                                    topic = topic,
                                                    onClick = { onTopicClick(topic.tid, currentForum.name) },
                                                    onPreviewNeeded = { vm.requestTopicPreview(topic.tid) },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }

                                            if (s.isLoadingMore && filterQuery.isBlank()) {
                                                item(key = "loading_more_grid", span = StaggeredGridItemSpan.FullLine) {
                                                    ListLoadingMore()
                                                }
                                            }

                                            if (!s.hasMore && visibleTopics.isNotEmpty() && filterQuery.isBlank()) {
                                                item(key = "no_more_grid", span = StaggeredGridItemSpan.FullLine) {
                                                    ListStatusHint("— 已显示全部内容 —")
                                                }
                                            }

                                            s.loadMoreError?.let { message ->
                                                item(key = "load_more_error_grid", span = StaggeredGridItemSpan.FullLine) {
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
                                }
                            }
                        }
                    }

                    // 回到顶部悬浮按钮
                    ScrollToTopFab(
                        visible = showScrollTop && state is ListUiState.Success,
                        onClick = {
                            scope.launch {
                                if (displayMode == DisplayMode.TEXT) listState.animateScrollToItem(0)
                                else gridState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 18.dp, bottom = 20.dp)
                    )
                }
            }
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

@Composable
private fun ForumListTopBar(
    forum: Forum,
    isFavorite: Boolean,
    displayMode: DisplayMode,
    showFilter: Boolean,
    onBack: (() -> Unit)?,
    onToggleFilter: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPinShortcut: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = ForumColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = topInset + 6.dp,
                        bottom = 8.dp,
                        start = if (onBack != null) 4.dp else 14.dp,
                        end = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = ForumColors.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (forum.fid.isNotBlank()) {
                    ForumIcon(forum = forum, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = forum.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForumColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (displayMode == DisplayMode.TEXT) "列表模式 · 下拉刷新" else "图文瀑布流 · 下拉刷新",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onToggleFilter, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "本页筛选",
                        tint = if (showFilter) ForumColors.Accent else ForumColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onToggleMode, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (displayMode == DisplayMode.TEXT) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = if (displayMode == DisplayMode.TEXT) "切换瀑布流" else "切换列表",
                        tint = ForumColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                BouncyFavoriteButton(
                    isFavorite = isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(38.dp),
                    iconSize = 20.dp
                )

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = ForumColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("刷新列表") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRefresh()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加到桌面") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPinShortcut()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
        }
    }
}

@Composable
private fun ForumFilterStrip(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        color = ForumColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ForumColors.SurfaceMuted)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ForumColors.Meta,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "在当前已加载帖子中筛选标题或作者…",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForumColors.Meta
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = ForumColors.TextPrimary),
                        cursorBrush = SolidColor(ForumColors.Accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空",
                        tint = ForumColors.Meta,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
            HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
        }
    }
}

@Composable
private fun ListStatusHint(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = ForumColors.Meta
    )
}

@Composable
private fun ListLoadingMore() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = ForumColors.Accent
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在加载更多…",
            style = MaterialTheme.typography.labelSmall,
            color = ForumColors.Meta
        )
    }
}

/**
 * 现代帖子列表卡片：热度标签 + 清晰标题 + 作者/时间/回复胶囊。
 */
@Composable
private fun ModernTopicCard(
    topic: Topic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val replyCount = topic.replies.toIntOrNull() ?: 0
    val isHot = replyCount >= 100
    val isWarm = replyCount in 30..99

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 热度标签行（仅热门帖展示，避免信息过载）
            if (isHot || isWarm) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    if (isHot) {
                        BadgePill(
                            text = "热帖",
                            containerColor = ForumColors.HotSoft,
                            contentColor = ForumColors.Hot,
                            icon = Icons.Outlined.LocalFireDepartment
                        )
                    } else {
                        BadgePill(
                            text = "热议",
                            containerColor = ForumColors.OwnerBg,
                            contentColor = ForumColors.OwnerText
                        )
                    }
                }
            }

            // 标题
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleSmall,
                color = ForumColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(9.dp))

            // 底部元信息行：最后回复人 + 时间 …… 回复数胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (topic.author.isNotBlank()) {
                        Text(
                            text = topic.author,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = ForumColors.Link,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (topic.replyTime.isNotBlank()) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForumColors.Floor
                            )
                        }
                    }
                    if (topic.replyTime.isNotBlank()) {
                        Text(
                            text = topic.replyTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Meta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // 回复数胶囊
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isHot -> ForumColors.HotSoft
                                isWarm -> ForumColors.OwnerBg
                                else -> ForumColors.SurfaceMuted
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = when {
                            isHot -> ForumColors.Hot
                            isWarm -> ForumColors.OwnerText
                            else -> ForumColors.Meta
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = topic.replies.ifBlank { "0" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isHot -> ForumColors.Hot
                            isWarm -> ForumColors.OwnerText
                            else -> ForumColors.Strong
                        }
                    )
                }
            }
        }
    }
}

/**
 * 现代图文瀑布流卡片：
 * - 有图帖：上方圆角大图 + 多图角标，下方高对比度文本区（标题 + 作者/回复数）
 * - 纯文帖：自然包裹高度 + 柔和主题色条 + 清晰标题与元数据
 */
@Composable
private fun WaterfallTopicCard(
    topic: Topic,
    onClick: () -> Unit,
    onPreviewNeeded: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(topic.tid) { onPreviewNeeded() }

    val imageUrl = topic.previewImages.firstOrNull()
    val replyCount = topic.replies.toIntOrNull() ?: 0
    val isHot = replyCount >= 100

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        if (imageUrl != null) {
            val aspectRatio = remember(topic.tid) {
                val hash = abs(topic.tid.hashCode()) % 100
                0.82f + (hash / 100f) * 0.45f
            }
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(ForumColors.SurfaceMuted)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = topic.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                    if (topic.previewImages.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${topic.previewImages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ForumColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    WaterfallMetaRow(topic = topic, replyCount = replyCount, isHot = isHot)
                }
            }
        } else {
            val accentColors = listOf(
                ForumColors.Accent,
                ForumColors.Link,
                ForumColors.OwnerText,
                Color(0xFF0D9488),
                Color(0xFF7C3AED)
            )
            val accentBarColor = remember(topic.tid, accentColors) {
                accentColors[abs(topic.tid.hashCode()) % accentColors.size]
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentBarColor)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ForumColors.TextPrimary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                WaterfallMetaRow(topic = topic, replyCount = replyCount, isHot = isHot)
            }
        }
    }
}

@Composable
private fun WaterfallMetaRow(
    topic: Topic,
    replyCount: Int,
    isHot: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = topic.author.ifBlank { topic.replyTime },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = ForumColors.Meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (replyCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$replyCount 回复",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isHot) ForumColors.Hot else ForumColors.Strong
            )
        }
    }
}
