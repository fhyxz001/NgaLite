package com.ngalite.app.data

/** 解析 UBB 标签，将原始正文拆分为 ContentNode 列表 */
object UbbParser {

    private const val IMG_BASE = "https://img.nga.178.com/attachments/"
    private const val T_IMG = 0
    private const val T_QUOTE = 1
    private const val T_EMOJI = 2
    private const val IMG_PREFIX = "[img]"
    private const val IMG_SUFFIX = "[/img]"
    private const val QUOTE_PREFIX = "[quote]"
    private const val QUOTE_SUFFIX = "[/quote]"

    /** 匹配 [s:ac:blink] / [s:a2:xxx] / [s:ng:xxx] / [s:pst:xxx] / [s:dt:xxx] / [s:pg:xxx] 格式的表情标签 */
    private val EMOJI_REGEX = Regex("""\[s:(ac|a2|ng|pst|dt|pg):([^\]]+)]""")

    /** 匹配回复元信息：[b]Reply to [pid=...]Reply[/pid] Post by [uid=...]用户名[/uid] (日期)[/b] */
    private val REPLY_META_REGEX = Regex(
        """\[b]\s*Reply to\s*\[pid=[^\]]*]Reply\[/pid]\s*Post by\s*\[uid=[^\]]*](.*?)\[/uid]\s*\([^)]*\)\s*\[/b]""",
        RegexOption.IGNORE_CASE
    )

    private data class TagPos(val type: Int, val start: Int, val end: Int)

    private val STRIP_UBB = Regex("""\[/?[a-z]+\w*(?:=[^\]]*)?]""")
    private val PID_REPLY = Regex("""\[pid=[^\]]*]Reply\[/pid]""")
    private val BOLD_BLOCK = Regex("""\[b](.*?)\[/b]""")
    private val CLEAN_DATE = Regex("""\s*\(\d{4}-\d{2}-\d{2}\s*\d{2}:\d{2}\)""")
    private val CLEAN_TRAIL_COLON = Regex("""\s*:\s*$""")
    private val POST_BY = Regex("""(?i)\bPost by\b""")
    private val REPLY_TO = Regex("""(?i)\bReply to\b""")
    private val MULTI_SPACE = Regex("""[ \t]+""")
    private val MULTI_NEWLINE_UBB = Regex("""\n{3,}""")

    /** 解析正文，返回 ContentNode 列表 */
    fun parse(content: String): List<ContentNode> {
        val nodes = mutableListOf<ContentNode>()
        var remaining = preprocessReplyMeta(content)

        while (remaining.isNotEmpty()) {
            // 找下一个标签：一次性计算所有候选位置，不创建列表
            val imgStart = remaining.indexOf(IMG_PREFIX, ignoreCase = true)
            val imgEnd = if (imgStart >= 0) remaining.indexOf(IMG_SUFFIX, ignoreCase = true) else -1
            val quoteStart = remaining.indexOf(QUOTE_PREFIX, ignoreCase = true)
            val quoteEnd = if (quoteStart >= 0) remaining.indexOf(QUOTE_SUFFIX, ignoreCase = true) else -1
            val emojiMatch = EMOJI_REGEX.find(remaining)

            // 找最早出现的标签（无分配版本）
            val earliest = findEarliest(imgStart, imgEnd, quoteStart, quoteEnd, emojiMatch)

            if (earliest == null) {
                val textRest = stripUbbTags(remaining)
                if (textRest.isNotBlank()) {
                    nodes.add(ContentNode.Text(textRest))
                }
                break
            }

            // 标签前的文本
            if (earliest.start > 0) {
                val textBefore = stripUbbTags(remaining.substring(0, earliest.start))
                if (textBefore.isNotBlank()) {
                    nodes.add(ContentNode.Text(textBefore))
                }
            }

            when (earliest.type) {
                T_IMG -> {
                    val path = remaining.substring(imgStart + 5, imgEnd)
                    val cleanPath = path.removePrefix("./").removePrefix("/")
                    nodes.add(ContentNode.Image(IMG_BASE + cleanPath))
                    remaining = remaining.substring(imgEnd + 6)
                }
                T_QUOTE -> {
                    val rawQuote = remaining.substring(quoteStart + 7, quoteEnd)
                    nodes.add(ContentNode.Quote(cleanQuoteMeta(rawQuote)))
                    remaining = remaining.substring(quoteEnd + 8)
                }
                T_EMOJI -> {
                    val m = emojiMatch!! // safe: earliest exists
                    nodes.add(ContentNode.Emoji(m.groupValues[1], m.groupValues[2]))
                    remaining = remaining.substring(m.range.last + 1)
                }
            }
        }

        return nodes
    }

    /** 找出最先出现的标签，避免创建临时对象 */
    private fun findEarliest(
        imgStart: Int, imgEnd: Int,
        quoteStart: Int, quoteEnd: Int,
        emojiMatch: MatchResult?
    ): TagPos? {
        var best: TagPos? = null
        if (imgStart >= 0 && imgEnd > imgStart) {
            best = TagPos(T_IMG, imgStart, imgEnd + 6)
        }
        if (quoteStart >= 0 && quoteEnd > quoteStart && (best == null || quoteStart < best.start)) {
            best = TagPos(T_QUOTE, quoteStart, quoteEnd + 8)
        }
        if (emojiMatch != null) {
            val es = emojiMatch.range.first
            if (best == null || es < best.start) {
                best = TagPos(T_EMOJI, es, emojiMatch.range.last + 1)
            }
        }
        return best
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
        return text.replace(STRIP_UBB, "")
    }

    /** 去除 NGA 引用开头的元信息标签，简化可读文本 */
    private fun cleanQuoteMeta(quote: String): String {
        var cleaned = quote
        cleaned = cleaned.replace(PID_REPLY, "")
        cleaned = cleaned.replace(BOLD_BLOCK) { match ->
            match.groupValues[1]
                .replace(STRIP_UBB, "")
                .replace(CLEAN_DATE, "")
                .replace(CLEAN_TRAIL_COLON, "")
                .replace(POST_BY, "")
                .replace(REPLY_TO, "回复")
                .trim()
        }
        cleaned = cleaned.replace(STRIP_UBB, "")
        cleaned = cleaned.replace(MULTI_SPACE, " ")
            .replace(MULTI_NEWLINE_UBB, "\n\n")
        return cleaned.trim()
    }
}
