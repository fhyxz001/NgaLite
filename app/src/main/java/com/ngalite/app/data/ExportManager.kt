package com.ngalite.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * 帖子导出管理器：支持导出 Markdown、图片、HTML、PDF，输出适配手机屏幕 16:9 展示。
 *
 * - Markdown：转成 MD 文本并写入剪贴板
 * - 图片：用 WebView 按手机屏幕宽度渲染后截图，保存到相册
 * - HTML：内联图片为 data URL，保存到下载目录，便于离线查看
 * - PDF：通过 Android 打印框架输出，可保存为 PDF
 */
object ExportManager {

    private const val TEMPLATE_ASSET = "article_export_template.html"
    private const val EXPORT_DIR = "NgaLite"
    private const val EXPORT_DPI = 200f

    private val imageClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** 导出内容快照 */
    data class ExportContent(val title: String, val posts: List<Post>)

    // ---- HTML 构建 ----

    fun buildExportHtml(context: Context, content: ExportContent, includeAttribution: Boolean): String {
        val template = context.assets.open(TEMPLATE_ASSET).use { stream ->
            stream.bufferedReader().use { it.readText() }
        }
        return renderTemplate(template, content, includeAttribution)
    }

    private fun renderTemplate(template: String, content: ExportContent, includeAttribution: Boolean): String {
        val postsHtml = content.posts.joinToString("\n") { buildPostHtml(it) }
        val exportedDate = "导出日期：" +
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val attributionClass = if (includeAttribution) "export-credit" else "export-credit is-hidden"
        return template
            .replace("{{title}}", escapeHtml(content.title))
            .replace("{{postsHtml}}", postsHtml)
            .replace("{{exportedDate}}", escapeHtml(exportedDate))
            .replace("{{appAttributionClass}}", attributionClass)
    }

    private fun buildPostHtml(post: Post): String {
        val contentHtml = post.contentNodes.joinToString("") { nodeToHtml(it) }
        val likesHtml = if (post.likes != "0") {
            """<span class="post-likes">赞 ${escapeHtml(post.likes)}</span>"""
        } else ""
        return """
        |<div class="post">
        |    <div class="post-head">
        |        <div class="post-head-left">
        |            <span class="post-author">${escapeHtml(post.author)}</span>
        |            <span class="post-floor">${escapeHtml(post.floor)}</span>
        |        </div>
        |        <span class="post-date">${escapeHtml(post.date)}</span>
        |        $likesHtml
        |    </div>
        |    <div class="post-content">$contentHtml</div>
        |</div>
        """.trimMargin()
    }

    private fun nodeToHtml(node: ContentNode): String = when (node) {
        is ContentNode.Text -> {
            if (node.text.isBlank()) ""
            else "<p>${escapeHtml(node.text).replace("\n", "<br>")}</p>"
        }
        is ContentNode.Image -> {
            "<img src=\"${escapeHtml(node.url)}\" alt=\"图片\" />"
        }
        is ContentNode.Quote -> {
            val body = escapeHtml(node.content).replace("\n", "<br>")
            "<blockquote><span class=\"quote-label\">引用</span>$body</blockquote>"
        }
    }

    // ---- Markdown ----

    fun convertToMarkdown(content: ExportContent): String {
        val sb = StringBuilder()
        sb.append("# ").append(content.title).append("\n\n")
        sb.append("---\n\n")
        content.posts.forEach { post ->
            val likesStr = if (post.likes != "0") " 赞 ${post.likes} " else " "
            sb.append("### ").append(post.floor).append(" ")
                .append(post.author).append("（").append(post.date).append(likesStr).append("）\n\n")
            post.contentNodes.forEach { node ->
                when (node) {
                    is ContentNode.Text -> {
                        if (node.text.isNotBlank()) {
                            sb.append(node.text.trim()).append("\n\n")
                        }
                    }
                    is ContentNode.Image -> {
                        sb.append("![图片](").append(node.url).append(")\n\n")
                    }
                    is ContentNode.Quote -> {
                        node.content.lines().forEach { line ->
                            sb.append("> ").append(line).append("\n")
                        }
                        sb.append("\n")
                    }
                }
            }
            sb.append("---\n\n")
        }
        return sb.toString().trimEnd()
    }

    // ---- 工具方法 ----

    fun escapeHtml(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#x27;")
                else -> append(c)
            }
        }
    }

    fun buildFileName(title: String, extension: String): String {
        val safeTitle = sanitizeFileName(title).ifBlank { "NGA帖子" }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = extension.trimStart('.')
        return "ngalite_${safeTitle}_$ts.$ext"
    }

    private fun sanitizeFileName(text: String): String = text.trim()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    // ---- 图片内联（离线 HTML 用） ----

    /**
     * 将 HTML 中的图片下载并内联为 data URL，失败时保留原地址。
     */
    suspend fun inlineImagesInHtml(html: String, cookie: String): String = withContext(Dispatchers.IO) {
        val document = Jsoup.parse(html)
        val images = document.select("img")
        if (images.isEmpty()) return@withContext html
        images.forEach { img ->
            val src = img.attr("src")
            if (src.isNotBlank() && !src.startsWith("data:")) {
                runCatching { fetchImageAsDataUrl(src, cookie) }
                    .onSuccess { img.attr("src", it) }
            }
        }
        document.outerHtml()
    }

    private fun fetchImageAsDataUrl(url: String, cookie: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NgaApi.UA)
            .header("Cookie", cookie)
            .header("Referer", "https://bbs.nga.cn/")
            .build()
        imageClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw RuntimeException("图片内容为空")
            val mime = resp.header("Content-Type")
                ?.substringBefore(';')?.trim()
                ?.takeIf { it.startsWith("image/") }
                ?: guessImageMime(url)
            val base64 = Base64.getEncoder().encodeToString(bytes)
            return "data:$mime;base64,$base64"
        }
    }

    private fun guessImageMime(url: String): String {
        val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    // ---- 保存到存储 ----

    fun saveHtmlToDownloads(context: Context, displayName: String, htmlContent: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$EXPORT_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载文件")
            return try {
                resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(htmlContent)
                } ?: throw IllegalStateException("无法打开下载文件")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "$EXPORT_DIR/$displayName"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), EXPORT_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建下载目录")
        val file = File(dir, displayName)
        file.writeText(htmlContent)
        return file.absolutePath
    }

    fun saveBitmapToGallery(context: Context, displayName: String, bitmap: Bitmap): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/$EXPORT_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建图片文件")
            return try {
                resolver.openOutputStream(uri)?.use { os ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os)) {
                        throw IllegalStateException("图片编码失败")
                    }
                } ?: throw IllegalStateException("无法打开图片文件")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "$EXPORT_DIR/$displayName"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), EXPORT_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建图片目录")
        val file = File(dir, displayName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    // ---- 图片渲染（WebView 截图，宽度跟随手机屏幕） ----

    /**
     * 使用 WebView 按手机屏幕宽度渲染 HTML 并截图，输出图片适配手机屏幕 16:9 展示。
     */
    suspend fun renderHtmlToBitmap(
        context: Context,
        html: String,
        timeoutMs: Long = 15_000L,
    ): Bitmap = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.useWideViewPort = true
                setBackgroundColor(Color.WHITE)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
            val handler = Handler(Looper.getMainLooper())
            // 渲染宽度跟随手机屏幕，保证在 16:9 手机屏幕上完整展示
            val viewportWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(360)
            var finished = false
            var timeoutRunnable = Runnable {}

            fun fail(error: Throwable) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeoutRunnable)
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
                if (cont.isActive) cont.resumeWithException(error)
            }

            fun finish(contentHeightPx: Int) {
                if (finished) return
                finished = true
                handler.removeCallbacks(timeoutRunnable)
                val safeHeight = contentHeightPx.coerceAtLeast(1)
                measureAndLayout(webView, viewportWidth, safeHeight)
                val density = context.resources.displayMetrics.densityDpi.coerceAtLeast(1).toFloat()
                val scale = (EXPORT_DPI / density).coerceAtLeast(1f)
                val bitmapWidth = (viewportWidth * scale).roundToInt().coerceAtLeast(1)
                val bitmapHeight = (safeHeight * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.scale(
                    bitmapWidth.toFloat() / viewportWidth.toFloat(),
                    bitmapHeight.toFloat() / safeHeight.toFloat(),
                )
                webView.draw(canvas)
                runCatching { webView.destroy() }
                if (cont.isActive) cont.resume(bitmap)
            }

            fun scheduleReadinessCheck(attempt: Int = 0, lastHeight: Int = -1, stablePasses: Int = 0) {
                handler.postDelayed({
                    if (finished) return@postDelayed
                    val density = webView.resources.displayMetrics.density
                    val contentHeightPx = maxOf(
                        (webView.contentHeight * density).roundToInt(),
                        webView.measuredHeight,
                        webView.height,
                        1,
                    )
                    if (contentHeightPx <= 1 && attempt >= 20) {
                        fail(IllegalStateException("内容为空"))
                        return@postDelayed
                    }
                    measureAndLayout(webView, viewportWidth, contentHeightPx.coerceAtLeast(1))
                    val nextStable = if (contentHeightPx == lastHeight) stablePasses + 1 else 0
                    if (contentHeightPx > 1 && (nextStable >= 2 || attempt >= 20)) {
                        finish(contentHeightPx)
                    } else {
                        scheduleReadinessCheck(attempt + 1, contentHeightPx, nextStable)
                    }
                }, if (attempt == 0) 400L else 160L)
            }

            timeoutRunnable = Runnable { fail(IllegalStateException("渲染超时")) }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!finished) scheduleReadinessCheck()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame != false) fail(IllegalStateException("加载错误"))
                }
            }
            measureAndLayout(webView, viewportWidth, 1)
            handler.postDelayed(timeoutRunnable, timeoutMs)
            webView.loadDataWithBaseURL(
                "https://bbs.nga.cn",
                html,
                "text/html",
                "UTF-8",
                null,
            )

            cont.invokeOnCancellation {
                if (!finished) {
                    finished = true
                    handler.removeCallbacks(timeoutRunnable)
                    runCatching { webView.stopLoading() }
                    runCatching { webView.destroy() }
                }
            }
        }
    }

    private fun measureAndLayout(webView: WebView, widthPx: Int, heightPx: Int) {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, width, height)
    }

    // ---- PDF（Android 打印框架） ----

    /**
     * 通过系统打印对话框输出 PDF，内容已适配手机屏幕宽度。
     */
    fun printPdf(context: Context, jobName: String, html: String) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
        }
        var printed = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (printed) return
                printed = true
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter(jobName)
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("default", "default", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, adapter, attrs)
            }
        }
        webView.loadDataWithBaseURL("https://bbs.nga.cn", html, "text/html", "UTF-8", null)
    }
}
