package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumCategory

private val StarColor = androidx.compose.ui.graphics.Color(0xFFFFC107)

@Composable
fun CommunityScreen(
    onForumClick: (String) -> Unit,
    vm: CommunityViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.load(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        when (val s = state) {
            is CommunityUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }

            is CommunityUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            is CommunityUiState.Success -> CommunityContent(
                state = s,
                onCategoryClick = { vm.selectCategory(it) },
                onForumClick = { forum -> onForumClick(forum.fid) },
                onFavoriteClick = { vm.toggleFavorite(it.fid) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CommunityContent(
    state: CommunityUiState.Success,
    onCategoryClick: (ForumCategory) -> Unit,
    onForumClick: (Forum) -> Unit,
    onFavoriteClick: (Forum) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
    ) {
        // 左侧分类列表（包含「我的收藏」）
        LazyColumn(
            modifier = Modifier
                .width(100.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = state.categories,
                key = { "cat_${it.name}" }
            ) { category ->
                val selected = category.name == state.selectedCategory.name
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 右侧板块网格
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.selectedCategory.forums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无收藏板块\n点击右下角星标收藏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 84.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = state.selectedCategory.forums,
                        key = { "forum_${it.fid}" }
                    ) { forum ->
                        ForumGridItem(
                            forum = forum,
                            isFavorite = forum.fid in state.favoriteFids,
                            onClick = { onForumClick(forum) },
                            onFavoriteClick = { onFavoriteClick(forum) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumGridItem(
    forum: Forum,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ForumIcon(forum = forum, size = 48.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = forum.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(32.dp)
            )
        }

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) StarColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
