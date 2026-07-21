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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.ngalite.app.NgaApp
import com.ngalite.app.data.ContentNode
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.ExportManager
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Post
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val title: String,
        val forumName: String,
        val originalPost: Post?,
        val comments: List<Post>,
        /** 当前页码（NGA read.php 按 20 条/页分页） */
        val currentPage: Int = 1,
        /** 是否可能有下一页：当前页解析满 20 条时为 true，翻页后若内容重复/为空则回退并置 false */
        val hasMore: Boolean = false,
        /** 翻页加载中：保留当前页内容，仅分页栏显示加载状态 */
        val isPageLoading: Boolean = false
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}

/**
 * 全局缓存 emoji 资源存在性集合，避免每个 [InlineRichText] 实例各自扫描 assets。
 * 在首次访问时通过 [Lazy] 初始化，后续所有 Composable 共享同一份。
 */
private val emojiExistsCache: Map<String, Boolean> by lazy {
    val result = mutableMapOf<String, Boolean>()
    val ctx = NgaApp.instance
    for (folder in listOf("ac", "a2", "ng", "pst", "dt", "pg")) {
        try {
            val files = ctx.assets.list(folder) ?: continue
            files.filter { it.endsWith(".png") }.forEach { file ->
                result["$folder/${file.removeSuffix(".png")}"] = true
            }
        } catch (_: Exception) { }
    }
    result
}

class DetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state

    private var currentTid: String = ""
    private var currentForumName: String = ""

    /** 当前页码（NGA read.php 按 20 条/页分页） */
    private var currentPage = 1

    /** 当前页首条帖子的楼层签名，用于识别翻过最后一页时 NGA 返回的重复内容 */
    private var firstPostSignature = ""

    /** 代次计数器：每次 load() 自增，用于丢弃已取消协程的结果 */
    private var loadGeneration = 0L

    fun load(tid: String, forumName: String = "") {
        currentTid = tid
        currentForumName = forumName
        currentPage = 1
        firstPostSignature = ""
        loadPage(1)
    }

    /** 重试：重新加载当前页（加载失败后停留在原页码） */
    fun retry() = loadPage(currentPage)

    /** 下一页：仅在智能判断可能有下一页时可触发 */
    fun loadNextPage() {
        val s = _state.value as? DetailUiState.Success ?: return
        if (!s.hasMore || s.isPageLoading) return
        loadPage(currentPage + 1)
    }

    /** 上一页 */
    fun loadPrevPage() {
        val s = _state.value as? DetailUiState.Success ?: return
        if (currentPage <= 1 || s.isPageLoading) return
        loadPage(currentPage - 1)
    }

    private fun loadPage(page: Int) {
        val myGen = ++loadGeneration
        val prevSuccess = _state.value as? DetailUiState.Success
        val prevSignature = firstPostSignature
        val prevPage = currentPage
        currentPage = page
        viewModelScope.launch {
            // 翻页时保留已有内容、仅分页栏显示加载中；首载/无内容时才显示全屏 Loading
            _state.value = prevSuccess?.copy(isPageLoading = true) ?: DetailUiState.Loading
            try {
                val html = withContext(Dispatchers.IO) { fetchThreadPage(currentTid, page) }
                if (myGen != loadGeneration) return@launch
                val result = withContext(Dispatchers.Default) { NgaParser.parseDetail(html) }
                if (myGen != loadGeneration) return@launch

                // 智能分页判断：翻过最后一页时 NGA 会返回空内容或重复上一页的内容，
                // 此时回退到上一页并标记没有下一页，避免"下一页"被无限点击
                if (page > prevPage && prevSuccess != null &&
                    (result.posts.isEmpty() || signatureOf(result.posts) == prevSignature)
                ) {
                    currentPage = prevPage
                    _state.value = prevSuccess.copy(hasMore = false, isPageLoading = false)
                    return@launch
                }

                if (result.posts.isEmpty()) {
                    _state.value = DetailUiState.Error("未能解析帖子内容，请稍后重试")
                    return@launch
                }

                firstPostSignature = signatureOf(result.posts)
                val originalPost = if (page == 1) result.posts.firstOrNull() else null
                val comments = if (page == 1) result.posts.drop(1) else result.posts
                _state.value = DetailUiState.Success(
                    result.title,
                    currentForumName,
                    originalPost,
                    comments,
                    currentPage = page,
                    hasMore = result.posts.size >= PAGE_SIZE
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (myGen != loadGeneration) return@launch
                _state.value = DetailUiState.Error(t.message ?: "未知错误")
            }
        }
    }

    /** 首条帖子的楼层签名（楼层+作者+时间），用于跨页识别重复内容 */
    private fun signatureOf(posts: List<Post>): String {
        val p = posts.firstOrNull() ?: return ""
        return "${p.floor}|${p.author}|${p.date}"
    }

    companion object {
        /** NGA read.php 每页固定条数，用于智能判断是否还有下一页 */
        private const val PAGE_SIZE = 20
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

/** 详情页分页专用 client：复用 [NgaApi] 的共享连接池与超时配置 */
private val detailPageClient by lazy { NgaApi.sharedClientBuilder().build() }

/**
 * 抓取帖子指定页的 HTML（NGA read.php 按 20 条/页分页）。
 *
 * 第 1 页复用 [NgaApi.fetchThread]，保持原有 CookieJar 行为不变；
 * 第 2 页及以后手动携带 [CookieStore] 中的登录 Cookie。
 */
private fun fetchThreadPage(tid: String, page: Int): String {
    if (page <= 1) return NgaApi.fetchThread(tid)
    val builder = Request.Builder()
        .url("https://bbs.nga.cn/read.php?tid=$tid&page=$page")
        .header("User-Agent", NgaApi.UA)
        .header("Accept-Charset", "GBK")
    val cookie = CookieStore.get()
    if (cookie.isNotBlank()) builder.header("Cookie", cookie)
    detailPageClient.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
        val bytes = resp.body?.bytes() ?: return ""
        return String(bytes, Charset.forName("GBK"))
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
    val listState = rememberLazyListState()
    // 翻页成功后滚动到顶部，从新楼层的第一条开始阅读
    val loadedPage = (state as? DetailUiState.Success)?.currentPage ?: 0
    LaunchedEffect(loadedPage) {
        listState.scrollToItem(0)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

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
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        val topSpacing = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
        when (val s = state) {
            is DetailUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is DetailUiState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(MaterialTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.retry() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重试")
                }
            }

            is DetailUiState.Success -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topSpacing + 12.dp, bottom = 12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                        Text(
                            s.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { showExportDialog = true },
                            enabled = state is DetailUiState.Success && !isExporting
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
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

                itemsIndexed(s.comments, key = { index, post -> "$index-${post.floor}-${post.author}" }) { _, post ->
                    CommentCard(post) { fullScreenImageUrl = it }
                }

                // 底部分页栏：有评论或已翻到第 2 页及以后时显示
                if (s.comments.isNotEmpty() || s.currentPage > 1) {
                    item {
                        DetailPager(
                            currentPage = s.currentPage,
                            hasPrev = s.currentPage > 1,
                            hasNext = s.hasMore,
                            isLoading = s.isPageLoading,
                            onPrev = vm::loadPrevPage,
                            onNext = vm::loadNextPage
                        )
                    }
                }
            }
        }

        if (state !is DetailUiState.Success) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(padding)
                    .padding(top = topSpacing, start = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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

/** 详情页底部分页栏：按 20 条/页智能判断，最后一页不再显示"下一页" */
@Composable
private fun DetailPager(
    currentPage: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    isLoading: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onPrev, enabled = hasPrev && !isLoading) {
            Text("上一页")
        }
        Spacer(Modifier.width(8.dp))
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "第 $currentPage 页",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (hasNext) {
            TextButton(onClick = onNext, enabled = !isLoading) {
                Text("下一页")
            }
        } else {
            Text(
                "没有更多了",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
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

    // 缓存节点分组结果，避免每次重组都重新遍历
    val groupedNodes = remember(nodes) { groupContentNodes(nodes) }

    groupedNodes.forEachIndexed { index, group ->
        // 使用 key 防止 Compose 位置记忆化在节点类型变化时错配状态
        key(index, group::class) {
            when (group) {
                is NodeGroup.Inline -> {
                    InlineRichText(group.nodes)
                }
                is NodeGroup.Image -> {
                    val request = remember(group.url, screenWidthPx) {
                        ImageRequest.Builder(context)
                            .data(group.url)
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
                            .clickable { onImageClick(group.url) },
                        contentScale = ContentScale.FillWidth
                    )
                }
                is NodeGroup.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            .padding(top = 12.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
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
                                group.content,
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
}

/** 节点分组结果，用于缓存 PostContent 的遍历 */
private sealed class NodeGroup {
    class Inline(val nodes: List<ContentNode>) : NodeGroup()
    data class Image(val url: String) : NodeGroup()
    data class Quote(val content: String) : NodeGroup()
}

/** 将内容节点列表分组：连续的文本/表情合并为 Inline，图片和引用各自独立 */
private fun groupContentNodes(nodes: List<ContentNode>): List<NodeGroup> {
    if (nodes.isEmpty()) return emptyList()
    val groups = mutableListOf<NodeGroup>()
    var i = 0
    while (i < nodes.size) {
        val node = nodes[i]
        if (node is ContentNode.Text || node is ContentNode.Emoji) {
            val group = mutableListOf<ContentNode>()
            while (i < nodes.size && (nodes[i] is ContentNode.Text || nodes[i] is ContentNode.Emoji)) {
                group.add(nodes[i])
                i++
            }
            groups.add(NodeGroup.Inline(group))
        } else if (node is ContentNode.Image) {
            groups.add(NodeGroup.Image(node.url))
            i++
        } else if (node is ContentNode.Quote) {
            groups.add(NodeGroup.Quote(node.content))
            i++
        } else {
            i++
        }
    }
    return groups
}

/**
 * 将连续的文本和表情节点渲染为内联富文本，表情图片从 assets 加载。
 * 使用 FlowRow 实现文本与表情图片的行内混排。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineRichText(nodes: List<ContentNode>) {
    val emojiExists = emojiExistsCache

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
        nodes.forEachIndexed { index, node ->
            // 使用 key 防止 Compose 位置记忆化在节点类型变化时错配状态
            key(index, node::class) {
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
                        if (key in emojiExists) {
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
