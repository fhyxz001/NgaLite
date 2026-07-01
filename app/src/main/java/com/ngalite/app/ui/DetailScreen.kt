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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
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
    data class Success(
        val title: String,
        val forumName: String,
        val originalPost: Post?,
        val comments: List<Post>
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}

class DetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state

    private var currentTid: String = ""
    private var currentForumName: String = ""

    fun load(tid: String, forumName: String = "") {
        currentTid = tid
        currentForumName = forumName
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThread(tid) }
                val title = withContext(Dispatchers.Default) { NgaParser.parseThreadTitle(html) }
                val posts = withContext(Dispatchers.Default) { NgaParser.parsePosts(html) }
                val originalPost = posts.firstOrNull()
                val comments = if (posts.isNotEmpty()) posts.drop(1) else emptyList()
                _state.value = DetailUiState.Success(title, forumName, originalPost, comments)
            } catch (e: Exception) {
                _state.value = DetailUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    private fun postUrl(): String =
        if (currentTid.isBlank()) "" else "https://bbs.nga.cn/read.php?tid=$currentTid"

    private fun exportContent(): ExportManager.ExportContent? =
        (state.value as? DetailUiState.Success)?.let { success ->
            val allPosts = listOfNotNull(success.originalPost) + success.comments
            val sorted = allPosts.sortedByDescending { p -> p.likes.toIntOrNull() ?: 0 }.take(10)
            ExportManager.ExportContent(success.title, sorted, postUrl())
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
    forumName: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel()
) {
    LaunchedEffect(tid, forumName) { vm.load(tid, forumName) }
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }

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
                title = { Text("帖子详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    showTopMenu = false
                                    vm.load(tid, forumName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出 / 分享") },
                                enabled = state is DetailUiState.Success && !isExporting,
                                onClick = {
                                    showTopMenu = false
                                    showExportDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                TextButton(onClick = { vm.load(tid, forumName) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重试")
                }
            }

            is DetailUiState.Success -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (s.forumName.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                s.forumName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                item {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                s.originalPost?.let { post ->
                    item {
                        OriginalPostCard(post) { fullScreenImageUrl = it }
                    }
                }

                if (s.comments.isNotEmpty()) {
                    item {
                        Text(
                            "全部评论",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                    }
                }

                itemsIndexed(s.comments, key = { index, _ -> index }) { _, post ->
                    CommentCard(post) { fullScreenImageUrl = it }
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

    // 全屏图片查看
    fullScreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "放大图片",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun OriginalPostCard(post: Post, onImageClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            PostContent(post.contentNodes, onImageClick)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                "${post.date} · 楼主${if (post.views != "0") " · ${post.views} 浏览" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentCard(post: Post, onImageClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    post.floor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    post.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            PostContent(post.contentNodes, onImageClick)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${post.date}${if (post.views != "0") " · ${post.views} 浏览" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (post.likes != "0") {
                    Text(
                        "赞 ${post.likes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * 渲染正文内容节点：连续的文本与表情合并为内联富文本，图片/引用单独成块。
 */
@Composable
private fun PostContent(nodes: List<ContentNode>, onImageClick: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenDensity = density.density
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenWidthPx = remember(screenDensity, screenWidthDp) {
        (screenWidthDp * screenDensity).toInt()
    }
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
            // 记忆 ImageRequest，避免每次重组都重新构建
            val request = remember(node.url, screenWidthPx) {
                ImageRequest.Builder(context)
                    .data(node.url)
                    .size(Size(Dimension.Pixels(screenWidthPx), Dimension.Undefined))
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onImageClick(node.url) },
                contentScale = ContentScale.FillWidth
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
 * 使用 FlowRow 实现文本与表情图片的行内混排。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineRichText(nodes: List<ContentNode>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val emojiCache = remember { mutableMapOf<String, Boolean>() }

    // 过滤纯空白内容
    val hasContent = nodes.any { node ->
        when (node) {
            is ContentNode.Text -> node.text.isNotBlank()
            is ContentNode.Emoji -> true
            else -> false
        }
    }
    if (!hasContent) return

    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.padding(top = 10.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        nodes.forEach { node ->
            when (node) {
                is ContentNode.Text -> {
                    if (node.text.isNotBlank()) {
                        Text(
                            node.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is ContentNode.Emoji -> {
                    val key = "${node.folder}/${node.name}"
                    val exists = emojiCache.getOrPut(key) {
                        try {
                            context.assets.open("$key.png").close()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    if (exists) {
                        AsyncImage(
                            model = "file:///android_asset/$key.png",
                            contentDescription = node.name,
                            modifier = Modifier
                                .size(34.dp)
                                .padding(horizontal = 1.dp),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        )
                    } else {
                        Text(
                            "[s:${node.folder}:${node.name}]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                else -> {}
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
