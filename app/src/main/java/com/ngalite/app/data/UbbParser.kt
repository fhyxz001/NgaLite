package com.ngalite.app.data

/** 解析 UBB 标签，将原始正文拆分为 ContentNode 列表 */
object UbbParser {

    private const val IMG_BASE = "https://img.nga.178.com/attachments/"

    /** 解析正文，返回 ContentNode 列表 */
    fun parse(content: String): List<ContentNode> {
        val nodes = mutableListOf<ContentNode>()
        var remaining = content

        while (remaining.isNotEmpty()) {
            // 找下一个标签
            val imgStart = remaining.indexOf("[img]", ignoreCase = true)
            val imgEnd = remaining.indexOf("[/img]", ignoreCase = true)
            val quoteStart = remaining.indexOf("[quote]", ignoreCase = true)
            val quoteEnd = remaining.indexOf("[/quote]", ignoreCase = true)

            // 找出最先出现的标签
            val candidates = mutableListOf<TagCandidate>()

            if (imgStart != -1 && imgEnd != -1 && imgStart < imgEnd) {
                candidates.add(TagCandidate("img", imgStart, imgEnd + 6))
            }
            if (quoteStart != -1 && quoteEnd != -1 && quoteStart < quoteEnd) {
                candidates.add(TagCandidate("quote", quoteStart, quoteEnd + 8))
            }

            if (candidates.isEmpty()) {
                // 没有更多标签，剩余部分作为文本
                nodes.add(ContentNode.Text(remaining))
                break
            }

            // 选最先出现的标签
            val earliest = candidates.minBy { it.start }

            // 标签前的文本
            if (earliest.start > 0) {
                nodes.add(ContentNode.Text(remaining.substring(0, earliest.start)))
            }

            when (earliest.type) {
                "img" -> {
                    val path = remaining.substring(imgStart + 5, imgEnd)
                    val cleanPath = path.removePrefix("./").removePrefix("/")
                    val url = IMG_BASE + cleanPath
                    nodes.add(ContentNode.Image(url))
                    remaining = remaining.substring(imgEnd + 6)
                }
                "quote" -> {
                    val rawQuote = remaining.substring(quoteStart + 7, quoteEnd)
                    val cleaned = cleanQuoteMeta(rawQuote)
                    nodes.add(ContentNode.Quote(cleaned))
                    remaining = remaining.substring(quoteEnd + 8)
                }
            }
        }

        return nodes
    }

    /** 去除 NGA 引用开头的元信息标签 */
    private fun cleanQuoteMeta(quote: String): String {
        var cleaned = quote
        // 去除 [pid=...]Reply[/pid]
        cleaned = cleaned.replace(Regex("""\[pid=[^\]]*]Reply\[/pid]\s*"""), "")
        // 去除 [b]Post by [uid=...]xxx[/uid] (yyyy-MM-dd HH:mm):[/b]
        cleaned = cleaned.replace(Regex("""\[b]Post by \[uid=[^\]]*][^\[]*\[/uid]\s*\(\d{4}-\d{2}-\d{2}\s*\d{2}:\d{2}\)\s*:\s*\[/b]\s*"""), "")
        return cleaned.trim()
    }

    private data class TagCandidate(
        val type: String,
        val start: Int,
        val end: Int
    )
}
