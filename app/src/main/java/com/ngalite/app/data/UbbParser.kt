package com.ngalite.app.data

/** 解析 UBB 标签，将原始正文拆分为 ContentNode 列表 */
object UbbParser {

    private const val IMG_BASE = "https://img.nga.178.com/attachments/"

    /** 匹配 [s:ac:blink] / [s:a2:xxx] 格式的表情标签 */
    private val EMOJI_REGEX = Regex("""\[s:(ac|a2):([^\]]+)]""")

    /** 匹配回复元信息：[b]Reply to [pid=...]Reply[/pid] Post by [uid=...]用户名[/uid] (日期)[/b] */
    private val REPLY_META_REGEX = Regex(
        """\[b]\s*Reply to\s*\[pid=[^\]]*]Reply\[/pid]\s*Post by\s*\[uid=[^\]]*](.*?)\[/uid]\s*\([^)]*\)\s*\[/b]""",
        RegexOption.IGNORE_CASE
    )

    /** 解析正文，返回 ContentNode 列表 */
    fun parse(content: String): List<ContentNode> {
        val nodes = mutableListOf<ContentNode>()
        var remaining = preprocessReplyMeta(content)

        while (remaining.isNotEmpty()) {
            // 找下一个标签
            val imgStart = remaining.indexOf("[img]", ignoreCase = true)
            val imgEnd = remaining.indexOf("[/img]", ignoreCase = true)
            val quoteStart = remaining.indexOf("[quote]", ignoreCase = true)
            val quoteEnd = remaining.indexOf("[/quote]", ignoreCase = true)
            val emojiMatch = EMOJI_REGEX.find(remaining)

            // 找出最先出现的标签
            val candidates = mutableListOf<TagCandidate>()

            if (imgStart != -1 && imgEnd != -1 && imgStart < imgEnd) {
                candidates.add(TagCandidate("img", imgStart, imgEnd + 6))
            }
            if (quoteStart != -1 && quoteEnd != -1 && quoteStart < quoteEnd) {
                candidates.add(TagCandidate("quote", quoteStart, quoteEnd + 8))
            }
            if (emojiMatch != null) {
                candidates.add(TagCandidate("emoji", emojiMatch.range.first, emojiMatch.range.last + 1))
            }

            if (candidates.isEmpty()) {
                // 没有更多标签，剩余部分作为文本（清理残留 UBB 标签）
                val textRest = stripUbbTags(remaining)
                if (textRest.isNotBlank()) {
                    nodes.add(ContentNode.Text(textRest))
                }
                break
            }

            // 选最先出现的标签
            val earliest = candidates.minBy { it.start }

            // 标签前的文本（清理残留 UBB 标签）
            if (earliest.start > 0) {
                val textBefore = stripUbbTags(remaining.substring(0, earliest.start))
                if (textBefore.isNotBlank()) {
                    nodes.add(ContentNode.Text(textBefore))
                }
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
                "emoji" -> {
                    val match = emojiMatch!!
                    nodes.add(ContentNode.Emoji(match.groupValues[1], match.groupValues[2]))
                    remaining = remaining.substring(match.range.last + 1)
                }
            }
        }

        return nodes
    }

    /** 预处理回复元信息，将 [b]Reply to ... Post by ... (date)[/b] 格式化为 "回复 用户名。" */
    private fun preprocessReplyMeta(content: String): String {
        return REPLY_META_REGEX.replace(content) { match ->
            val username = match.groupValues[1].trim()
            "回复 $username。"
        }
    }

    /** 去除文本中的 UBB 标签（保留标签内文字内容），不影响 [img]/[quote]/[s:ac:xxx] 等已解析标签 */
    private fun stripUbbTags(text: String): String {
        return text.replace(Regex("""\[/?[a-z]+\w*(?:=[^\]]*)?]"""), "")
    }

    /** 去除 NGA 引用开头的元信息标签，简化可读文本 */
    private fun cleanQuoteMeta(quote: String): String {
        var cleaned = quote
        // 1. 去除 [pid=...]Reply[/pid]（无阅读价值）
        cleaned = cleaned.replace(Regex("""\[pid=[^\]]*]Reply\[/pid]"""), "")
        // 2. 处理 [b]...[/b] 块：剥去内部 UBB 标签，去除日期和引用前缀，保留可读文字
        cleaned = cleaned.replace(Regex("""\[b](.*?)\[/b]""")) { match ->
            match.groupValues[1]
                .replace(Regex("""\[/?[a-z]+\w*(?:=[^\]]*)?]"""), "")  // 剥去 UBB 标签
                .replace(Regex("""\s*\(\d{4}-\d{2}-\d{2}\s*\d{2}:\d{2}\)"""), "") // 去除日期
                .replace(Regex("""\s*:\s*$"""), "") // 去除末尾冒号
                .replace(Regex("""(?i)\bPost by\b"""), "") // 去除 "Post by" 标记
                .replace(Regex("""(?i)\bReply to\b"""), "回复") // "Reply to" → "回复"
                .trim()
        }
        // 3. 去除剩余孤立的 UBB 标签
        cleaned = cleaned.replace(Regex("""\[/?[a-z]+\w*(?:=[^\]]*)?]"""), "")
        // 4. 规范化空白（去除多余空格和空行）
        cleaned = cleaned.replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
        return cleaned.trim()
    }

    private data class TagCandidate(
        val type: String,
        val start: Int,
        val end: Int
    )
}
