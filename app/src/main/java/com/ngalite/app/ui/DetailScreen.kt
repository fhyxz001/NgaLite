package com.ngalite.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.ngalite.app.NgaApp
import com.ngalite.app.data.BaseConfig
import com.ngalite.app.data.ContentNode
import com.ngalite.app.data.CookieStore
import com.ngalite.app.data.ExportManager
import com.ngalite.app.data.HtmlShareConfig
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Post
import com.ngalite.app.data.UserBrief
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    /** 当前加载协程，新加载前取消上一个，避免并发请求浪费带宽 */
    private var loadJob: kotlinx.coroutines.Job? = null

    /** 楼层作者名回查协程 + 并发上限（新版页面不在 HTML 里渲染作者名） */
    private var nameJob: kotlinx.coroutines.Job? = null
    private val nameSemaphore = Semaphore(4)

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
        loadJob?.cancel()
        val prevSuccess = _state.value as? DetailUiState.Success
        val prevSignature = firstPostSignature
        val prevPage = currentPage
        currentPage = page
        loadJob = viewModelScope.launch {
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
                // 正文先展示，作者名/头像缺失时再按 uid 回查（不阻塞阅读）
                resolveAuthorInfo(myGen)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (myGen != loadGeneration) return@launch
                _state.value = DetailUiState.Error(t.message ?: "未知错误")
            }
        }
    }

    /**
     * 楼层作者信息回查：新版 NGA 的 read.php 只在 HTML 里给出作者 uid（`#postauthorN` 锚点为空，
     * 用户名与头像由页面脚本填充），因此解析不到名字或头像时按 uid 调接口补全，拿到后原地刷新状态。
     */
    private fun resolveAuthorInfo(generation: Long) {
        val snapshot = _state.value as? DetailUiState.Success ?: return
        val uids = (listOfNotNull(snapshot.originalPost) + snapshot.comments)
            .filter { it.uid.isNotBlank() && (it.author.isBlank() || it.avatarUrl.isBlank()) }
            .map { it.uid }
            .distinct()
        if (uids.isEmpty()) return

        nameJob?.cancel()
        nameJob = viewModelScope.launch {
            val resolved: Map<String, UserBrief> = withContext(Dispatchers.IO) {
                uids.map { uid ->
                    async {
                        nameSemaphore.withPermit {
                            uid to runCatching { NgaApi.fetchUserBrief(uid) }.getOrNull()
                        }
                    }
                }.awaitAll()
                    .mapNotNull { (uid, brief) -> brief?.let { uid to it } }
                    .toMap()
            }
            if (resolved.isEmpty() || generation != loadGeneration) return@launch
            val current = _state.value as? DetailUiState.Success ?: return@launch
            fun withInfo(post: Post): Post {
                val brief = resolved[post.uid] ?: return post
                var updated = post
                if (updated.author.isBlank() && brief.name.isNotBlank()) {
                    updated = updated.copy(author = brief.name)
                }
                if (updated.avatarUrl.isBlank() && brief.avatar.isNotBlank()) {
                    val url = NgaParser.avatarUrl(brief.avatar, post.uid)
                    if (url.isNotBlank()) updated = updated.copy(avatarUrl = url)
                }
                return updated
            }
            _state.value = current.copy(
                originalPost = current.originalPost?.let { withInfo(it) },
                comments = current.comments.map { withInfo(it) }
            )
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
        if (currentTid.isBlank()) "" else "${BaseConfig.baseUrl}/read.php?tid=$currentTid"

    private fun exportContent(): ExportManager.ExportContent? =
        (state.value as? DetailUiState.Success)?.let { success ->
            val allPosts = listOfNotNull(success.originalPost) + success.comments
            ExportManager.ExportContent(success.title, allPosts, postUrl())
        }

    /** 复制纯文本到剪贴板（不再使用 Markdown 格式） */
    fun exportPlainText(context: Context): String? {
        val content = exportContent() ?: return null
        val text = ExportManager.convertToPlainText(content)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NGA帖子", text))
        return text
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

    /** 上传 HTML 生成分享链接，成功后自动复制链接到剪贴板 */
    fun exportShareLink(
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
                // 按设置页选择的分享服务生成链接：配置1 pad / 配置2 htmlto.link
                val link = when (HtmlShareConfig.provider()) {
                    HtmlShareConfig.Provider.PAD -> {
                        val html = ExportManager.buildExportHtml(context, content, includeAttribution)
                        val inlined = ExportManager.inlineImagesInHtml(html, cookie)
                        ExportManager.uploadHtmlToPad(inlined)
                    }
                    HtmlShareConfig.Provider.HTMLTO -> {
                        val markdown = ExportManager.convertToMarkdown(content)
                        ExportManager.uploadMarkdownToHtmltoLink(content.title, markdown)
                    }
                }
                // 分享文本带帖子标题，便于接收方直接知道内容
                val shareText = if (content.title.isBlank()) link else "${content.title} $link"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NGA帖子链接", shareText))
                onResult(true, shareText)
            } catch (e: Exception) {
                onResult(false, "分享失败: ${e.message}")
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
        .url("${BaseConfig.baseUrl}/read.php?tid=$tid&page=$page")
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
    val loadedPage = (state as? DetailUiState.Success)?.currentPage ?: 0
    LaunchedEffect(loadedPage) {
        listState.scrollToItem(0)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var shareText by remember { mutableStateOf<String?>(null) }
    var fullScreenState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

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
        containerColor = ForumColors.Page,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        val topSpacing = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
        when (val s = state) {
            is DetailUiState.Loading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(ForumColors.Page),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForumColors.Meta
                )
            }

            is DetailUiState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = topSpacing)
                    .background(ForumColors.Page),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.message,
                    color = ForumColors.Meta,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
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
                    .padding(padding)
                    .background(ForumColors.Page),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = topSpacing, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题栏 + 统计信息
                item {
                    DetailHeader(
                        title = s.title,
                        forumName = s.forumName,
                        originalPost = s.originalPost,
                        comments = s.comments,
                        currentPage = s.currentPage,
                        onBack = onBack,
                        onShare = { showExportDialog = true }
                    )
                }

                // 主楼
                s.originalPost?.let { post ->
                    item {
                        FloorCard(
                            post = post,
                            isOwner = true,
                            onImageClick = { images, index -> fullScreenState = images to index }
                        )
                    }
                }

                // 回复区标题
                if (s.comments.isNotEmpty()) {
                    item {
                        SectionBar(
                            text = if (s.currentPage == 1) "全部回复" else "第 ${s.currentPage} 页",
                            trailing = "${s.comments.size} 条"
                        )
                    }
                }

                // 回复列表（key 不含作者名：用户名回查后原地刷新，避免楼层被重建）
                itemsIndexed(s.comments, key = { index, post -> "${s.currentPage}-$index-${post.floor}" }) { _, post ->
                    FloorCard(
                        post = post,
                        isOwner = false,
                        onImageClick = { images, index -> fullScreenState = images to index }
                    )
                }

                // 分页栏
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

                // 底部结束标记
                if (!s.hasMore && s.comments.isNotEmpty() && !s.isPageLoading) {
                    item { EndMarker() }
                }
            }
        }

        // Loading / Error 状态下的返回按钮
        if (state !is DetailUiState.Success) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(padding)
                    .padding(top = topSpacing, start = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { if (!isExporting) showExportDialog = false },
            isExporting = isExporting,
            onCopyText = { includeAttribution ->
                if (vm.exportPlainText(context) != null) {
                    toast("纯文本已复制到剪贴板")
                    showExportDialog = false
                }
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
            onExportShareLink = { includeAttribution ->
                isExporting = true
                vm.exportShareLink(context, includeAttribution) { success, msg ->
                    isExporting = false
                    if (success) {
                        shareText = msg
                        showExportDialog = false
                    } else {
                        toast(msg)
                    }
                }
            }
        )
    }

    // 分享链接成功弹窗
    shareText?.let { text ->
        ShareLinkDialog(shareText = text, onDismiss = { shareText = null })
    }

    // 全屏图片查看
    fullScreenState?.let { (images, index) ->
        FullScreenImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { fullScreenState = null }
        )
    }
}

/**
 * 论坛风格标题栏：白色区块 + 返回/板块/分享工具行 + 标题 + 统计信息
 */
@Composable
private fun DetailHeader(
    title: String,
    forumName: String,
    originalPost: Post?,
    comments: List<Post>,
    currentPage: Int,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val totalPosts = (if (originalPost != null) 1 else 0) + comments.size
    val views = originalPost?.views?.toIntOrNull() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ForumColors.Surface)
    ) {
        // 工具行：返回 + 板块名 + 分享
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp)
                )
            }
            if (forumName.isNotBlank()) {
                Text(
                    forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = ForumColors.Link,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "分享",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 标题
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        // 统计信息
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatChip("$totalPosts 层")
            StatChip("${comments.size} 回复")
            if (views > 0) StatChip("$views 浏览")
            if (currentPage > 1) StatChip("第 $currentPage 页")
        }
    }
}

/** 统计小标签：浅底 + 次要文字，论坛风格的计数条 */
@Composable
private fun StatChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(ForumColors.QuoteBg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = ForumColors.Meta,
            maxLines = 1
        )
    }
}

/** 区块标题条：左侧蓝色竖条 + 标题 + 右侧计数 */
@Composable
private fun SectionBar(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(ForumColors.Accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = ForumColors.Meta
            )
        }
    }
}

/**
 * 楼层作者显示名：真实用户名 > UID 占位 > 匿名。
 * 用户名回查期间先显示 UID 占位，拿到后由 ViewModel 刷新状态替换为真实用户名。
 */
private fun displayAuthor(post: Post): String = post.author.ifBlank {
    if (post.uid.isNotBlank()) "UID:${post.uid}" else "匿名"
}

/**
 * 论坛楼层卡片：作者 + 楼主徽标 + 楼层号，发丝线下方是正文
 */
@Composable
private fun FloorCard(
    post: Post,
    isOwner: Boolean,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ForumColors.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 楼层信息行：头像 + 作者 + 楼主徽标 …… 楼层号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            FloorAvatar(
                url = post.avatarUrl,
                name = displayAuthor(post),
                size = 38.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            displayAuthor(post),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ForumColors.Link,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOwner) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ForumColors.OwnerBg)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "楼主",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForumColors.OwnerText
                                )
                            }
                        }
                    }
                    Text(
                        when {
                            post.floor.isBlank() -> ""
                            post.floor.startsWith("#") -> post.floor
                            else -> "#${post.floor}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ForumColors.Floor,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(3.dp))

                // 时间 + 浏览数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = ForumColors.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val viewCount = post.views.toIntOrNull() ?: 0
                    if (viewCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "浏览 $viewCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Floor
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = ForumColors.Divider,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )

        // 正文
        PostContent(post.contentNodes, onImageClick)

        // 底部互动栏：点赞数（NGA 的"推荐值"），为 0 时不占位
        val likeCount = post.likes.toIntOrNull() ?: 0
        if (likeCount > 0) {
            HorizontalDivider(
                color = ForumColors.Divider,
                thickness = 1.dp,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    tint = ForumColors.OwnerText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    likeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ForumColors.OwnerText
                )
            }
        }
    }
}

/**
 * 楼层头像：圆角方形；没有头像时用用户名首字占位，保证布局稳定。
 */
@Composable
private fun FloorAvatar(url: String, name: String, size: Dp) {
    val shape = RoundedCornerShape(4.dp)
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(ForumColors.QuoteBg),
            contentScale = ContentScale.Crop,
            error = ColorPainter(ForumColors.QuoteBg)
        )
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(ForumColors.QuoteBg)
            .border(1.dp, ForumColors.Divider, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).ifBlank { "?" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ForumColors.Meta
        )
    }
}

/**
 * 论坛风格分页栏：上一页 / 第 N 页 / 下一页
 */
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        PagerButton(label = "上一页", enabled = hasPrev && !isLoading, onClick = onPrev)

        Spacer(Modifier.width(14.dp))

        // 页码 / 加载状态
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "加载中…",
                style = MaterialTheme.typography.labelSmall,
                color = ForumColors.Meta
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(ForumColors.Surface)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    "第 $currentPage 页",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        PagerButton(label = "下一页", enabled = hasNext && !isLoading, onClick = onNext)
    }
}

/** 分页按钮：可用时为白底蓝字，不可用时为浅灰底 */
@Composable
private fun PagerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (enabled) ForumColors.Surface else ForumColors.QuoteBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) ForumColors.Link else ForumColors.Floor
        )
    }
}

/**
 * 底部结束标记
 */
@Composable
private fun EndMarker() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.width(32.dp),
            color = ForumColors.Divider
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "本页没有更多楼层",
            style = MaterialTheme.typography.labelSmall,
            color = ForumColors.Floor
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.width(32.dp),
            color = ForumColors.Divider
        )
    }
}

/**
 * 渲染正文内容节点：连续的文本与表情合并为内联富文本，图片/引用单独成块。
 */
@Composable
private fun PostContent(nodes: List<ContentNode>, onImageClick: (List<String>, Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenDensity = density.density
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenWidthPx = remember(screenDensity, screenWidthDp) {
        (screenWidthDp * screenDensity).toInt()
    }

    // 收集当前帖子所有图片 URL，用于全屏查看时左右滑动切换
    val allImages = remember(nodes) {
        nodes.filterIsInstance<ContentNode.Image>().map { it.url }
    }

    // 缓存节点分组结果，避免每次重组都重新遍历
    val groupedNodes = remember(nodes) { groupContentNodes(nodes) }

    var imgIdx = 0
    groupedNodes.forEachIndexed { index, group ->
        // 使用 key 防止 Compose 位置记忆化在节点类型变化时错配状态
        key(index, group::class) {
            when (group) {
                is NodeGroup.Inline -> {
                    InlineRichText(group.nodes)
                }
                is NodeGroup.Image -> {
                    val currentImageIndex = imgIdx
                    imgIdx++
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
                            .clip(RoundedCornerShape(3.dp))
                            .background(ForumColors.QuoteBg)
                            .clickable { onImageClick(allImages, currentImageIndex) },
                        contentScale = ContentScale.FillWidth
                    )
                }
                is NodeGroup.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            .padding(top = 10.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ForumColors.QuoteBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(ForumColors.QuoteBar)
                        )
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                "引用",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForumColors.Meta,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                group.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
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
 * 使用 AnnotatedString + inlineContent 让表情与文字垂直居中对齐（TextCenter）。
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun InlineRichText(nodes: List<ContentNode>) {
    val emojiExists = emojiExistsCache
    // 表情占位符高度不能超过行高，否则会溢出行框与上下行文字重叠。
    // 尺寸取 34.sp（原 34.dp），并给含表情的段落显式设 lineHeight 恰好容纳它
    val emojiSize = 34.sp
    val hasEmoji = nodes.any { it is ContentNode.Emoji }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val textStyle = if (hasEmoji) baseStyle.copy(lineHeight = emojiSize) else baseStyle

    val hasContent = nodes.any { node ->
        when (node) {
            is ContentNode.Text -> node.text.isNotBlank()
            is ContentNode.Emoji -> true
            else -> false
        }
    }
    if (!hasContent) return

    val inlineContent = remember(nodes) {
        buildMap<String, InlineTextContent> {
            nodes.forEachIndexed { index, node ->
                if (node is ContentNode.Emoji) {
                    val key = "${node.folder}/${node.name}"
                    if (key in emojiExists) {
                        put(
                            "emoji-$index",
                            InlineTextContent(
                                Placeholder(
                                    width = emojiSize,
                                    height = emojiSize,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                )
                            ) {
                                AsyncImage(
                                    model = "file:///android_asset/$key.png",
                                    contentDescription = node.name,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    Text(
        text = buildAnnotatedString {
            nodes.forEachIndexed { index, node ->
                when (node) {
                    is ContentNode.Text -> {
                        if (node.text.isNotBlank()) append(node.text)
                    }
                    is ContentNode.Emoji -> {
                        val key = "${node.folder}/${node.name}"
                        if (key in emojiExists) {
                            appendInlineContent("emoji-$index", "[${node.name}]")
                        } else {
                            append("[s:${node.folder}:${node.name}]")
                        }
                    }
                    else -> {}
                }
            }
        },
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    isExporting: Boolean,
    onCopyText: (includeAttribution: Boolean) -> Unit,
    onExportHtml: (includeAttribution: Boolean) -> Unit,
    onExportImage: (includeAttribution: Boolean) -> Unit,
    onExportShareLink: (includeAttribution: Boolean) -> Unit,
) {
    var includeAttribution by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享") },
        text = {
            Column {
                Text(
                    "选择分享方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                ExportOption(Icons.Default.Link, "分享链接", "上传生成可访问链接") {
                    onExportShareLink(includeAttribution)
                }
                ExportOption(Icons.Default.ContentCopy, "复制文本", "将帖子正文复制为纯文本") {
                    onCopyText(includeAttribution)
                }
                ExportOption(Icons.Default.Html, "HTML", "保存为 HTML 文件到下载目录") {
                    onExportHtml(includeAttribution)
                }
                ExportOption(Icons.Default.Image, "图片", "渲染为长图并保存到相册") {
                    onExportImage(includeAttribution)
                }

                Spacer(Modifier.height(8.dp))
                Row(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分享链接生成成功弹窗：标题+链接已自动复制到剪贴板，可再次复制或关闭 */
@Composable
private fun ShareLinkDialog(shareText: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享链接已生成") },
        text = {
            Column {
                Text(
                    "已复制到剪贴板：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    shareText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NGA帖子链接", shareText))
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
