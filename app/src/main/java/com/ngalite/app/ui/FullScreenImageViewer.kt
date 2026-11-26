package com.ngalite.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.ngalite.app.data.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 现代全屏图片查看器：
 * - 支持左右滑动切换（1x 缩放时无缝交给 HorizontalPager）
 * - 支持双指捏合缩放、双击放大/还原、放大后单指拖拽
 * - 顶部悬浮关闭与页码指示，底部一键保存至相册
 */
@Composable
fun FullScreenImageViewer(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex)
    ) { images.size }
    var isSaving by remember { mutableStateOf(false) }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingSaveUrl
        pendingSaveUrl = null
        if (granted && url != null) {
            scope.launch { saveImageToGallery(context, url) { isSaving = it } }
        } else {
            toast(context, "需要存储权限才能保存图片")
        }
    }

    fun requestSave(url: String) {
        if (isSaving) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch { saveImageToGallery(context, url) { isSaving = it } }
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                scope.launch { saveImageToGallery(context, url) { isSaving = it } }
            } else {
                pendingSaveUrl = url
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImagePage(
                    imageUrl = images[page],
                    contentDescription = "图片 ${page + 1}/${images.size}",
                    onSingleTap = onDismiss
                )
            }

            // 顶部透明 HUD：关闭按钮 + 页码胶囊
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (images.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                Spacer(Modifier.size(40.dp))
            }

            // 底部保存按钮胶囊
            Surface(
                onClick = { requestSave(images[pagerState.currentPage]) },
                enabled = !isSaving,
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在保存…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "保存图片",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "保存原图",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isSaving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    imageUrl: String,
    contentDescription: String,
    onSingleTap: () -> Unit
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(),
        label = "imgScale"
    )

    fun clampOffset(target: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || containerSize.width == 0 || containerSize.height == 0) {
            return Offset.Zero
        }
        val maxX = containerSize.width * (targetScale - 1f) / 2f
        val maxY = containerSize.height * (targetScale - 1f) / 2f
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onSingleTap()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.4f
                        }
                    }
                )
            }
            .pointerInput(imageUrl) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        // 仅当多指捏合或已处于放大状态时拦截事件，1x 单指滑动保留给 HorizontalPager 翻页
                        if (pointerCount > 1 || scale > 1.02f || zoomChange != 1f) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = newScale
                            offset = clampOffset(offset + panChange, newScale)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

private suspend fun saveImageToGallery(
    context: Context,
    url: String,
    onSavingChange: (Boolean) -> Unit
) {
    onSavingChange(true)
    try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context).data(url).build()
        val result = withContext(Dispatchers.IO) { loader.execute(request) }
        val drawable = result.drawable
        if (drawable == null) {
            toast(context, "无法获取图片")
            return
        }
        val bitmap = drawable.toBitmap()
        val name = "ngalite_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        val location = withContext(Dispatchers.IO) {
            ExportManager.saveBitmapToGallery(context, name, bitmap)
        }
        toast(context, "图片已保存到相册 $location")
    } catch (e: Exception) {
        toast(context, "保存失败: ${e.message}")
    } finally {
        onSavingChange(false)
    }
}
