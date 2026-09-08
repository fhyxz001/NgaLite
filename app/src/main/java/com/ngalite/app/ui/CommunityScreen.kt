package com.ngalite.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Scaffold(contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) { padding ->
        when (val current = state) {
            is CommunityUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is CommunityUiState.Error -> ErrorContent(
                message = current.message,
                onRetry = { vm.load(context) },
                modifier = Modifier.padding(padding)
            )
            is CommunityUiState.Success -> CommunityContent(
                state = current,
                onQueryChange = vm::updateQuery,
                onCategoryClick = vm::selectCategory,
                onForumClick = { onForumClick(it.fid) },
                onForumLongClick = { shortcutForum = it },
                modifier = Modifier.padding(padding)
            )
        }
    }

    // 长按板块弹出"添加到桌面"确认对话框
    shortcutForum?.let { forum ->
        AlertDialog(
            onDismissRequest = { shortcutForum = null },
            icon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
            title = { Text("添加到桌面") },
            text = { Text("将「${forum.name}」添加到桌面快捷方式，点击后直接进入该板块。") },
            confirmButton = {
                TextButton(onClick = {
                    val success = ShortcutHelper.requestPinShortcut(context, forum)
                    if (!success) {
                        android.widget.Toast.makeText(
                            context, "当前设备不支持桌面快捷方式", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    shortcutForum = null
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { shortcutForum = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u6682\u65f6\u65e0\u6cd5\u52a0\u8f7d\u793e\u533a", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("\u91cd\u65b0\u52a0\u8f7d") }
    }
}

@Composable
private fun CommunityContent(
    state: CommunityUiState.Success,
    onQueryChange: (String) -> Unit,
    onCategoryClick: (ForumCategory) -> Unit,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ForumColors.Page)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        // 顶部白底区块：标题 + 论坛风格搜索框
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ForumColors.Surface)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                "社区",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            )
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "搜索板块",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ForumColors.Meta
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ForumColors.Meta
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清空搜索",
                                modifier = Modifier.size(18.dp),
                                tint = ForumColors.Meta
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ForumColors.QuoteBg,
                    unfocusedContainerColor = ForumColors.QuoteBg,
                    disabledContainerColor = ForumColors.QuoteBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.query.isNotBlank()) {
            ForumList(
                forums = state.searchResults,
                emptyMessage = "没有找到相关板块",
                onForumClick = onForumClick,
                onForumLongClick = onForumLongClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            CategoryTabs(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategoryClick = onCategoryClick
            )
            ForumList(
                forums = state.selectedCategory.forums,
                emptyMessage = "这里还没有收藏的板块\n长按板块图标可添加到桌面",
                onForumClick = onForumClick,
                onForumLongClick = onForumLongClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 分类标签：论坛风格下划线选中态，白底一栏 */
@Composable
private fun CategoryTabs(
    categories: List<ForumCategory>,
    selectedCategory: ForumCategory,
    onCategoryClick: (ForumCategory) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(ForumColors.Surface)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            items(categories, key = { "category_${it.name}" }) { category ->
                val selected = category.name == selectedCategory.name
                Column(
                    modifier = Modifier
                        .clickable { onCategoryClick(category) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) ForumColors.Accent else ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (selected) ForumColors.Accent else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
    }
}

/** 板块列表：图标 + 名称/简介 + 右箭头，行间发丝线 */
@Composable
private fun ForumList(
    forums: List<Forum>,
    emptyMessage: String,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit,
    modifier: Modifier = Modifier
) {
    if (forums.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = ForumColors.Meta,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
        ) {
            items(forums, key = { "forum_${it.fid}" }) { forum ->
                ForumRow(
                    forum = forum,
                    onClick = { onForumClick(forum) },
                    onLongClick = { onForumLongClick(forum) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumRow(
    forum: Forum,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPressed) ForumColors.QuoteBg else ForumColors.Surface)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ForumIcon(forum = forum, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = forum.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (forum.description.isNotBlank()) {
                    Text(
                        text = forum.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ForumColors.Floor,
                modifier = Modifier.size(18.dp)
            )
        }
        HorizontalDivider(
            color = ForumColors.Divider,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 66.dp)
        )
    }
}
