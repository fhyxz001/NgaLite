package com.ngalite.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Topic
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
    Forum("706", "大时代"),
    Forum("-447601", "二次元国家地理"),
    Forum("-60252908", "旮旯game"),
    Forum("498", "二手交易"),
    Forum("428", "手综"),
    Forum("-202020", "程序员职业交流"),
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
    onSettingsClick: () -> Unit,
    vm: ListViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val currentForum by vm.currentForum.collectAsState()
    var showForumMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    /** 读取剪贴板中的 NGA 帖子链接并跳转 */
    fun readClipboardAndOpen() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        val regex = Regex("""https?://bbs\.nga\.cn/read\.php\?\S*?tid=(\d+)""")
        val match = regex.find(text)
        if (match != null) {
            onTopicClick(match.groupValues[1])
        } else {
            Toast.makeText(context, "剪贴板中没有 NGA 帖子链接", Toast.LENGTH_SHORT).show()
        }
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
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
                    IconButton(onClick = { readClipboardAndOpen() }) {
                        val inBitmap = remember { BitmapFactory.decodeStream(context.assets.open("in.png")) }
                        Image(bitmap = inBitmap.asImageBitmap(), contentDescription = "读取剪贴板链接", modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onSettingsClick) {
                        val settingsBitmap = remember { BitmapFactory.decodeStream(context.assets.open("set.png")) }
                        Image(bitmap = settingsBitmap.asImageBitmap(), contentDescription = "设置", modifier = Modifier.size(24.dp))
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
