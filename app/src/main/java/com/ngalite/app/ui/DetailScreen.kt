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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PostTextBackground = Color(0xFFF3F3F3)

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val title: String,
        val forumName: String,
        val originalPost: Post?,
        val comments: List<Post>,
        val page: Int,
        val hasNextPage: Boolean,
        val isPageLoading: Boolean = false
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
}

/**
 * 全局缓存 emoji 资源存在性集合，避免每个 [InlineRichText] 瀹炰緥鍚勮嚜鎵弿 assets銆?
 * 鍦ㄩ娆¤闂椂閫氳繃 [Lazy] 鍒濆鍖栵紝鍚庣画鎵€鏈?Composable 鍏变韩鍚屼竴浠姐€?
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

    /** 浠ｆ璁℃暟鍣細姣忔 load() 鑷锛岀敤浜庝涪寮冨凡鍙栨秷鍗忕▼鐨勭粨鏋?*/
    private var loadGeneration = 0L

    private companion object {
        // NGA read.php renders 20 floors per page. The supplied P1 fixture has seven floors,
        // which correctly identifies it as the final page without an unnecessary request.
        const val POSTS_PER_PAGE = 20
    }

    fun load(tid: String, forumName: String = "") {
        currentTid = tid
        currentForumName = forumName
        loadPage(1, initialLoad = true)
    }

    fun loadPage(page: Int) {
        loadPage(page, initialLoad = false)
    }

    private fun loadPage(page: Int, initialLoad: Boolean) {
        val targetPage = page.coerceAtLeast(1)
        if (currentTid.isBlank()) return
        val previous = _state.value as? DetailUiState.Success
        if (!initialLoad && previous?.isPageLoading == true) return

        val myGen = ++loadGeneration
        viewModelScope.launch {
            if (initialLoad || previous == null) {
                _state.value = DetailUiState.Loading
            } else {
                _state.value = previous.copy(isPageLoading = true)
            }
            try {
                val html = withContext(Dispatchers.IO) { NgaApi.fetchThread(currentTid, targetPage) }
                if (myGen != loadGeneration) return@launch
                val result = withContext(Dispatchers.Default) { NgaParser.parseDetail(html) }
                if (myGen != loadGeneration) return@launch

                // An exact 20-floor final page has no reliable next-page marker in NGA's static HTML.
                // When page N + 1 is empty, retain N and mark it as final instead of showing an error.
                if (result.posts.isEmpty() && previous != null && targetPage > previous.page) {
                    _state.value = previous.copy(hasNextPage = false, isPageLoading = false)
                    return@launch
                }

                val originalPost = if (targetPage == 1) result.posts.firstOrNull() else null
                val comments = if (targetPage == 1) result.posts.drop(1) else result.posts
                _state.value = DetailUiState.Success(
                    title = result.title,
                    forumName = currentForumName,
                    originalPost = originalPost,
                    comments = comments,
                    page = targetPage,
                    hasNextPage = true
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (myGen != loadGeneration) return@launch
                _state.value = previous?.copy(isPageLoading = false)
                    ?: DetailUiState.Error(t.message ?: "????")
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

    /** 导出 HTML 鍒颁笅杞界洰褰?*/
    fun exportHtml(
        context: Context,
        includeAttribution: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        val content = exportContent()
        if (content == null) {
            onResult(false, "Content is not ready")
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

    /** 瀵煎嚭鍥剧墖鍒扮浉鍐岋紙鎸夋墜鏈哄睆骞曞搴︽覆鏌擄級 */
    fun exportImage(
        context: Context,
        includeAttribution: Boolean,
        onResult: (Boolean, String) -> Unit,
    ) {
        val content = exportContent()
        if (content == null) {
            onResult(false, "Content is not ready")
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

    /** 閫氳繃绯荤粺鎵撳嵃瀵硅瘽妗嗗鍑?PDF */
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
                // 鎵撳嵃妗嗘灦宸叉帴绠?UI，异常仅记录
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
    val listState = rememberLazyListState()
    LaunchedEffect((state as? DetailUiState.Success)?.page) {
        if (state is DetailUiState.Success) listState.scrollToItem(0)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    val writeStorageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(context, "Storage permission is required to export", android.widget.Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { vm.load(tid, forumName) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("重试")
                }
            }

            is DetailUiState.Success -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topSpacing + 12.dp,
                        bottom = 92.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u5206\u4eab")
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
                                enabled = !isExporting && !s.isPageLoading
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "??")
                            }
                        }
                    }

                    s.originalPost?.let { post ->
                        item { OriginalPostCard(post) { fullScreenImageUrl = it } }
                    }

                    if (s.comments.isNotEmpty()) {
                        item {
                            Text(
                                text = if (s.page == 1) "\u5168\u90e8\u56de\u590d" else "P${s.page} \u9875\u56de\u590d",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            )
                        }
                    }

                    itemsIndexed(s.comments, key = { index, post -> "${s.page}-$index-${post.floor}-${post.author}" }) { _, post ->
                        CommentCard(post) { fullScreenImageUrl = it }
                    }
                }

                ThreadPager(
                    page = s.page,
                    canGoNext = s.hasNextPage,
                    isLoading = s.isPageLoading,
                    onPrevious = { vm.loadPage(s.page - 1) },
                    onNext = { vm.loadPage(s.page + 1) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { if (!isExporting) showExportDialog = false },
            isExporting = isExporting,
            onExportMarkdown = { includeAttribution ->
                vm.exportMarkdown(context)
                toast("Markdown copied to clipboard")
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
                toast("Print dialog opened. You can save the thread as a PDF.")
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
private fun ThreadPager(
    page: Int,
    canGoNext: Boolean,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious, enabled = page > 1 && !isLoading) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(2.dp))
                Text("\u4e0a\u4e00\u9875")
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("P$page", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onNext, enabled = canGoNext && !isLoading) {
                Text("\u4e0b\u4e00\u9875")
                Spacer(Modifier.size(2.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun OriginalPostCard(post: Post, onImageClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = PostTextBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            PostContent(post.contentNodes, onImageClick)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text(
                "${post.date} · 楼主${if (post.views != "0") " · ${post.views} 娴忚" else ""}",
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
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = PostTextBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    "${post.date}${if (post.views != "0") " · ${post.views} 娴忚" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (post.likes != "0") {
                    Text(
                        "Likes ${post.likes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * 娓叉煋姝ｆ枃鍐呭鑺傜偣锛氳繛缁殑鏂囨湰涓庤〃鎯呭悎骞朵负鍐呰仈瀵屾枃鏈紝鍥剧墖/寮曠敤鍗曠嫭鎴愬潡銆?
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
        // 使用 key 闃叉 Compose 浣嶇疆璁板繂鍖栧湪鑺傜偣绫诲瀷鍙樺寲鏃堕敊閰嶇姸鎬?
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
                            .clip(RoundedCornerShape(18.dp))
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
                            .clip(RoundedCornerShape(16.dp))
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

/** 鑺傜偣鍒嗙粍缁撴灉锛岀敤浜庣紦瀛?PostContent 鐨勯亶鍘?*/
private sealed class NodeGroup {
    class Inline(val nodes: List<ContentNode>) : NodeGroup()
    data class Image(val url: String) : NodeGroup()
    data class Quote(val content: String) : NodeGroup()
}

/** 灏嗗唴瀹硅妭鐐瑰垪琛ㄥ垎缁勶細杩炵画鐨勬枃鏈?琛ㄦ儏鍚堝苟涓?Inline锛屽浘鐗囧拰寮曠敤鍚勮嚜鐙珛 */
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
 * 灏嗚繛缁殑鏂囨湰鍜岃〃鎯呰妭鐐规覆鏌撲负鍐呰仈瀵屾枃鏈紝琛ㄦ儏鍥剧墖浠?assets 鍔犺浇銆?
 * 使用 FlowRow 瀹炵幇鏂囨湰涓庤〃鎯呭浘鐗囩殑琛屽唴娣锋帓銆?
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
            // 使用 key 闃叉 Compose 浣嶇疆璁板繂鍖栧湪鑺傜偣绫诲瀷鍙樺寲鏃堕敊閰嶇姸鎬?
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
                    "\u9009\u62e9\u5bfc\u51fa\u683c\u5f0f",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                ExportOption(Icons.Default.Code, "Markdown", "\u590d\u5236\u4e3a Markdown \u6587\u672c") {
                    onExportMarkdown(includeAttribution)
                }
                ExportOption(Icons.Default.Html, "HTML", "\u4fdd\u5b58 HTML \u6587\u4ef6\u5230\u4e0b\u8f7d\u76ee\u5f55") {
                    onExportHtml(includeAttribution)
                }
                ExportOption(Icons.Default.Image, "\u56fe\u7247", "\u6e32\u67d3\u5e76\u4fdd\u5b58\u957f\u56fe") {
                    onExportImage(includeAttribution)
                }
                ExportOption(Icons.Default.PictureAsPdf, "PDF", "\u901a\u8fc7\u7cfb\u7edf\u6253\u5370\u5bf9\u8bdd\u6846\u4fdd\u5b58\u4e3a PDF") {
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
                        Text("\u6b63\u5728\u5bfc\u51fa\u2026", style = MaterialTheme.typography.bodyMedium)
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
