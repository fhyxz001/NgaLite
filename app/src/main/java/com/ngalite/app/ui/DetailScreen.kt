package com.ngalite.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
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
            val sorted = it.posts.sortedByDescending { p -> p.likes.toIntOrNull() ?: 0 }.take(10)
            ExportManager.ExportContent(it.title, sorted)
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
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is DetailUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.load(tid) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重试")
                }
            }

            is DetailUiState.Success -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
            ) {
                items(s.posts) { post ->
                    PostItem(post)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                post.author,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                post.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            // 点赞数
            if (post.likes != "0") {
                Text(
                    "赞 ${post.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // 渲染正文内容节点
        PostContent(post.contentNodes)
    }
}

/**
 * 渲染正文内容节点：连续的文本与表情合并为内联富文本，图片/引用单独成块。
 */
@Composable
private fun PostContent(nodes: List<ContentNode>) {
    var i = 0
    while (i < nodes.size) {
        val node = nodes[i]
        if (node is ContentNode.Text || node is ContentNode.Emoji) {
            // 收集连续的文本和表情节点，一起渲染为内联富文本
            val group = mutableListOf<ContentNode>()
            while (i < nodes.size && (nodes[i] is ContentNode.Text || nodes[i] is ContentNode.Emoji)) {
                group.add(nodes[i])
                i++
            }
            InlineRichText(group)
        } else if (node is ContentNode.Image) {
            var showFull by remember { mutableStateOf(false) }
            AsyncImage(
                model = node.url,
                contentDescription = "图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFull = !showFull },
                contentScale = if (showFull) ContentScale.Fit else ContentScale.FillWidth
            )
            i++
        } else if (node is ContentNode.Quote) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // 左侧强调色竖条
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "引用",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        node.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            i++
        } else {
            i++
        }
    }
}

/**
 * 将连续的文本和表情节点渲染为内联富文本，表情图片从 assets 加载。
 */
@Composable
private fun InlineRichText(nodes: List<ContentNode>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val emojiCache = remember { mutableMapOf<String, Boolean>() }

    // 收集表情信息：(id, folder, name)
    val slots = mutableListOf<Triple<String, String, String>>()
    var counter = 0

    val annotated = buildAnnotatedString {
        nodes.forEach { node ->
            when (node) {
                is ContentNode.Text -> append(node.text)
                is ContentNode.Emoji -> {
                    val id = "emoji_${counter++}"
                    slots.add(Triple(id, node.folder, node.name))
                    appendInlineContent(id, "[${node.name}]")
                }
                else -> {}
            }
        }
    }

    // 过滤纯空白内容
    val hasContent = nodes.any { node ->
        when (node) {
            is ContentNode.Text -> node.text.isNotBlank()
            is ContentNode.Emoji -> true
            else -> false
        }
    }
    if (!hasContent) return

    // 构建内联表情映射，检查 assets 中是否存在对应图片
    val inlineContent = slots.associate { (id, folder, name) ->
        val key = "$folder/$name"
        val exists = emojiCache.getOrPut(key) {
            try {
                context.assets.open("$key.png").close()
                true
            } catch (e: Exception) {
                false
            }
        }
        id to InlineTextContent(
            Placeholder(
                width = 22.sp,
                height = 22.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            if (exists) {
                AsyncImage(
                    model = "file:///android_asset/$key.png",
                    contentDescription = name,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    "[s:$folder:$name]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent,
        modifier = Modifier.padding(top = 10.dp)
    )
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
