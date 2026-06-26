package com.ngalite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ngalite.app.data.ContentNode
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.ExportManager
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val title: String, val posts: List<Post>) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class DetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state

    fun load(tid: String) {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThread(tid) }
                val title = withContext(Dispatchers.Default) { NgaParser.parseThreadTitle(html) }
                val posts = withContext(Dispatchers.Default) { NgaParser.parsePosts(html) }
                _state.value = DetailUiState.Success(title, posts)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    private fun exportContent(): ExportManager.ExportContent? =
        (state.value as? DetailUiState.Success)?.let {
            ExportManager.ExportContent(it.title, it.posts)
        }

    /** 复制 Markdown 到剪贴板 */
    fun exportMarkdown(context: Context): String? {
        val content = exportContent() ?: return null
        val markdown = ExportManager.convertToMarkdown(content)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NGA帖子", markdown))
        return markdown
    }

    /** 导出 HTML 到下载目录 */
    fun exportHtml(
        context: Context,
        includeAttribution: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        val content = exportContent()
        if (content == null) {
            onResult(false, "内容未加载完成")
            return
        }
        viewModelScope.launch {
            try {
                val cookie = CookieStore.get()
                val html = ExportManager.buildExportHtml(context, content, includeAttribution)
                val inlined = ExportManager.inlineImagesInHtml(html, cookie)
                val name = ExportManager.buildFileName(content.title, "html")
                val location = withContext(Dispatchers.IO) {
                    ExportManager.saveHtmlToDownloads(context, name, inlined)
                }
                onResult(true, "HTML 已保存到 $location")
            } catch (e: Exception) {
                onResult(false, "HTML 导出失败: ${e.message}")
            }
        }
    }

    /** 导出图片到相册（按手机屏幕宽度渲染） */
    fun exportImage(
        context: Context,
        includeAttribution: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        val content = exportContent()
        if (content == null) {
            onResult(false, "内容未加载完成")
            return
        }
        viewModelScope.launch {
            try {
                val cookie = CookieStore.get()
                val html = ExportManager.buildExportHtml(context, content, includeAttribution)
                val inlined = ExportManager.inlineImagesInHtml(html, cookie)
                val bitmap = ExportManager.renderHtmlToBitmap(context, inlined)
                try {
                    val name = ExportManager.buildFileName(content.title, "jpg")
                    val location = withContext(Dispatchers.IO) {
                        ExportManager.saveBitmapToGallery(context, name, bitmap)
                    }
                    onResult(true, "图片已保存到相册 $location")
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                onResult(false, "图片导出失败: ${e.message}")
            }
        }
    }

    /** 通过系统打印对话框导出 PDF */
    fun exportPdf(context: Context, includeAttribution: Boolean): String? {
        val content = exportContent() ?: return null
        viewModelScope.launch {
            try {
                val cookie = CookieStore.get()
                val html = ExportManager.buildExportHtml(context, content, includeAttribution)
                val inlined = ExportManager.inlineImagesInHtml(html, cookie)
                val jobName = ExportManager.buildFileName(content.title, "pdf")
                withContext(Dispatchers.Main) {
                    ExportManager.printPdf(context, jobName, inlined)
                }
            } catch (e: Exception) {
                // 打印框架已接管 UI，异常仅记录
            }
        }
        return content.title
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    tid: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel()
) {
    LaunchedEffect(tid) { vm.load(tid) }
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val writeStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(context, "需要存储权限才能导出", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureStoragePermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) return true
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) writeStorageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return granted
    }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? DetailUiState.Success)?.title ?: "帖子详情"
                    Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(tid) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = state is DetailUiState.Success && !isExporting
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "导出/分享")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            is DetailUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                IconButton(onClick = { vm.load(tid) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试")
                }
            }

            is DetailUiState.Success -> LazyColumn(
                Modifier.fillMaxSize().padding(padding)
            ) {
                items(s.posts) { post ->
                    PostItem(post)
                    HorizontalDivider()
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { if (!isExporting) showExportDialog = false },
            isExporting = isExporting,
            onExportMarkdown = { includeAttribution ->
                vm.exportMarkdown(context)
                toast("Markdown 已复制到剪贴板")
                showExportDialog = false
            },
            onExportHtml = { includeAttribution ->
                if (!ensureStoragePermission()) return@ExportDialog
                isExporting = true
                vm.exportHtml(context, includeAttribution) { success, msg ->
                    isExporting = false
                    toast(msg)
                    if (success) showExportDialog = false
                }
            },
            onExportImage = { includeAttribution ->
                if (!ensureStoragePermission()) return@ExportDialog
                isExporting = true
                vm.exportImage(context, includeAttribution) { success, msg ->
                    isExporting = false
                    toast(msg)
                    if (success) showExportDialog = false
                }
            },
            onExportPdf = { includeAttribution ->
                vm.exportPdf(context, includeAttribution)
                toast("已打开打印对话框，可选择保存为 PDF")
                showExportDialog = false
            }
        )
    }
}

@Composable
private fun PostItem(post: Post) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${post.floor} ${post.author}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                post.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // 渲染正文内容节点
        post.contentNodes.forEach { node ->
            when (node) {
                is ContentNode.Text -> {
                    if (node.text.isNotBlank()) {
                        Text(
                            node.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                is ContentNode.Image -> {
                    var showFull by remember { mutableStateOf(false) }
                    AsyncImage(
                        model = node.url,
                        contentDescription = "图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFull = !showFull },
                        contentScale = if (showFull) ContentScale.Fit else ContentScale.FillWidth
                    )
                }

                is ContentNode.Quote -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            "引用",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            node.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    isExporting: Boolean,
    onExportMarkdown: (includeAttribution: Boolean) -> Unit,
    onExportHtml: (includeAttribution: Boolean) -> Unit,
    onExportImage: (includeAttribution: Boolean) -> Unit,
    onExportPdf: (includeAttribution: Boolean) -> Unit,
) {
    var includeAttribution by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 / 分享") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "选择导出格式，导出内容已适配手机屏幕展示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                ExportOption(Icons.Default.Code, "Markdown", "复制为 Markdown 文本") {
                    onExportMarkdown(includeAttribution)
                }
                ExportOption(Icons.Default.Html, "HTML", "保存为 HTML 文件到下载目录") {
                    onExportHtml(includeAttribution)
                }
                ExportOption(Icons.Default.Image, "图片", "渲染为长图并保存到相册") {
                    onExportImage(includeAttribution)
                }
                ExportOption(Icons.Default.PictureAsPdf, "PDF", "通过系统打印对话框导出 PDF") {
                    onExportPdf(includeAttribution)
                }

                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "包含 NgaLite 署名",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = includeAttribution,
                        onCheckedChange = { includeAttribution = it },
                        enabled = !isExporting,
                    )
                }

                if (isExporting) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("正在导出…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) { Text("关闭") }
        },
    )
}

@Composable
private fun ExportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
