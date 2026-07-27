package com.ngalite.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
 * 全屏图片查看器：支持左右滑动切换、长按保存到相册、单击关闭。
 *
 * @param images 图片 URL 列表
 * @param initialIndex 初始展示的图片索引
 * @param onDismiss 关闭回调
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
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = images[page],
                    contentDescription = "图片 ${page + 1}/${images.size}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { onDismiss() },
                                onLongPress = { requestSave(images[page]) }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // 页码指示器：多张图片时显示当前页码
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // 保存中指示器
            if (isSaving) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/**
 * 通过 Coil 获取图片 Bitmap 并保存到相册。
 * 复用全局 ImageLoader 的内存/磁盘缓存，避免重复下载。
 */
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
        // toBitmap() 会处理 HARDWARE 配置的 bitmap，确保可被 compress 写入文件
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
