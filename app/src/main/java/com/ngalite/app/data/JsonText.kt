package com.ngalite.app.data

/**
 * NGA 返回的"JSON"多为 `window.script_muti_get_var_store={...}` 或内嵌用户表这类简单字面量，
 * 这里提供轻量取值与反转义工具，避免为了几个字段引入完整的 JSON 解析器。
 */
internal object JsonText {

    /**
     * 取出 `"key":"value"` 形式的字符串字段。
     *
     * - 值可能是 `null`（返回空串）
     * - 值里的 `\"`、`\\`、`\uXXXX` 会被反转义
     * - 找不到键时返回空串
     */
    fun stringField(text: String, key: String): String {
        val token = "\"$key\""
        var searchFrom = 0
        while (true) {
            val keyIndex = text.indexOf(token, searchFrom)
            if (keyIndex < 0) return ""
            var i = text.indexOf(':', keyIndex + token.length)
            if (i < 0) return ""
            i++
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) return ""
            if (text.startsWith("null", i)) {
                // 该键是 null，继续找后面可能出现的同名键
                searchFrom = i + 4
                continue
            }
            if (text[i] == '"') {
                val sb = StringBuilder()
                var j = i + 1
                while (j < text.length) {
                    val c = text[j]
                    if (c == '\\' && j + 1 < text.length) {
                        sb.append(c).append(text[j + 1])
                        j += 2
                        continue
                    }
                    if (c == '"') return unescape(sb.toString()).trim()
                    sb.append(c)
                    j++
                }
                return ""
            }
            // 该键后面不是字符串（例如对象/数组），继续找下一个同名键
            searchFrom = keyIndex + token.length
        }
    }

    /** 还原 JSON/JS 字符串中的转义（\" \\ \n \t \uXXXX 等） */
    fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                sb.append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> {
                    val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                    val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 4
                    } else {
                        sb.append(next)
                    }
                }
                else -> sb.append(next)
            }
            i += 2
        }
        return sb.toString()
    }
}
