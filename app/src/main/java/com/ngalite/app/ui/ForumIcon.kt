package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngalite.app.NgaApp
import com.ngalite.app.data.Forum
import kotlin.math.abs

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

private val placeholderGradients = listOf(
    Color(0xFFEA580C) to Color(0xFFFB923C),
    Color(0xFF2563EB) to Color(0xFF60A5FA),
    Color(0xFF0D9488) to Color(0xFF2DD4BF),
    Color(0xFF7C3AED) to Color(0xFFA78BFA),
    Color(0xFFDB2777) to Color(0xFFF472B6),
    Color(0xFF059669) to Color(0xFF34D399),
)

/**
 * 现代圆角方框（Squircle）板块图标：
 * 有内置图标时展示高清晰图片，无图标时根据板块名生成确定性渐变首字徽章。
 */
@Composable
fun ForumIcon(
    forum: Forum,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(size * 0.25f)
    val hasIcon = remember(forum.fid) { forumIconExists(forum.fid) }

    if (hasIcon) {
        val iconSize = size * 0.70f
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(ForumColors.SurfaceMuted)
                .border(1.dp, ForumColors.Divider, shape),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = "file:///android_asset/icons/f${forum.fid}.png",
                contentDescription = forum.name,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        val pair = remember(forum.fid, forum.name) {
            val idx = abs((forum.fid + forum.name).hashCode()) % placeholderGradients.size
            placeholderGradients[idx]
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(pair.first, pair.second))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = forum.name.firstOrNull()?.toString() ?: "?",
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
