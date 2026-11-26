package com.ngalite.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.ngalite.app.data.NgaApi
import com.ngalite.app.data.NgaParser
import com.ngalite.app.data.Post
import com.ngalite.app.data.ThreadRecordsStore
import com.ngalite.app.data.UserBrief
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
import java.nio.charset.Charset
import kotlin.math.abs

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val title: String,
        val forumName: String,
        val originalPost: Post?,
        val comments: List<Post>,
        val opUid: String = "",
        val onlyOwner: Boolean = false,
        val currentPage: Int = 1,
        val hasMore: Boolean = false,
        val isPageLoading: Boolean = false,
        val isRefreshing: Boolean = false
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}

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
    private var currentPage = 1
    private var opUid: String = ""
    private var onlyOwnerMode: Boolean = false
    private var firstPostSignature = ""
    private var loadGeneration = 0L
    private var loadJob: kotlinx.coroutines.Job? = null
    private var nameJob: kotlinx.coroutines.Job? = null
    private val nameSemaphore = Semaphore(4)

    fun load(tid: String, forumName: String = "") {
        if (tid == currentTid && _state.value is DetailUiState.Success) return
        currentTid = tid
        currentForumName = forumName
        currentPage = 1
        opUid = ""
        onlyOwnerMode = false
        firstPostSignature = ""
        loadPage(1, isRefresh = false)
    }

    fun retry() = loadPage(currentPage, isRefresh = false)

    fun refresh() = loadPage(currentPage, isRefresh = true)

    fun toggleOnlyOwner() {
        val s = _state.value as? DetailUiState.Success ?: return
        onlyOwnerMode = !s.onlyOwner
        _state.value = s.copy(onlyOwner = onlyOwnerMode)
    }

    fun loadNextPage() {
        val s = _state.value as? DetailUiState.Success ?: return
        if (!s.hasMore || s.isPageLoading) return
        loadPage(currentPage + 1, isRefresh = false)
    }

    fun loadPrevPage() {
        val s = _state.value as? DetailUiState.Success ?: return
        if (currentPage <= 1 || s.isPageLoading) return
        loadPage(currentPage - 1, isRefresh = false)
    }

    fun jumpToPage(page: Int) {
        val target = page.coerceAtLeast(1)
        val s = _state.value as? DetailUiState.Success ?: return
        if (s.isPageLoading || target == currentPage) return
        loadPage(target, isRefresh = false)
    }

    private fun loadPage(page: Int, isRefresh: Boolean) {
        val myGen = ++loadGeneration
        loadJob?.cancel()
        val prevSuccess = _state.value as? DetailUiState.Success
        val prevSignature = firstPostSignature
        val prevPage = currentPage
        currentPage = page
        loadJob = viewModelScope.launch {
            _state.value = when {
                prevSuccess != null && isRefresh -> prevSuccess.copy(isRefreshing = true)
                prevSuccess != null -> prevSuccess.copy(isPageLoading = true)
                else -> DetailUiState.Loading
            }
            try {
                val html = withContext(Dispatchers.IO) { fetchThreadPage(currentTid, page) }
                if (myGen != loadGeneration) return@launch
                val result = withContext(Dispatchers.Default) { NgaParser.parseDetail(html) }
                if (myGen != loadGeneration) return@launch

                if (!isRefresh && page > prevPage && prevSuccess != null &&
                    (result.posts.isEmpty() || signatureOf(result.posts) == prevSignature)
                ) {
                    currentPage = prevPage
                    _state.value = prevSuccess.copy(hasMore = false, isPageLoading = false, isRefreshing = false)
                    return@launch
                }

                if (result.posts.isEmpty()) {
                    if (prevSuccess != null && isRefresh) {
                        _state.value = prevSuccess.copy(isRefreshing = false)
                    } else {
                        _state.value = DetailUiState.Error("未能解析帖子内容，请稍后重试")
                    }
                    return@launch
                }

                firstPostSignature = signatureOf(result.posts)
                val originalPost = if (page == 1) result.posts.firstOrNull() else null
                if (page == 1 && originalPost != null && originalPost.uid.isNotBlank()) {
                    opUid = originalPost.uid
                }
                val comments = if (page == 1) result.posts.drop(1) else result.posts
                _state.value = DetailUiState.Success(
                    title = result.title,
                    forumName = currentForumName,
                    originalPost = originalPost,
                    comments = comments,
                    opUid = opUid,
                    onlyOwner = onlyOwnerMode,
                    currentPage = page,
                    hasMore = result.posts.size >= PAGE_SIZE,
                    isPageLoading = false,
                    isRefreshing = false
                )

                val firstAuthor = (originalPost ?: result.posts.firstOrNull())?.author.orEmpty()
                ThreadRecordsStore.recordVisit(
                    tid = currentTid,
                    title = result.title,
                    forumName = currentForumName,
                    author = firstAuthor,
                    page = page
                )

                resolveAuthorInfo(myGen)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (myGen != loadGeneration) return@launch
                if (prevSuccess != null && isRefresh) {
                    _state.value = prevSuccess.copy(isRefreshing = false)
                } else {
                    _state.value = DetailUiState.Error(t.message ?: "未知错误")
                }
            }
        }
    }

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
            val updatedOp = current.originalPost?.let { withInfo(it) }
            _state.value = current.copy(
                originalPost = updatedOp,
                comments = current.comments.map { withInfo(it) }
            )
            if (updatedOp != null && updatedOp.author.isNotBlank()) {
                ThreadRecordsStore.recordVisit(
                    tid = currentTid,
                    title = current.title,
                    forumName = current.forumName,
                    author = updatedOp.author,
                    page = current.currentPage
                )
            }
        }
    }

    private fun signatureOf(posts: List<Post>): String {
        val p = posts.firstOrNull() ?: return ""
        return "${p.floor}|${p.author}|${p.date}"
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val IMAGE_MAX_COMMENTS = 10
    }

    private fun postUrl(): String =
        if (currentTid.isBlank()) "" else "${BaseConfig.baseUrl}/read.php?tid=$currentTid"

    private fun exportContent(maxComments: Int = Int.MAX_VALUE): ExportManager.ExportContent? =
        (state.value as? DetailUiState.Success)?.let { success ->
            val allPosts = listOfNotNull(success.originalPost) + success.comments.take(maxComments)
            ExportManager.ExportContent(
                title = success.title,
                posts = allPosts,
                url = postUrl(),
                totalReplies = success.comments.size,
            )
        }

    fun exportPlainText(context: Context): String? {
        val content = exportContent() ?: return null
        val text = ExportManager.convertToPlainText(content)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NGA帖子", text))
        return text
    }

    fun exportImage(context: Context, onResult: (Boolean, String) -> Unit) {
        val content = exportContent(maxComments = IMAGE_MAX_COMMENTS)
        if (content == null) {
            onResult(false, "内容未加载完成")
            return
        }
        viewModelScope.launch {
            try {
                val cookie = CookieStore.get()
                val html = ExportManager.buildExportHtml(context, content, forImage = true)
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

private val detailPageClient by lazy { NgaApi.sharedClientBuilder().build() }

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
    val bookmarks by ThreadRecordsStore.bookmarks.collectAsState()
    val isBookmarked = remember(bookmarks, tid) { bookmarks.any { it.tid == tid } }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val loadedPage = (state as? DetailUiState.Success)?.currentPage ?: 0
    LaunchedEffect(loadedPage) {
        if (loadedPage > 0) listState.scrollToItem(0)
    }

    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }

    val context = LocalContext.current
    var showExportSheet by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var fullScreenState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val writeStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要存储权限才能导出", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) writeStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return granted
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    val successState = state as? DetailUiState.Success

    Scaffold(
        containerColor = ForumColors.Page,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            DetailStickyTopBar(
                forumName = successState?.forumName?.ifBlank { forumName } ?: forumName,
                threadTitle = successState?.title.orEmpty(),
                showThreadTitle = isScrolledPastHeader && successState != null,
                onlyOwner = successState?.onlyOwner ?: false,
                showActions = successState != null,
                isBookmarked = isBookmarked,
                onBack = onBack,
                onToggleOnlyOwner = vm::toggleOnlyOwner,
                onToggleBookmark = {
                    val s = successState ?: return@DetailStickyTopBar
                    val saved = ThreadRecordsStore.toggleBookmark(
                        tid = tid,
                        title = s.title,
                        forumName = s.forumName,
                        author = s.originalPost?.author.orEmpty(),
                        page = s.currentPage
                    )
                    Toast.makeText(context, if (saved) "已收藏帖子" else "已取消收藏", Toast.LENGTH_SHORT).show()
                },
                onShare = { showExportSheet = true }
            )
        },
        bottomBar = {
            if (successState != null) {
                DetailBottomReadingBar(
                    currentPage = successState.currentPage,
                    hasPrev = successState.currentPage > 1,
                    hasNext = successState.hasMore,
                    isLoading = successState.isPageLoading,
                    showScrollTop = isScrolledPastHeader,
                    onPrev = vm::loadPrevPage,
                    onNext = vm::loadNextPage,
                    onJumpPage = { showJumpDialog = true },
                    onScrollTopOrRefresh = {
                        if (isScrolledPastHeader) {
                            scope.launch { listState.animateScrollToItem(0) }
                        } else {
                            vm.refresh()
                        }
                    }
                )
            }
        }
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> {
                ThreadDetailSkeleton(modifier = Modifier.padding(padding))
            }

            is DetailUiState.Error -> {
                ErrorStateView(
                    title = "帖子加载失败",
                    message = s.message,
                    onRetry = { vm.retry() },
                    modifier = Modifier.padding(padding)
                )
            }

            is DetailUiState.Success -> {
                val filteredComments = remember(s.comments, s.onlyOwner, s.opUid) {
                    if (!s.onlyOwner || s.opUid.isBlank()) s.comments
                    else s.comments.filter { it.uid == s.opUid }
                }

                PullToRefreshBox(
                    isRefreshing = s.isRefreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 帖子标题 Hero 卡片
                        item(key = "hero_header") {
                            DetailHeroHeader(
                                title = s.title,
                                forumName = s.forumName,
                                originalPost = s.originalPost,
                                commentsCount = s.comments.size,
                                currentPage = s.currentPage,
                                onlyOwner = s.onlyOwner
                            )
                        }

                        // 主楼
                        s.originalPost?.let { post ->
                            item(key = "op_${s.currentPage}_${post.floor}") {
                                FloorCard(
                                    post = post,
                                    isOwner = true,
                                    isMainPost = true,
                                    onImageClick = { images, index -> fullScreenState = images to index }
                                )
                            }
                        }

                        // 回复区标题
                        if (s.comments.isNotEmpty()) {
                            item(key = "replies_section") {
                                SectionBar(
                                    text = if (s.onlyOwner) "只看楼主回复" else if (s.currentPage == 1) "全部回复" else "第 ${s.currentPage} 页回复",
                                    trailing = "${filteredComments.size} 条"
                                )
                            }
                        }

                        if (s.onlyOwner && filteredComments.isEmpty() && s.comments.isNotEmpty()) {
                            item(key = "only_owner_empty") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ForumColors.Surface,
                                    border = BorderStroke(1.dp, ForumColors.Divider),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "当前页没有楼主的回复，可翻页或关闭「只看楼主」",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ForumColors.Meta,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = filteredComments,
                            key = { index, post -> "${s.currentPage}-$index-${post.floor}" }
                        ) { _, post ->
                            val isOwnerReply = s.opUid.isNotBlank() && post.uid == s.opUid
                            FloorCard(
                                post = post,
                                isOwner = isOwnerReply,
                                isMainPost = false,
                                onImageClick = { images, index -> fullScreenState = images to index },
                                modifier = Modifier.animateItem()
                            )
                        }

                        if (!s.hasMore && filteredComments.isNotEmpty() && !s.isPageLoading) {
                            item(key = "end_marker") { EndMarker() }
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            onDismiss = { if (!isExporting) showExportSheet = false },
            isExporting = isExporting,
            onCopyText = {
                if (vm.exportPlainText(context) != null) {
                    toast("纯文本已复制到剪贴板")
                    showExportSheet = false
                }
            },
            onExportImage = {
                if (!ensureStoragePermission()) return@ExportBottomSheet
                isExporting = true
                vm.exportImage(context) { success, msg ->
                    isExporting = false
                    toast(msg)
                    if (success) showExportSheet = false
                }
            }
        )
    }

    if (showJumpDialog && successState != null) {
        JumpPageDialog(
            currentPage = successState.currentPage,
            onDismiss = { showJumpDialog = false },
            onConfirm = { page ->
                showJumpDialog = false
                vm.jumpToPage(page)
            }
        )
    }

    fullScreenState?.let { (images, index) ->
        FullScreenImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { fullScreenState = null }
        )
    }
}

@Composable
private fun DetailStickyTopBar(
    forumName: String,
    threadTitle: String,
    showThreadTitle: Boolean,
    onlyOwner: Boolean,
    showActions: Boolean,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleOnlyOwner: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Surface(
        color = ForumColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 4.dp, bottom = 4.dp, start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = ForumColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                AnimatedContent(
                    targetState = showThreadTitle,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    modifier = Modifier.weight(1f),
                    label = "stickyTitle"
                ) { showTitle ->
                    if (showTitle && threadTitle.isNotBlank()) {
                        Text(
                            text = threadTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ForumColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (forumName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgePill(
                                text = forumName,
                                containerColor = ForumColors.LinkSoft,
                                contentColor = ForumColors.Link
                            )
                        }
                    } else {
                        Text(
                            text = "帖子详情",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ForumColors.TextPrimary
                        )
                    }
                }

                if (showActions) {
                    val ownerBg by animateColorAsState(
                        targetValue = if (onlyOwner) ForumColors.OwnerBg else ForumColors.SurfaceMuted,
                        label = "ownerFilterBg"
                    )
                    val ownerFg by animateColorAsState(
                        targetValue = if (onlyOwner) ForumColors.OwnerText else ForumColors.TextSecondary,
                        label = "ownerFilterFg"
                    )
                    Surface(
                        onClick = onToggleOnlyOwner,
                        shape = RoundedCornerShape(10.dp),
                        color = ownerBg,
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = "只看楼主",
                                tint = ownerFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "楼主",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ownerFg
                            )
                        }
                    }

                    BouncyFavoriteButton(
                        isFavorite = isBookmarked,
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(38.dp),
                        iconSize = 20.dp
                    )

                    IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享导出",
                            tint = ForumColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
        }
    }
}

@Composable
private fun DetailHeroHeader(
    title: String,
    forumName: String,
    originalPost: Post?,
    commentsCount: Int,
    currentPage: Int,
    onlyOwner: Boolean
) {
    val views = originalPost?.views?.toIntOrNull() ?: 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ForumColors.Surface,
        border = BorderStroke(1.dp, ForumColors.Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ForumColors.TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (forumName.isNotBlank()) {
                    BadgePill(
                        text = forumName,
                        containerColor = ForumColors.AccentSoft,
                        contentColor = ForumColors.Accent
                    )
                }
                BadgePill(
                    text = "第 $currentPage 页",
                    containerColor = ForumColors.SurfaceMuted,
                    contentColor = ForumColors.Strong
                )
                BadgePill(
                    text = "$commentsCount 回复",
                    containerColor = ForumColors.SurfaceMuted,
                    contentColor = ForumColors.Strong
                )
                if (views > 0) {
                    BadgePill(
                        text = "$views 浏览",
                        containerColor = ForumColors.SurfaceMuted,
                        contentColor = ForumColors.Meta,
                        icon = Icons.Outlined.Visibility
                    )
                }
                if (onlyOwner) {
                    BadgePill(
                        text = "只看楼主",
                        containerColor = ForumColors.OwnerBg,
                        contentColor = ForumColors.OwnerText
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionBar(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.5.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(ForumColors.Accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ForumColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = ForumColors.Meta
            )
        }
    }
}

private fun displayAuthor(post: Post): String = post.author.ifBlank {
    if (post.uid.isNotBlank()) "UID:${post.uid}" else "匿名用户"
}

private fun formatFloorLabel(rawFloor: String, isMainPost: Boolean): String {
    if (isMainPost && (rawFloor.isBlank() || rawFloor == "#0" || rawFloor == "0")) return "主楼"
    val clean = rawFloor.removePrefix("#").trim()
    return when (clean) {
        "0" -> "主楼"
        "1" -> "沙发 #1"
        "2" -> "板凳 #2"
        "3" -> "地板 #3"
        "" -> ""
        else -> "#$clean"
    }
}

@Composable
private fun FloorCard(
    post: Post,
    isOwner: Boolean,
    isMainPost: Boolean,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val authorName = displayAuthor(post)
    val floorLabel = formatFloorLabel(post.floor, isMainPost)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ForumColors.Surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isMainPost) ForumColors.Accent.copy(alpha = 0.30f) else ForumColors.Divider
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloorAvatar(
                    url = post.avatarUrl,
                    name = authorName,
                    size = 38.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOwner) ForumColors.Accent else ForumColors.Link,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOwner) {
                            Spacer(Modifier.width(6.dp))
                            BadgePill(
                                text = "楼主",
                                containerColor = ForumColors.OwnerBg,
                                contentColor = ForumColors.OwnerText
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = ForumColors.Meta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val viewCount = post.views.toIntOrNull() ?: 0
                        if (viewCount > 0) {
                            Text(
                                text = " · $viewCount 浏览",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForumColors.Floor
                            )
                        }
                    }
                }

                if (floorLabel.isNotBlank()) {
                    BadgePill(
                        text = floorLabel,
                        containerColor = if (isMainPost) ForumColors.AccentSoft else ForumColors.SurfaceMuted,
                        contentColor = if (isMainPost) ForumColors.Accent else ForumColors.Floor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            HorizontalDivider(
                color = ForumColors.Divider,
                thickness = 1.dp,
                modifier = Modifier.padding(top = 11.dp, bottom = 6.dp)
            )

            PostContent(post.contentNodes, onImageClick)

            val likeCount = post.likes.toIntOrNull() ?: 0
            if (likeCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ForumColors.OwnerBg)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = "推荐",
                            tint = ForumColors.OwnerText,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = likeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ForumColors.OwnerText
                        )
                    }
                }
            }
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFFEA580C) to Color(0xFFFB923C),
    Color(0xFF2563EB) to Color(0xFF60A5FA),
    Color(0xFF0D9488) to Color(0xFF2DD4BF),
    Color(0xFF7C3AED) to Color(0xFFA78BFA),
    Color(0xFFDB2777) to Color(0xFFF472B6),
    Color(0xFF4F46E5) to Color(0xFF818CF8),
)

@Composable
private fun FloorAvatar(url: String, name: String, size: Dp) {
    val shape = CircleShape
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(ForumColors.SurfaceMuted)
                .border(1.dp, ForumColors.Divider, shape),
            contentScale = ContentScale.Crop,
            error = ColorPainter(ForumColors.SurfaceMuted)
        )
        return
    }
    val pair = remember(name) {
        avatarPalette[abs(name.hashCode()) % avatarPalette.size]
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).ifBlank { "?" },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun DetailBottomReadingBar(
    currentPage: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    isLoading: Boolean,
    showScrollTop: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJumpPage: () -> Unit,
    onScrollTopOrRefresh: () -> Unit
) {
    Surface(
        color = ForumColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = ForumColors.Divider, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onPrev,
                    enabled = hasPrev && !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasPrev && !isLoading) ForumColors.SurfaceMuted else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一页",
                            tint = if (hasPrev && !isLoading) ForumColors.TextPrimary else ForumColors.Floor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "上一页",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasPrev && !isLoading) ForumColors.TextPrimary else ForumColors.Floor
                        )
                    }
                }

                // 中央可点击页码胶囊
                Surface(
                    onClick = onJumpPage,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    color = ForumColors.AccentSoft
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = ForumColors.Accent
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "加载中",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ForumColors.Accent
                            )
                        } else {
                            Text(
                                text = "第 $currentPage 页",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ForumColors.Accent
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onNext,
                        enabled = hasNext && !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasNext && !isLoading) ForumColors.SurfaceMuted else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "下一页",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasNext && !isLoading) ForumColors.TextPrimary else ForumColors.Floor
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "下一页",
                                tint = if (hasNext && !isLoading) ForumColors.TextPrimary else ForumColors.Floor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(onClick = onScrollTopOrRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (showScrollTop) Icons.Default.ArrowUpward else Icons.Default.Refresh,
                            contentDescription = if (showScrollTop) "回到顶部" else "刷新当前页",
                            tint = ForumColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpPageDialog(
    currentPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转页码") },
        text = {
            OutlinedTextField(
                value = pageInput,
                onValueChange = { input -> pageInput = input.filter { it.isDigit() }.take(5) },
                label = { Text("输入目标页码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = pageInput.toIntOrNull() ?: currentPage
                    onConfirm(target.coerceAtLeast(1))
                }
            ) {
                Text("跳转", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EndMarker() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.width(36.dp), color = ForumColors.Divider)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "本页没有更多楼层",
            style = MaterialTheme.typography.labelSmall,
            color = ForumColors.Floor
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(modifier = Modifier.width(36.dp), color = ForumColors.Divider)
    }
}

@Composable
private fun PostContent(nodes: List<ContentNode>, onImageClick: (List<String>, Int) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenDensity = density.density
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = remember(screenDensity, screenWidthDp) {
        (screenWidthDp * screenDensity).toInt()
    }

    val allImages = remember(nodes) {
        nodes.filterIsInstance<ContentNode.Image>().map { it.url }
    }
    val groupedNodes = remember(nodes) { groupContentNodes(nodes) }

    var imgIdx = 0
    groupedNodes.forEachIndexed { index, group ->
        key(index, group::class) {
            when (group) {
                is NodeGroup.Inline -> InlineRichText(group.nodes)

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
                            .clip(RoundedCornerShape(12.dp))
                            .background(ForumColors.SurfaceMuted)
                            .clickable { onImageClick(allImages, currentImageIndex) },
                        contentScale = ContentScale.FillWidth
                    )
                }

                is NodeGroup.Quote -> {
                    QuoteBlock(content = group.content)
                }
            }
        }
    }
}

@Composable
private fun QuoteBlock(content: String) {
    val isLongQuote = remember(content) { content.length > 180 || content.count { it == '\n' } >= 5 }
    var expanded by remember(content) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ForumColors.QuoteBg,
        border = BorderStroke(1.dp, ForumColors.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Max)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(ForumColors.QuoteBar)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = ForumColors.Meta,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "引用回复",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ForumColors.Meta
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = ForumColors.TextSecondary,
                    maxLines = if (isLongQuote && !expanded) 5 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLongQuote) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (expanded) "收起引用" else "展开全部引用",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ForumColors.Link,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}

private sealed class NodeGroup {
    class Inline(val nodes: List<ContentNode>) : NodeGroup()
    data class Image(val url: String) : NodeGroup()
    data class Quote(val content: String) : NodeGroup()
}

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

@OptIn(ExperimentalTextApi::class)
@Composable
private fun InlineRichText(nodes: List<ContentNode>) {
    val emojiExists = emojiExistsCache
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
                                    placeholder = ColorPainter(ForumColors.SurfaceMuted)
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
                    is ContentNode.Text -> if (node.text.isNotBlank()) append(node.text)
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
        color = ForumColors.TextPrimary,
        inlineContent = inlineContent,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportBottomSheet(
    onDismiss: () -> Unit,
    isExporting: Boolean,
    onCopyText: () -> Unit,
    onExportImage: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ForumColors.Surface,
        contentColor = ForumColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "导出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ForumColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "选择需要将当前帖子导出的形式",
                style = MaterialTheme.typography.bodySmall,
                color = ForumColors.Meta
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportGridTile(
                    icon = Icons.Default.Image,
                    title = "生成长图",
                    subtitle = "保存图片到相册",
                    tint = Color(0xFF7C3AED),
                    enabled = !isExporting,
                    onClick = onExportImage,
                    modifier = Modifier.weight(1f)
                )
                ExportGridTile(
                    icon = Icons.Default.ContentCopy,
                    title = "复制文本",
                    subtitle = "纯文本到剪贴板",
                    tint = ForumColors.Link,
                    enabled = !isExporting,
                    onClick = onCopyText,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = isExporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ForumColors.Accent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在处理导出内容…",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForumColors.Meta
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportGridTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ForumColors.SurfaceMuted,
        border = BorderStroke(1.dp, ForumColors.Divider)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ForumColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ForumColors.Meta
                )
            }
        }
    }
}
