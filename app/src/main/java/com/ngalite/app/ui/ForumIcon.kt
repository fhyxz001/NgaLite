package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngalite.app.NgaApp
import com.ngalite.app.data.Forum

/**
 * 全局缓存 assets/icons/ 目录下的文件名集合，避免每个图标项都扫描目录。
 */
private val forumIconCache: Set<String> by lazy {
    try {
        NgaApp.instance.assets.list("icons")?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
}

fun forumIconExists(fid: String): Boolean = forumIconCache.contains("f${fid}.png")

@Composable
fun ForumIcon(
    forum: Forum,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val iconSize = size * 0.65f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (forumIconExists(forum.fid)) {
            coil.compose.AsyncImage(
                model = "file:///android_asset/icons/f${forum.fid}.png",
                contentDescription = forum.name,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = forum.name.firstOrNull()?.toString() ?: "?",
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
