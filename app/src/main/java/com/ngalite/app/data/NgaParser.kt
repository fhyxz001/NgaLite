package com.ngalite.app.data

import org.jsoup.Jsoup

/** 用 Jsoup 解析 NGA 的 GBK HTML，提取帖子列表与详情 */
object NgaParser {

    private const val MAX_PREVIEW_IMAGES = 4

    /** commonui.postArg.proc(...) 的参数序号：recommend（推荐值 "总,支持,反对"），见 NGA js_read.js 的 arg 列表 */
    private const val PROC_RECOMMEND_INDEX = 15
    private val IMAGE_UBB_REGEX = Regex("""(?is)\[img(?:=[^\]]+)?](.*?)\[/img]""")
    private val EXCLUDED_PREVIEW_IMAGE_PARTS = listOf(
        "/post/smile/",
        "/emoticon/",
        "/ficon/",
        "blank.gif",
        "loading.gif"
    )

    private val TID_REGEX = Regex("""tid=(\d+)""")
    private val UID_REGEX = Regex("""[?&]uid=(\d+)""")
    /** 内嵌用户表 commonui.userInfo.setAll({...}) 里的条目起点："uid":12345（兜底形态） */
    private val USER_UID_REGEX = Regex(""""uid"\s*:\s*"?(\d+)"?""")
    /** 内嵌用户表的条目键：{"65081493":{"uid":…（setAll 的常规形态，排除嵌套的数值键对象） */
    private val USER_ENTRY_REGEX = Regex(""""(\d{2,})"\s*:\s*\{\s*"uid"\s*:""")
    /** 单个楼层的 commonui.postArg.proc(...) 调用（参数顺序见 NGA js_read.js 的 arg 列表） */
    private val PROC_CALL_REGEX = Regex("""postArg\.proc\s*\(""")
    /** 头像短码 .a/{uid}_{序号}.{扩展名} */
    private val AVATAR_SHORT_REGEX =
        Regex("""\.a/(\d+)_(\d+)\.(jpg|jpeg|png|gif|webp)(\?\d+)?""", RegexOption.IGNORE_CASE)
    /** 站内内置头像的文件名形态 */
    private val AVATAR_FILE_REGEX =
        Regex("""^[\w.-]+\.(jpg|jpeg|png|gif|webp)$""", RegexOption.IGNORE_CASE)
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
        val posts = parsePostsFromDoc(doc, parseUserInfoMap(html))
        return DetailResult(title, posts)
    }

    /** 解析帖子列表 */
    fun parseTopicList(html: String): List<Topic> {
        val doc = Jsoup.parse(html)
        val users = parseUserInfoMap(html)
        val rows = doc.select("tr.topicrow")
        return rows.mapNotNull { row ->
            val topicLink = row.selectFirst("a.topic") ?: return@mapNotNull null
            val href = topicLink.attr("href")
            val tid = TID_REGEX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = topicLink.text().trim()
            val replies = row.selectFirst("a.replies")?.text()?.trim() ?: ""
            val replyTime = row.selectFirst(".replydate")?.text()?.trim() ?: ""
            // 最后回复者：老版页面直接渲染名字，新版页面为空节点，需要用内嵌用户表按 uid 补全
            val replyerEl = row.selectFirst(".replyer")
            val replyerUid = extractUid(replyerEl)
            val author = resolveAuthorName(replyerEl, replyerUid, users)
            val previewImages = extractPreviewImages(row, title)
            if (title.isEmpty()) null
            else Topic(tid, title, replies, author, replyTime, previewImages, replyerUid)
        }
    }

    /** 从形如 nuke.php?func=ucp&uid=12345 的链接里取出用户 uid（元素本身或其内部链接） */
    private fun extractUid(element: org.jsoup.nodes.Element?): String {
        if (element == null) return ""
        val href = element.attr("href").ifBlank {
            element.selectFirst("a[href]")?.attr("href").orEmpty()
        }
        return UID_REGEX.find(href)?.groupValues?.get(1).orEmpty()
    }

    /**
     * 解析页面内嵌的用户表 `commonui.userInfo.setAll({...})`：老版 read.php 会内联整页的
     * uid → 用户名/头像映射（用户名与头像都不在楼层 HTML 里，而是由前端脚本填入空锚点）。
     * 新版页面不再内联，此时返回空表，由调用方按 uid 回查接口。
     */
    private fun parseUserInfoMap(html: String): Map<String, UserBrief> {
        val start = html.indexOf("userInfo.setAll(")
        if (start < 0) return emptyMap()
        val end = html.indexOf("</script>", start).let { if (it < 0) html.length else it }
        val blob = html.substring(start, end)
        val result = HashMap<String, UserBrief>()

        // 常规形态：{"65081493":{"uid":65081493,"username":"…","avatar":…}}，按条目键切分窗口
        val entries = USER_ENTRY_REGEX.findAll(blob).toList()
        if (entries.isNotEmpty()) {
            entries.forEachIndexed { index, match ->
                val uid = match.groupValues[1]
                val windowStart = match.range.last + 1
                val windowEnd = entries.getOrNull(index + 1)?.range?.first ?: blob.length
                if (windowStart >= windowEnd) return@forEachIndexed
                val brief = readUserBrief(blob.substring(windowStart, windowEnd))
                if (brief.name.isNotBlank() || brief.avatar.isNotBlank()) result[uid] = brief
            }
            if (result.isNotEmpty()) return result
        }

        // 兜底形态：条目里直接给 "uid":12345，窗口截止到下一条 uid 之前
        val uidMatches = USER_UID_REGEX.findAll(blob).toList()
        uidMatches.forEachIndexed { index, match ->
            val uid = match.groupValues[1]
            val windowStart = match.range.last + 1
            val windowEnd = uidMatches.getOrNull(index + 1)?.range?.first ?: blob.length
            if (windowStart >= windowEnd) return@forEachIndexed
            val brief = readUserBrief(blob.substring(windowStart, windowEnd))
            if (brief.name.isNotBlank() || brief.avatar.isNotBlank()) result[uid] = brief
        }
        return result
    }

    /** 从单条用户信息文本里取出用户名与头像短码 */
    private fun readUserBrief(text: String): UserBrief = UserBrief(
        name = JsonText.stringField(text, "username"),
        avatar = JsonText.stringField(text, "avatar")
    )

    /**
     * 作者名来源优先级：
     * 1. 页面内嵌用户表（按 uid 命中，最准确）
     * 2. 元素上的 `data-name`（NGA 增强脚本会把用户名写在这里）
     * 3. 锚点自身文本（剔除"楼主"等身份标签与管理员角标）
     */
    private fun resolveAuthorName(
        element: org.jsoup.nodes.Element?,
        uid: String,
        users: Map<String, UserBrief>
    ): String {
        if (uid.isNotEmpty()) {
            users[uid]?.name?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (element == null) return ""
        val dataName = element.attr("data-name").trim().ifBlank {
            element.selectFirst("[data-name]")?.attr("data-name")?.trim().orEmpty()
        }
        if (dataName.isNotBlank()) return dataName
        val clone = element.clone()
        clone.select(".hld__post-author, .hld__extra-icon, sup").remove()
        return clone.text().trim()
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
            src.startsWith("/") -> BaseConfig.baseUrl + src
            else -> BaseConfig.baseUrl + "/" + src
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
            value.startsWith("/attachments/", ignoreCase = true) -> "https://img.nga.cn$value"
            value.startsWith("./") -> UbbParser.IMG_BASE + value.removePrefix("./")
            value.startsWith("/") -> BaseConfig.baseUrl + value
            else -> UbbParser.IMG_BASE + value
        }
        return url.takeUnless { candidate ->
            EXCLUDED_PREVIEW_IMAGE_PARTS.any { candidate.contains(it, ignoreCase = true) }
        }
    }

    /** Parses all floors in a thread detail response. */
    fun parsePosts(html: String): List<Post> = parsePostsFromDoc(Jsoup.parse(html), parseUserInfoMap(html))

    private fun parsePostsFromDoc(
        doc: org.jsoup.nodes.Document,
        users: Map<String, UserBrief> = emptyMap()
    ): List<Post> {
        val rows = doc.select("tr.postrow")
        return rows.mapIndexedNotNull { index, row ->
            val authorEl = row.selectFirst("[id^=postauthor]")
            val uid = extractUid(authorEl)
            val author = resolveAuthorName(authorEl, uid, users)
            val date = row.selectFirst("[id^=postdate]")?.text()?.trim() ?: ""
            val contentEl = row.selectFirst("[id^=postcontent]") ?: return@mapIndexedNotNull null
            val rawText = htmlToText(contentEl.html())
            val contentNodes = UbbParser.parse(rawText)
            val floor = row.selectFirst("a[name^=l]")?.text()?.trim() ?: "#$index"
            val likes = parseLikes(row)
            val views = parseViews(row)
            val avatarUrl = resolveAvatarUrl(row, uid, users)
            Post(floor, author, date, likes, views, contentNodes, uid, avatarUrl)
        }
    }

    /**
     * 楼层头像：优先用行内已渲染的 `<img id="posteravatarN">`（新版页面/浏览器 DOM），
     * 否则用内嵌用户表里的头像短码还原成完整地址。
     */
    private fun resolveAvatarUrl(
        row: org.jsoup.nodes.Element,
        uid: String,
        users: Map<String, UserBrief>
    ): String {
        // 行内已渲染的头像：限定在 posterinfo 容器内，避免命中引用块里的头像
        val inline = row.selectFirst("img[id^=posteravatar]")
            ?: row.selectFirst("[id^=posterinfo] img.avatar")
        val inlineSrc = normalizeImageUrl(inline?.attr("src").orEmpty())
        if (!inlineSrc.isNullOrBlank()) return inlineSrc
        if (uid.isNotEmpty()) {
            users[uid]?.avatar?.takeIf { it.isNotBlank() }?.let { raw ->
                avatarUrl(raw, uid).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    /**
     * 把 NGA 用户信息里的头像短码还原为可访问地址，规则与网页端 `commonui.avatarUrl` 一致：
     * - `.a/{uid}_{序号}.{扩展名}` → `https://img.nga.cn/avatars/2002/{uid2path}/{uid}_{序号}.{扩展名}`
     * - 站内图片绝对地址 → 原样返回（非 NGA 图床则丢弃）
     * - 纯文件名（站内内置头像）→ `https://img4.nga.cn/ngabbs/face/{文件名}`
     */
    fun avatarUrl(raw: String, uid: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        AVATAR_SHORT_REGEX.find(value)?.let { match ->
            val avatarUid = match.groupValues[1]
            val index = match.groupValues[2]
            val ext = match.groupValues[3].lowercase()
            val query = match.groupValues[4]
            val path = uid2path(avatarUid).ifBlank { uid2path(uid) }
            if (path.isBlank()) return ""
            return "https://img.nga.cn/avatars/2002/$path/${avatarUid}_$index.$ext$query"
        }
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            return if (value.contains("nga.cn") || value.contains("178.com")) value else ""
        }
        return if (AVATAR_FILE_REGEX.matches(value)) "https://img4.nga.cn/ngabbs/face/$value" else ""
    }

    /** NGA 的头像目录规则：uid 转 16 进制补足 9 位后，每 3 位一组倒序 */
    private fun uid2path(uid: String): String {
        val id = uid.toLongOrNull() ?: return ""
        if (id <= 0) return ""
        val hex = id.toString(16).padStart(9, '0').takeLast(9)
        if (hex.length < 9) return ""
        return "${hex.substring(6, 9)}/${hex.substring(3, 6)}/${hex.substring(0, 3)}"
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

    /**
     * 从楼层行解析推荐（点赞）数，按可靠性依次尝试：
     * 1. `[id^=likes_num]` 元素（部分页面变体）
     * 2. `.recommendvalue` 元素（新版页面渲染出的推荐值）
     * 3. 该帖后随的 `commonui.postArg.proc(...)` 调用第 16 个参数（老版页面的推荐值 "总,支持,反对"）
     * 4. 行文本里的"赞 N"
     */
    private fun parseLikes(row: org.jsoup.nodes.Element): String {
        row.select("[id^=likes_num]").firstOrNull()?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        row.selectFirst(".recommendvalue, [id^=recommendvalue]")?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        findPostProcCall(row)?.let { call ->
            parseRecommend(procArgument(call, PROC_RECOMMEND_INDEX))?.let { return it }
        }
        val match = LIKES_REGEX.find(row.text())
        if (match != null) return match.groupValues[1]
        return "0"
    }

    /** 找出该楼层对应的 `commonui.postArg.proc(...)` 调用文本（老版页面里跟在帖子表格之后） */
    private fun findPostProcCall(row: org.jsoup.nodes.Element): String? {
        var node: org.jsoup.nodes.Element? = row
        while (node != null && node.tagName() != "table") node = node.parent()
        // 表格后面紧跟脚本；不同页面变体可能隔着少量兄弟节点或一层容器，这里向上找两层
        var searchFrom: org.jsoup.nodes.Element? = node ?: row.parent()
        var level = 0
        while (searchFrom != null && level < 3) {
            var sibling = searchFrom.nextElementSibling()
            var guard = 0
            while (sibling != null && guard < 8) {
                if (sibling.tagName() == "script" && sibling.data().contains("postArg.proc")) {
                    val data = sibling.data()
                    val prefix = PROC_CALL_REGEX.find(data) ?: return null
                    return data.substring(prefix.range.last + 1)
                }
                sibling = sibling.nextElementSibling()
                guard++
            }
            searchFrom = searchFrom.parent()
            level++
        }
        return null
    }

    /**
     * 取 JS 调用的第 [index] 个参数（0 起）：按顶层逗号切分，跳过引号与括号内的逗号。
     * 返回去掉首尾引号并 trim 后的字面量。
     */
    private fun procArgument(call: String, index: Int): String? {
        val args = ArrayList<String>(24)
        val sb = StringBuilder()
        var depth = 0
        var quote: Char? = null
        var i = 0
        while (i < call.length) {
            val c = call[i]
            if (quote != null) {
                sb.append(c)
                if (c == '\\' && i + 1 < call.length) {
                    sb.append(call[i + 1])
                    i += 2
                    continue
                }
                if (c == quote) quote = null
                i++
                continue
            }
            when (c) {
                '\'', '"' -> {
                    quote = c
                    sb.append(c)
                }
                '(', '[', '{' -> {
                    depth++
                    sb.append(c)
                }
                ')', ']', '}' -> {
                    if (depth == 0 && c == ')') {
                        // 调用结束，收尾后返回
                        args.add(sb.toString().trim())
                        return args.getOrNull(index)?.trim('\'', '"')?.trim()
                    }
                    depth--
                    sb.append(c)
                }
                ',' -> if (depth == 0) {
                    args.add(sb.toString().trim())
                    sb.setLength(0)
                } else {
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        args.add(sb.toString().trim())
        return args.getOrNull(index)?.trim('\'', '"')?.trim()
    }

    /**
     * 解析 postArg.proc 的 recommend 参数：
     * - `"总,支持,反对"` → 取 `支持 - 反对`（与网页端一致，不为负）
     * - 纯数字 → 直接使用
     */
    private fun parseRecommend(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return null
        val parts = value.split(',')
        if (parts.size >= 3) {
            val up = parts[1].trim().toIntOrNull() ?: 0
            val down = parts[2].trim().toIntOrNull() ?: 0
            return (up - down).coerceAtLeast(0).toString()
        }
        return (value.toIntOrNull() ?: return null).coerceAtLeast(0).toString()
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
