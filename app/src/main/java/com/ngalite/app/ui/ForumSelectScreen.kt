package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngalite.app.NgaApp
import com.ngalite.app.data.FavoriteStore
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumCategory
import com.ngalite.app.data.ForumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val StarColor = Color(0xFFFFC107)

/**
 * 全局缓存 assets/icons/ 目录下的文件名集合，避免每个 [ForumSelectItem] 都扫描目录。
 * 首次访问时通过 [Lazy] 初始化。
 */
private val forumIconCache: Set<String> by lazy {
    try {
        NgaApp.instance.assets.list("icons")?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumSelectScreen(
    currentForum: Forum,
    onBack: () -> Unit,
    onForumSelected: (Forum) -> Unit
) {
    val context = LocalContext.current
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<ForumCategory>>(emptyList()) }
    var favoriteFids by remember { mutableStateOf(FavoriteStore.getFavorites()) }

    // 点击防抖：避免快速连点触发多次导航/切换
    var lastClickTime by remember { mutableStateOf(0L) }

    // 异步加载板块数据：在 IO 线程读取 assets，避免阻塞主线程
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ForumRepository.load(context)
        }
        categories = ForumRepository.categories
    }

    // 收藏的板块对象列表
    val favoriteForums = remember(favoriteFids, categories) {
        if (favoriteFids.isEmpty()) emptyList()
        else ForumRepository.allForums.filter { it.fid in favoriteFids }
    }

    // 切换收藏
    fun toggleFavorite(fid: String) {
        FavoriteStore.toggle(fid)
        favoriteFids = FavoriteStore.getFavorites()
    }

    // 板块点击防抖包装：点击后 400ms 内忽略后续点击，避免快速连点叠加导航
    fun handleForumClick(forum: Forum) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 400L) return
        lastClickTime = now
        onForumSelected(forum)
    }

    val filteredCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            categories.mapNotNull { cat ->
                val matched = cat.forums.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
                }
                if (matched.isEmpty()) null else ForumCategory(cat.name, matched)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索板块", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimary
                            ),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                            )
                        )
                    } else {
                        Text("板块选择")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = if (isSearching) "关闭搜索" else "搜索板块"
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ---- 收藏板块（置顶展示） ----
            if (favoriteForums.isNotEmpty() && searchQuery.isBlank()) {
                item(key = "header_favorites") {
                    CategoryHeader(name = "收藏板块")
                }
                itemsIndexed(
                    items = favoriteForums,
                    key = { _, f -> "fav_${f.fid}" }
                ) { index, forum ->
                    ForumSelectItem(
                        forum = forum,
                        isSelected = forum.fid == currentForum.fid,
                        isFavorite = true,
                        onFavoriteClick = { toggleFavorite(forum.fid) },
                        onClick = { handleForumClick(forum) }
                    )
                    if (index < favoriteForums.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                // 与下方分类的间距
                item(key = "fav_spacer") {
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ---- 全部分类 ----
            filteredCategories.forEach { category ->
                item(key = "header_${category.name}") {
                    CategoryHeader(name = category.name)
                }

                itemsIndexed(
                    items = category.forums,
                    key = { _, forum -> forum.fid }
                ) { index, forum ->
                    ForumSelectItem(
                        forum = forum,
                        isSelected = forum.fid == currentForum.fid,
                        isFavorite = forum.fid in favoriteFids,
                        onFavoriteClick = { toggleFavorite(forum.fid) },
                        onClick = { handleForumClick(forum) }
                    )
                    if (index < category.forums.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ForumSelectItem(
    forum: Forum,
    isSelected: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    // 全局缓存 assets/icons/ 下的文件列表，避免每项都扫描目录
    val iconExists = remember(forum.fid) { forumIconCache.contains("f${forum.fid}.png") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 板块图标：优先显示 assets 图标，兜底显示首字母
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (iconExists) {
                // 使用 Coil 异步加载 assets 图片，避免主线程解码阻塞
                coil.compose.AsyncImage(
                    model = "file:///android_asset/icons/f${forum.fid}.png",
                    contentDescription = forum.name,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    forum.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 名称 + 描述
        Column(modifier = Modifier.weight(1f)) {
            Text(
                forum.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (forum.description.isNotBlank()) {
                Text(
                    forum.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // 收藏星标
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onFavoriteClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) StarColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }

        // 当前选中标记
        if (isSelected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = "当前选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
