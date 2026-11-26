package com.ngalite.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 灵动弹性收藏按钮：点击收藏时带回弹缩放动画。
 */
@Composable
fun BouncyFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    activeTint: Color = ForumColors.OwnerText,
    inactiveTint: Color = ForumColors.Meta,
    contentDescription: String = if (isFavorite) "取消收藏" else "收藏"
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favScale"
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = contentDescription,
            tint = if (isFavorite) activeTint else inactiveTint,
            modifier = Modifier
                .size(iconSize)
                .scale(if (isFavorite) scale else 1f)
        )
    }
}

/**
 * 现代胶囊标签徽标（如 热、热议、楼主、板块名、页码等）。
 */
@Composable
fun BadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 返回顶部悬浮胶囊按钮：带淡入淡出和弹性缩放过渡。
 */
@Composable
fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)) +
            slideInVertically { it / 2 },
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)) +
            slideOutVertically { it / 2 }
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = ForumColors.SurfaceElevated,
            contentColor = ForumColors.Accent,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, ForumColors.Divider),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 统一的空状态视图：居中图标 + 主副文案 + 可选引导按钮。
 */
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ForumColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ForumColors.Meta,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ForumColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ForumColors.Meta,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * 统一的错误重试状态视图。
 */
@Composable
fun ErrorStateView(
    title: String = "加载遇到一点问题",
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ForumColors.HotSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = ForumColors.Hot,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ForumColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = ForumColors.Meta,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Button(
                    onClick = onSecondaryAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForumColors.SurfaceMuted,
                        contentColor = ForumColors.TextPrimary
                    )
                ) {
                    Text(secondaryActionLabel)
                }
            }
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新加载")
            }
        }
    }
}

/**
 * 动态 Shimmer 骨架块。
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val palette = ForumColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            palette.shimmerBase,
            palette.shimmerHighlight,
            palette.shimmerBase
        ),
        start = Offset(translateAnim - 350f, 0f),
        end = Offset(translateAnim, 260f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = rememberShimmerBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * 帖子列表骨架屏（支持列表态和双列瀑布流态）。
 */
@Composable
fun ThreadListSkeleton(
    isWaterfall: Boolean,
    modifier: Modifier = Modifier
) {
    val brush = rememberShimmerBrush()
    if (isWaterfall) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { idx ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ForumColors.Surface,
                        border = BorderStroke(1.dp, ForumColors.Divider)
                    ) {
                        Column {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (idx % 2 == 0) 150.dp else 110.dp),
                                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                                brush = brush
                            )
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShimmerBox(Modifier.fillMaxWidth().height(14.dp), brush = brush)
                                ShimmerBox(Modifier.fillMaxWidth(0.65f).height(14.dp), brush = brush)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { idx ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ForumColors.Surface,
                        border = BorderStroke(1.dp, ForumColors.Divider)
                    ) {
                        Column {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (idx % 2 == 0) 118.dp else 164.dp),
                                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                                brush = brush
                            )
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShimmerBox(Modifier.fillMaxWidth().height(14.dp), brush = brush)
                                ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp), brush = brush)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(7) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ForumColors.Surface,
                    border = BorderStroke(1.dp, ForumColors.Divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShimmerBox(Modifier.fillMaxWidth(0.92f).height(16.dp), brush = brush)
                        ShimmerBox(Modifier.fillMaxWidth(0.60f).height(16.dp), brush = brush)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ShimmerBox(Modifier.width(120.dp).height(12.dp), brush = brush)
                            ShimmerBox(Modifier.width(44.dp).height(16.dp), shape = RoundedCornerShape(6.dp), brush = brush)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 帖子详情骨架屏。
 */
@Composable
fun ThreadDetailSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ForumColors.Surface,
            border = BorderStroke(1.dp, ForumColors.Divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShimmerBox(Modifier.width(72.dp).height(20.dp), shape = RoundedCornerShape(6.dp), brush = brush)
                ShimmerBox(Modifier.fillMaxWidth().height(20.dp), brush = brush)
                ShimmerBox(Modifier.fillMaxWidth(0.75f).height(20.dp), brush = brush)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(Modifier.width(56.dp).height(18.dp), shape = RoundedCornerShape(6.dp), brush = brush)
                    ShimmerBox(Modifier.width(56.dp).height(18.dp), shape = RoundedCornerShape(6.dp), brush = brush)
                }
            }
        }
        repeat(4) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ForumColors.Surface,
                border = BorderStroke(1.dp, ForumColors.Divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(Modifier.size(38.dp), shape = CircleShape, brush = brush)
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ShimmerBox(Modifier.width(96.dp).height(14.dp), brush = brush)
                            ShimmerBox(Modifier.width(120.dp).height(11.dp), brush = brush)
                        }
                    }
                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp), brush = brush)
                    ShimmerBox(Modifier.fillMaxWidth(0.88f).height(14.dp), brush = brush)
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp), brush = brush)
                }
            }
        }
    }
}
