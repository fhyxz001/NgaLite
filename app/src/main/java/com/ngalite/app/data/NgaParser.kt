package com.ngalite.app.data

import org.jsoup.Jsoup

/** 用 Jsoup 解析 NGA 的 GBK HTML，提取帖子列表与详情 */
object NgaParser {

    private val TID_REGEX = Regex("""tid=(\d+)""")

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
    fun parsePosts(html: String): List<Post> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr.postrow")
        return rows.mapIndexedNotNull { index, row ->
            val author = row.selectFirst("[id^=postauthor]")?.text()?.trim() ?: ""
            val date = row.selectFirst("[id^=postdate]")?.text()?.trim() ?: ""
            val contentEl = row.selectFirst("[id^=postcontent]") ?: return@mapIndexedNotNull null
            val content = htmlToText(contentEl.html())
            val floor = row.selectFirst("a[name^=l]")?.text()?.trim() ?: "#$index"
            Post(floor, author, date, content)
        }
    }

    /** HTML 转纯文本：保留换行，剥离标签与实体 */
    private fun htmlToText(html: String): String {
        return html
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</p>".toRegex(), "\n\n")
            .replace("(?i)</div>".toRegex(), "\n")
            .replace("(?i)<img[^>]*>".toRegex(), "[图]")
            .replace("(?i)<[^>]+>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("\n{3,}".toRegex(), "\n\n")
            .trim()
    }
}
