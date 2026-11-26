package com.ngalite.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngalite.app.data.ThreadRecord
import com.ngalite.app.data.ThreadRecordsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 足迹页：集中管理「收藏帖子」与「最近浏览」历史。
 */
@Composable
fun RecordsScreen(
    onTopicClick: (tid: String, forumName: String) -> Unit
) {
    val bookmarks by ThreadRecordsStore.bookmarks.collectAsState()
    val history by ThreadRecordsStore.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = 收藏帖子, 1 = 最近浏览
    var showClearDialog by remember { mutableStateOf(false) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val currentList = if (selectedTab == 0) bookmarks else history

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForumColors.Page)
    ) {
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
                            text = "足迹",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ForumColors.TextPrimary
                        )
                        Text(
                            text = "收藏的帖子与浏览历史",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Meta
                        )
                    }
                    if (currentList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "清空当前列表",
                                tint = ForumColors.Meta
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 分段胶囊切换
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ForumColors.SurfaceMuted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RecordTabPill(
                            label = "收藏帖子 (${bookmarks.size})",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        RecordTabPill(
                            label = "最近浏览 (${history.size})",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            modifier = Modifier.weight(1f),
            label = "recordsTab"
        ) { tab ->
            val itemsList = if (tab == 0) bookmarks else history
            if (itemsList.isEmpty()) {
                EmptyStateView(
                    title = if (tab == 0) "暂无收藏的帖子" else "还没有浏览记录",
                    subtitle = if (tab == 0) "在帖子详情页右上角点击星标即可收藏" else "阅读过的帖子会自动记录在这里，方便随时回看",
                    icon = if (tab == 0) Icons.Outlined.BookmarkBorder else Icons.Outlined.History
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itemsList, key = { "${tab}_${it.tid}" }) { record ->
                        ThreadRecordCard(
                            record = record,
                            onClick = { onTopicClick(record.tid, record.forumName) },
                            onRemove = {
                                if (tab == 0) ThreadRecordsStore.removeBookmark(record.tid)
                                else ThreadRecordsStore.removeHistory(record.tid)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if (selectedTab == 0) "清空收藏帖子" else "清空浏览历史") },
            text = {
                Text(
                    if (selectedTab == 0) "确定要移除全部已收藏的帖子吗？此操作不可撤销。"
                    else "确定要清空全部最近浏览记录吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedTab == 0) ThreadRecordsStore.clearBookmarks()
                    else ThreadRecordsStore.clearHistory()
                    showClearDialog = false
                }) { Text("清空", color = ForumColors.Hot, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecordTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) ForumColors.Surface else ForumColors.SurfaceMuted,
        animationSpec = tween(180),
        label = "tabPillBg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = bgColor,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ForumColors.TextPrimary else ForumColors.Meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThreadRecordCard(
    record: ThreadRecord,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(record.timestamp) {
        if (record.timestamp <= 0L) ""
        else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ForumColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (record.forumName.isNotBlank()) {
                        BadgePill(
                            text = record.forumName,
                            containerColor = ForumColors.LinkSoft,
                            contentColor = ForumColors.Link
                        )
                    }
                    if (record.author.isNotBlank()) {
                        Text(
                            text = record.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Meta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (formattedTime.isNotBlank()) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Floor
                        )
                    }
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除记录",
                    tint = ForumColors.Floor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
