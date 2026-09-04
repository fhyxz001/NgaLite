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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
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
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("\u641c\u7d22\u7248\u5757") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "\u6e05\u7a7a\u641c\u7d22")
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        if (state.query.isNotBlank()) {
            ForumGrid(
                forums = state.searchResults,
                emptyMessage = "\u6ca1\u6709\u627e\u5230\u76f8\u5173\u7248\u5757",
                onForumClick = onForumClick,
                onForumLongClick = onForumLongClick,
                modifier = Modifier.weight(1f)
            )
        } else {
            CategoryPills(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategoryClick = onCategoryClick
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
            Spacer(Modifier.height(8.dp))
            ForumGrid(
                forums = state.selectedCategory.forums,
                emptyMessage = "\u8fd9\u91cc\u8fd8\u6ca1\u6709\u6536\u85cf\u7684\u7248\u5757\n\u70b9\u51fb\u661f\u6807\u5c06\u5b83\u4eec\u6dfb\u52a0\u5230\u6b64\u5904",
                onForumClick = onForumClick,
                onForumLongClick = onForumLongClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryPills(
    categories: List<ForumCategory>,
    selectedCategory: ForumCategory,
    onCategoryClick: (ForumCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { "category_${it.name}" }) { category ->
            val selected = category.name == selectedCategory.name
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
                        }
                    )
                    .clickable { onCategoryClick(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ForumGrid(
    forums: List<Forum>,
    emptyMessage: String,
    onForumClick: (Forum) -> Unit,
    onForumLongClick: (Forum) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (forums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(forums, key = { "forum_${it.fid}" }) { forum ->
                    ForumGridItem(
                        forum = forum,
                        onClick = { onForumClick(forum) },
                        onLongClick = { onForumLongClick(forum) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumGridItem(
    forum: Forum,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                // 板块图标按钮：正方形而非圆形
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)).background(ForumIconBackground),
                contentAlignment = Alignment.Center
            ) {
                ForumIcon(forum = forum, size = 72.dp)
            }
            Spacer(Modifier.height(10.dp))
                Text(
                    text = forum.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 32.dp)
                )
        }
    }
}
