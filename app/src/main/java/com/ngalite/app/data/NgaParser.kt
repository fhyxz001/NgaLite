package com.ngalite.app.data

import org.jsoup.Jsoup

/** 用 Jsoup 解析 NGA 的 GBK HTML，提取帖子列表与详情 */
object NgaParser {

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
            if (title.isEmpty()) null else Topic(tid, title, replies, author, replyTime)
        }
    }

    /** 解析帖子详情（楼层列表） */
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

    /** 从楼层行解析浏览数 */
    private fun parseViews(row: org.jsoup.nodes.Element): String {
        row.select("[id^=postview]").firstOrNull()?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        val text = row.text()
        val match = VIEWS_REGEX.find(text)
        if (match != null) return match.groupValues[1]
        return "0"
    }

    /** 从楼层行解析点赞数 */
    private fun parseLikes(row: org.jsoup.nodes.Element): String {
        row.select("[id^=likes_num]").firstOrNull()?.text()?.trim()?.let { num ->
            val cleaned = num.replace(NUM_CLEAN, "")
            if (cleaned.isNotBlank()) return cleaned
        }
        val likeText = row.text()
        val match = LIKES_REGEX.find(likeText)
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
