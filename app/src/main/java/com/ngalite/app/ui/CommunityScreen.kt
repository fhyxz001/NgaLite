package com.ngalite.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumCategory
import com.ngalite.app.data.ShortcutHelper

@Composable
fun CommunityScreen(
    onForumClick: (String) -> Unit,
    vm: CommunityViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var shortcutForum by remember { mutableStateOf<Forum?>(null) }

    LaunchedEffect(Unit) { vm.load(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page)
    ) {
        when (val current = state) {
            is CommunityUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForumColors.Accent, strokeWidth = 2.5.dp)
                }
            }

            is CommunityUiState.Error -> {
                ErrorStateView(
                    title = "无法加载社区板块",
                    message = current.message,
                    onRetry = { vm.load(context) }
                )
            }

            is CommunityUiState.Success -> {
                CommunityContent(
                    state = current,
                    onQueryChange = vm::updateQuery,
                    onCategoryClick = vm::selectCategory,
                    onToggleFavorite = { vm.toggleFavorite(it.fid) },
                    onForumClick = { onForumClick(it.fid) },
                    onForumLongClick = { shortcutForum = it }
                )
            }
        }
    }

    shortcutForum?.let { forum ->
        AlertDialog(
            onDismissRequest = { shortcutForum = null },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.AddToHomeScreen,
                    contentDescription = null,
                    tint = ForumColors.Accent
                )
            },
            title = { Text("添加到桌面") },
            text = { Text("将「${forum.name}」添加到桌面快捷方式，点击后可直接进入该板块。") },
            confirmButton = {
                TextButton(onClick = {
                    val success = ShortcutHelper.requestPinShortcut(context, forum)
                    if (!success) {
                        android.widget.Toast.makeText(
                            context, "当前设备不支持桌面快捷方式", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    shortcutForum = null
                }) { Text("添加", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { shortcutForum = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CommunityContent(
    state: CommunityUiState.Success,
    onQueryChange: (String) -> Unit,
    onCategoryClick: (ForumCategory) -> Unit,
    onToggleFavorite: (Forum) -> Unit,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var showSearchBar by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page)
    ) {
        // 顶部品牌栏与搜索图标
        Surface(
            color = ForumColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 10.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "NgaLite",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = ForumColors.TextPrimary
                        )
                        Text(
                            text = "艾泽拉斯国家地理 · 探索板块",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Meta
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BadgePill(
                            text = "${state.favoriteFids.size} 收藏",
                            containerColor = ForumColors.AccentSoft,
                            contentColor = ForumColors.Accent,
                            icon = Icons.Default.Explore
                        )
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    onQueryChange("")
                                    focusManager.clearFocus()
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Clear else Icons.Default.Search,
                                contentDescription = if (showSearchBar) "关闭搜索" else "搜索板块",
                                tint = if (showSearchBar) ForumColors.Accent else ForumColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 可折叠搜索栏
                AnimatedVisibility(visible = showSearchBar) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        CommunitySearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            onSearchAction = { focusManager.clearFocus() }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)

        // 搜索态 vs 常规浏览态平滑切换
        AnimatedContent(
            targetState = state.query.isNotBlank(),
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f),
            label = "communitySearchSwitch"
        ) { isSearching ->
            if (isSearching) {
                SearchResultsPane(
                    results = state.searchResults,
                    favoriteFids = state.favoriteFids,
                    onToggleFavorite = onToggleFavorite,
                    onForumClick = onForumClick,
                    onForumLongClick = onForumLongClick
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    // 现代双栏板块浏览：左侧分类导航轨 + 右侧板块卡片流
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CategorySideRail(
                            categories = state.categories,
                            selectedName = state.selectedCategory.name,
                            onCategoryClick = onCategoryClick
                        )

                        AnimatedContent(
                            targetState = state.selectedCategory,
                            transitionSpec = {
                                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            label = "categoryForums"
                        ) { category ->
                            CategoryForumPane(
                                category = category,
                                favoriteFids = state.favoriteFids,
                                onToggleFavorite = onToggleFavorite,
                                onForumClick = onForumClick,
                                onForumLongClick = onForumLongClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.SurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = ForumColors.Meta,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索板块名称、简介或 FID…",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = ForumColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(ForumColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空搜索",
                        tint = ForumColors.Meta,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentForumsStrip(
    forums: List<Forum>,
    onForumClick: (Forum) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ForumColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近常逛",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ForumColors.Strong
            )
        }
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(forums, key = { "recent_${it.fid}" }) { forum ->
                Surface(
                    onClick = { onForumClick(forum) },
                    shape = RoundedCornerShape(12.dp),
                    color = ForumColors.SurfaceMuted,
                    border = BorderStroke(1.dp, ForumColors.Divider)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ForumIcon(forum = forum, size = 22.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = forum.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = ForumColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
    }
}

@Composable
private fun CategorySideRail(
    categories: List<ForumCategory>,
    selectedName: String,
    onCategoryClick: (ForumCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(ForumColors.SurfaceMuted),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(categories, key = { "rail_${it.name}" }) { category ->
            val selected = category.name == selectedName
            val bgColor by animateColorAsState(
                targetValue = if (selected) ForumColors.Page else ForumColors.SurfaceMuted,
                animationSpec = tween(180),
                label = "railBg"
            )
            val indicatorHeight by animateDpAsState(
                targetValue = if (selected) 20.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "railInd"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .clickable { onCategoryClick(category) }
                    .padding(vertical = 13.dp, horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(indicatorHeight)
                            .clip(CircleShape)
                            .background(ForumColors.Accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = category.name,
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

@Composable
private fun CategoryForumPane(
    category: ForumCategory,
    favoriteFids: Set<String>,
    onToggleFavorite: (Forum) -> Unit,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit
) {
    if (category.forums.isEmpty()) {
        EmptyStateView(
            title = if (category.name == "我的收藏") "还没有收藏任何板块" else "该分类暂无板块",
            subtitle = if (category.name == "我的收藏") "点击板块右侧星标即可加入收藏\n长按板块可添加到手机桌面" else null,
            icon = Icons.Outlined.StarOutline
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "cat_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ForumColors.TextPrimary
                )
                Text(
                    text = "${category.forums.size} 个板块 · 长按可加桌面",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = ForumColors.Meta
                )
            }
        }

        items(category.forums, key = { "forum_${category.name}_${it.fid}" }) { forum ->
            ForumCardRow(
                forum = forum,
                isFavorite = forum.fid in favoriteFids,
                onToggleFavorite = { onToggleFavorite(forum) },
                onClick = { onForumClick(forum) },
                onLongClick = { onForumLongClick(forum) },
                modifier = Modifier.animateItem()
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SearchResultsPane(
    results: List<Forum>,
    favoriteFids: Set<String>,
    onToggleFavorite: (Forum) -> Unit,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit
) {
    if (results.isEmpty()) {
        EmptyStateView(
            title = "没有找到匹配的板块",
            subtitle = "试试更换关键词，或者直接输入板块 FID",
            icon = Icons.Outlined.Forum
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "search_count") {
            Text(
                text = "找到 ${results.size} 个相关板块",
                style = MaterialTheme.typography.labelMedium,
                color = ForumColors.Meta,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
        items(results, key = { "search_${it.fid}" }) { forum ->
            ForumCardRow(
                forum = forum,
                isFavorite = forum.fid in favoriteFids,
                onToggleFavorite = { onToggleFavorite(forum) },
                onClick = { onForumClick(forum) },
                onLongClick = { onForumLongClick(forum) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumCardRow(
    forum: Forum,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForumIcon(forum = forum, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = forum.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = ForumColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (forum.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = forum.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            BouncyFavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                iconSize = 20.dp
            )
        }
    }
}
