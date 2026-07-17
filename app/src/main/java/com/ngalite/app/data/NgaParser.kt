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
            val previewImages = extractPreviewImages(r