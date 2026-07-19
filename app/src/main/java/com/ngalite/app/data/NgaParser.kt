package com.ngalite.app.data

import org.jsoup.Jsoup

/** 用 Jsoup 解析 NGA 的 GBK HTML，提取帖子列表与详情 */
object NgaParser {

    private const val MAX_PREVIEW_IMAGES = 4
    private val IMAGE_UBB_REGEX = Regex("""(?is)\[img(?:=[^\]]+)?](.*?)\[/img]""")
    private val EXCLUDED_PREVIEW_IMAGE_PARTS = listOf(
        "/post/smile/",
        "/emoticon/",
        "/ficon/",
        "blank.gif",
        "loading.gif"
    )

    private val TID_REGEX = Regex("""tid=(\d+)""")
    private val BR_REGEX = """(?i)<br\s*/?>""".toRegex()
    private val P_REGEX = """(?i)</p>""".toRegex()
    private val DIV_REGEX = """(?i)</div>""".toRegex()
    private val TAG_REGEX = """(?i)<[^>]+>""".toRegex()
    private val MULTI_NEWLINE = """\n{3,}""".toRegex()
    private val VIEWS_REGEX = Regex("""浏览\s*[：:]?\s*([+\-]?\d+)""")
    private val LIKES_REGEX = Regex("""赞\s*[（(]?\s*([+\-]?\d+)\s*[）)]?""")
    private val NUM_CLEAN = Regex("[^0-9-]")
    private val TITLE_CLEAN = Regex("""\s*NGA.*$""")

    /** 从已解析的文档中提取标题 */
    private fun extractTitle(doc: org.jsoup.nodes.Document): String {
        return doc.title().replace(TITLE_CLEAN, "").trim().ifBlank { "NGA帖子" }
    }

    /** 解析帖子标题（来自 <title>，去掉 " NGA玩家社区 P1" 后缀） */
    fun parseThreadTitle(html: String): String = extractTitle(Jsoup.parse(html))

    /** 帖子详情解析结果 */
    data class DetailResult(val title: String, val posts: List<Post>)

    /** 一步完成标题 + 帖子内容解析（仅一次 Jsoup.parse） */
    fun parseDetail(html: String): DetailResult {
        val doc = Jsoup.parse(html)
        val title = extractTitle(doc)
        val posts = parsePostsFromDoc(doc)
        return DetailResult(title, posts)
    }

    /** 解析帖子列表 */
    fun parseTopicList(html: String): List<Topic> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr.topicrow")
        return rows.mapNotNull { row ->
            val topicLink = row.selectFirst("a.topic") ?: return@mapNotNull null
            val href = topicLink.attr("href")
            val tid = TID_REGEX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = topicLink.text().trim()
            val replies = row.selectFirst("a.replies")?.text()?.trim() ?: ""
            val replyTime = row.selectFirst(".replydate")?.text()?.trim() ?: ""
            val author = row.selectFirst(".replyer")?.text()?.trim() ?: ""
            val previewImages = extractPreviewImages(row, title)
            if (title.isEmpty()) null else Topic(tid, title, replies, author, replyTime, previewImages)
        }
    }

    /** 从帖子行中提取主楼预览图片地址 */
    private fun extractPreviewImages(row: org.jsoup.nodes.Element, title: String): List<String> {
        val images = mutableListOf<String>()
        // 1. 提取行内 <img> 标签（排除版块图标）
        row.select("img").forEach { img ->
            val src = img.attr("src").trim()
            if (src.isNotEmpty() && !src.contains("/ficon/", ignoreCase = true)) {
                images.add(toAbsoluteUrl(src))
            }
        }
        // 2. 标题中可能包含 [img]...[/img] UBB 图片
        val ubbPattern = Regex("""(?i)\[img\](.*?)\[/img\]""")
        ubbPattern.findAll(title).forEach { match ->
            val path = match.groupValues[1].trim().removePrefix("./").removePrefix("/")
            if (path.isNotEmpty()) {
                images.add(UbbParser.IMG_BASE + path)
            }
        }
        return images.distinct().take(MAX_PREVIEW_IMAGES)
    }

    /** 将相对地址补全为绝对地址 */
    private fun toAbsoluteUrl(src: String): String {
        return when {
            src.startsWith("http://", ignoreCase = true) ||
                src.startsWith("https://", ignoreCase = true) -> src
            src.startsWith("/") -> "https://bbs.nga.cn$src"
            else -> "https://bbs.nga.cn/$src"
        }
    }

    /** Extracts images from the original post for topic-list thumbnails. */
    fun parseMainPostImages(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val content = doc.selectFirst("tr.postrow [id^=postcontent]") ?: return emptyList()
        val images = mutableListOf<String>()

        // Images that NGA has already rendered as HTML in the original post.
        content.select("img").forEach { image ->
            val source = sequenceOf("data-src", "data-original", "file", "src")
                .map { image.attr(it).trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return@forEach
            normalizeImageUrl(source)?.let(images::add)
        }

        // Some NGA responses still contain raw [img] UBB tags.
        val rawText = htmlToText(content.html())
        IMAGE_UBB_REGEX.findAll(rawText).forEach { match ->
            normalizeImageUrl(match.groupValues[1])?.let(images::add)
        }

        return images.distinct().take(MAX_PREVIEW_IMAGES)
    }

    private fun normalizeImageUrl(source: String): String? {
        val value = source.trim().replace("&amp;", "&")
        if (value.isEmpty() || value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("blob:", ignoreCase = true)
        ) return null
        val url = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/attachments/", ignoreCase = true) -> "https://img.nga.178.com$value"
            value.startsWith("./") -> UbbParser.IMG_BASE + value.removePrefix("./")
            value.startsWith("/") -> "https://bbs.nga.cn$value"
            else -> UbbParser.IMG_BASE + value
        }
        return url.takeUnless { candidate ->
            EXCLUDED_PREVIEW_IMAGE_PARTS.any { candidate.contains(it, ignoreCase = true) }
        }
    }

    /** Parses all floors in a thread detail response. */
    fun parsePosts(html: String): List<Post> = parsePostsFromDoc(Jsoup.parse(html))

    private fun parsePostsFromDoc(doc: org.jsoup.nodes.Document): List<Post> {
        val rows = doc.select("tr.postrow")
        return rows.mapIndexedNotNull { index, row ->
            val author = row.selectFirst("[id^=postauthor]")?.text()?.trim() ?: ""
            val date = row.selectFirst("[id^=postdate]")?.text()?.trim() ?: ""
            val contentEl = row.selectFirst("[id^=postcontent]") ?: return@mapIndexedNotNull null
            val rawText = htmlToText(contentEl.html())
            val contentNodes = UbbParser.parse(rawText)
            val floor = row.selectFirst("a[name^=l]")?.text()?.trim() ?: "#$index"
            val likes = parseLikes(row)
            val views = parseViews(row)
            Post(floor, author, date, likes, views, contentNodes)
        }
    }

    /** 从楼层行解析浏览数：优先从特定元素提取，避免全文本扫描 */
    private fun parseViews(row: org.jsoup.nodes.Element): String {
        row.select("[id^=postview]").firstOrNull()?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        // 回退：仅在特定元素未找到时才扫描全文
        val match = VIEWS_REGEX.find(row.text())
        if (match != null) return match.groupValues[1]
        return "0"
    }

    /** 从楼层行解析点赞数：优先从特定元素提取，避免全文本扫描 */
    private fun parseLikes(row: org.jsoup.nodes.Element): String {
        row.select("[id^=likes_num]").firstOrNull()?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        // 回退：仅在特定元素未找到时才扫描全文
        val match = LIKES_REGEX.find(row.text())
        if (match != null) return match.groupValues[1]
        return "0"
    }

    /** HTML 转纯文本：保留换行，剥离标签与实体（保留 UBB 标签） */
    private fun htmlToText(html: String): String {
        return html
            .replace(BR_REGEX, "\n")
            .replace(P_REGEX, "\n\n")
            .replace(DIV_REGEX, "\n")
            .replace(TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(MULTI_NEWLINE, "\n\n")
            .trim()
    }
}
