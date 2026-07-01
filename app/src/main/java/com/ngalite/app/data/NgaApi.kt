package com.ngalite.app.data

import com.ngalite.app.NgaApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** NGA 网络请求：携带 Cookie + 桌面 UA，按 GBK 解码 */
object NgaApi {

    private const val BASE = "https://bbs.nga.cn"
    internal const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val cookieJar by lazy { PersistentCookieJar(NgaApp.instance) }

    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** 把登录成功后得到的 Cookie 头字符串注入到持久化 jar，供后续请求携带。 */
    fun setLoginCookies(cookieHeader: String) {
        val url = "$BASE/".toHttpUrlOrNull() ?: return
        cookieJar.saveCookies(url, cookieHeader)
    }

    /** 清空持久化 jar 中的 Cookie。 */
    fun clearCookies() {
        cookieJar.clear()
    }

    /** 拉取版块帖子列表 HTML */
    fun fetchThreadList(fid: String, page: Int = 1): String =
        fetch("$BASE/thread.php?fid=$fid&page=$page")

    /** 拉取帖子详情 HTML */
    fun fetchThread(tid: String): String =
        fetch("$BASE/read.php?tid=$tid")

    private fun fetch(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Charset", "GBK")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: return ""
            return String(bytes, Charset.forName("GBK"))
        }
    }

    /**
     * 账号密码登录，返回成功后的 Cookie 头字符串（name=value; ...）。
     *
     * 流程对应网页端 [account_copy] 的 _loginUI → _submit：
     * 1. POST /nuke.php（__lib=login&__act=login）提交账号/类型/RSA 加密后的密码；
     *    响应为 `window.script_muti_get_var_store={...}`，成功时 data[3] 含 uid 与 token。
     * 2. 用拿到的 uid + token 调 login_set_cookie_quick（对应网页端 loginSuccess 后
     *    父页向各域名提交表单设 cookie），用内存 CookieJar 捕获 Set-Cookie，
     *    拼成可持久化的 Cookie 头字符串。
     *
     * [account_copy]: 账号操作_files/account_copy.html
     *
     * @param type 账号类型：""=用户名/昵称, "mail"=邮箱, "id"=用户ID, "phone"=手机号
     * @throws LoginException 登录失败（账号密码错误、需验证码、服务端报错等）
     */
    fun login(name: String, type: String, password: String): LoginResult {
        // 1. 提交登录表单
        val encPwd = RsaCipher.encrypt(password.trim())
        val form = FormBody.Builder(null)
            .add("__lib", "login")
            .add("__output", "1")
            .add("__act", "login")
            .add("name", name.trim())
            .add("type", type)
            .add("password", encPwd)
            .add("__inchst", "UTF-8")
            .build()
        val loginReq = Request.Builder()
            .url("$BASE/nuke.php")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/nuke.php?__lib=login&__act=account&login")
            .post(form)
            .build()

        val jar = MemCookieJar()
        val loginClient = OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val loginResp = loginClient.newCall(loginReq).execute()
        val bodyBytes = loginResp.body?.bytes() ?: ByteArray(0)
        val body = String(bodyBytes, Charset.forName("GBK"))
        val httpCode = loginResp.code
        loginResp.close()
        if (httpCode !in 200..299) throw LoginException("网络错误 HTTP $httpCode")

        val (uid, token, username) = parseLoginResult(body, bodyBytes)

        // 2. 用 uid/token 设置会话 cookie（捕获 Set-Cookie）
        val setForm = FormBody.Builder(null)
            .add("uid", uid)
            .add("cid", token)
            .build()
        val setReq = Request.Builder()
            .url("$BASE/nuke.php?__lib=login&__act=login_set_cookie_quick&__output=9")
            .header("User-Agent", UA)
            .header("Referer", "$BASE/nuke.php?__lib=login&__act=account&login")
            .post(setForm)
            .build()
        loginClient.newCall(setReq).execute().close()

        val cookieHeader = jar.cookieHeader()
        val cookie = if (cookieHeader.isBlank()) {
            // login_set_cookie_quick 偶尔不直接回 Set-Cookie，退回用 uid/token 构造最小 cookie
            "ngaPassportUid=$uid; ngaPassportCid=$token"
        } else {
            cookieHeader
        }
        return LoginResult(cookie = cookie, username = username)
    }

    /** 登录结果：会话 Cookie 头字符串 + 用户名（可能为空）。 */
    data class LoginResult(val cookie: String, val username: String)

    /**
     * 解析 /nuke.php 登录响应：`window.script_muti_get_var_store={json}` 或纯 JSON。
     * 成功返回 (uid, token, username)，失败抛 [LoginException]。
     *
     * 注意 NGA 登录响应实际为 GBK 编码（Content-Type: text/javascript; charset=GBK），
     * 调用方需按 GBK 解码后再传入。
     */
    private fun parseLoginResult(body: String, rawBytes: ByteArray): Triple<String, String, String> {
        fun err(detail: String): String {
            val preview = body.take(500)
            val hex = rawBytes.take(200).joinToString(" ") { "%02x".format(it) }
            return "$detail\n响应预览: $preview\nHex: $hex"
        }
        val json = extractJson(body) ?: throw LoginException(err("登录响应解析失败"))
        // error 字段：网页端用 y.error[0]/y.error[1] 读取，既可能是数组 ["信息","码"]，
        // 也可能是对象 {"0":"信息","1":"码"}（实测验证码场景即为对象）。两种都要识别。
        val errorMsg = extractError(json)
        if (errorMsg != null) throw LoginException(errorMsg)

        // data 字段：与 error 一样既可能是数组 [...] 也可能是对象 {...}。
        val dataKeyIdx = json.indexOf(""""data"""")
        if (dataKeyIdx < 0) throw LoginException(err("未找到 data 字段"))
        val dataStart = skipToValue(json, dataKeyIdx)
        val item3 = when {
            dataStart == '[' -> {
                val dataArr = extractArrayAfter(json, dataKeyIdx)
                    ?: throw LoginException(err("data 数组解析失败"))
                extractNthObject(dataArr, 3)
            }
            dataStart == '{' -> {
                val dataObj = extractObjectAfter(json, dataKeyIdx)
                    ?: throw LoginException(err("data 对象解析失败"))
                extractValueOfKey(dataObj, "3")?.let { v ->
                    // v 是 "3" 对应的值，可能以 { 开头（对象）或以数字/字符串开头
                    val objStart = v.indexOf('{')
                    if (objStart >= 0) v.substring(objStart) else v
                }
            }
            else -> null
        } ?: throw LoginException(err("未找到 data[3]"))
        val uid = Regex(""""uid"\s*:\s*"?(\d+)""").find(item3)?.groupValues?.lastOrNull()
            ?: throw LoginException("未获取到 uid")
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(item3)?.groupValues?.lastOrNull()
            ?: throw LoginException("未获取到 token")
        val username = Regex(""""username"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(item3)
            ?.groupValues?.lastOrNull()?.replace("\\\"", "\"").orEmpty()
        return Triple(uid, token, username)
    }

    /**
     * 从 JSON 文本中提取 error 的可读信息（兼容数组与对象两种形态）。
     * - 数组：`"error":["信息","码"]` → 取第一个字符串元素
     * - 对象：`"error":{"0":"信息","1":"码"}` → 取 "0" 或第一个字符串值
     * 返回 null 表示没有 error 字段。
     */
    private fun extractError(json: String): String? {
        val keyIdx = json.indexOf(""""error"""")
        if (keyIdx < 0) return null
        // 跳过 "error": 后的空白，找到值起始字符
        var i = json.indexOf(':', keyIdx) + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        return when (json[i]) {
            '[' -> {
                val arr = extractArrayAfter(json, keyIdx) ?: return null
                // 取数组里第一个 "..." 字符串
                Regex(""""([^"]*)"""").find(arr)?.groupValues?.lastOrNull()
            }
            '{' -> {
                val obj = extractObjectAfter(json, keyIdx) ?: return null
                // 优先取 "0" 字段，否则取第一个字符串值
                Regex(""""0"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(obj)?.groupValues?.lastOrNull()
                    ?: Regex(""":"((?:[^"\\]|\\.)*)"""").find(obj)?.groupValues?.lastOrNull()
            }
            else -> null
        }?.replace("\\\"", "\"")?.ifBlank { null }
    }

    /** 从 [from] 位置之后找到 `"error":` 等键对应的对象并按括号配平截取。 */
    private fun extractObjectAfter(s: String, from: Int): String? {
        val objStart = s.indexOf('{', from)
        if (objStart < 0) return null
        var depth = 0
        var i = objStart
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(objStart + 1, i)
                }
            }
            i++
        }
        return null
    }

    /** 从 [from] 位置之后找到 `"data":` 对应的数组并按括号配平截取其内容。 */
    private fun extractArrayAfter(s: String, from: Int): String? {
        val arrStart = s.indexOf('[', from)
        if (arrStart < 0) return null
        var depth = 0
        var i = arrStart
        while (i < s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return s.substring(arrStart + 1, i)
                }
            }
            i++
        }
        return null
    }

    /** 从 `window.script_muti_get_var_store=...` 或纯 JSON 中取出 JSON 对象文本。 */
    private fun extractJson(body: String): String? {
        val raw = body.trim()
        if (raw.startsWith("{")) return raw
        val marker = "window.script_muti_get_var_store"
        val start = raw.indexOf(marker)
        if (start < 0) return null
        val eq = raw.indexOf('=', start)
        if (eq < 0) return null
        // 网页端会 eval 整个赋值表达式，值即 JSON 对象
        return raw.substring(eq + 1).trim().takeIf { it.startsWith("{") }
    }

    /** 从一个 JSON 数组字符串中取第 [index] 个对象元素（粗略匹配大括号）。 */
    private fun extractNthObject(arr: String, index: Int): String? {
        var depth = 0
        var count = 0
        var start = -1
        for (i in arr.indices) {
            val c = arr[i]
            if (c == '{') {
                if (depth == 0) {
                    if (count == index) start = i
                    count++
                }
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start != -1) {
                    return arr.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** 跳过 `"key":` 找到值的起始字符，返回该字符（`[`/`{`/`"`/数字等）或 null。 */
    private fun skipToValue(s: String, keyEnd: Int): Char? {
        var i = s.indexOf(':', keyEnd) + 1
        while (i < s.length && s[i].isWhitespace()) i++
        return s.getOrNull(i)
    }

    /**
     * 在 JSON 对象文本 [obj]（不含外层花括号）中，取出键 [key] 对应的值子串。
     * 支持值为字符串、数字、对象、数组。
     */
    private fun extractValueOfKey(obj: String, key: String): String? {
        val pat = """"$key"\s*:\s*"""
        val m = Regex(pat).find(obj) ?: return null
        val start = m.range.last + 1
        if (start >= obj.length) return null
        val c = obj[start]
        return when (c) {
            '"' -> {
                // 字符串值：找到结束引号
                var i = start + 1
                while (i < obj.length) {
                    if (obj[i] == '\\') { i += 2; continue }
                    if (obj[i] == '"') return obj.substring(start, i + 1)
                    i++
                }
                null
            }
            '{' -> {
                var depth = 0
                var i = start
                while (i < obj.length) {
                    when (obj[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return obj.substring(start, i + 1)
                        }
                    }
                    i++
                }
                null
            }
            '[' -> {
                var depth = 0
                var i = start
                while (i < obj.length) {
                    when (obj[i]) {
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) return obj.substring(start, i + 1)
                        }
                    }
                    i++
                }
                null
            }
            else -> {
                // 数字或布尔值：读到逗号或结束花括号
                val end = obj.indexOfFirst { it in setOf(',', '}') }.let { if (it < 0) obj.length else it }
                obj.substring(start, end).trim().takeIf { it.isNotEmpty() }
            }
        }
    }

    /** 登录失败异常，携带服务端/可读错误信息。 */
    class LoginException(message: String) : RuntimeException(message)

    /** 判断错误信息是否表示需要图形验证码。 */
    fun isCaptchaError(message: String?): Boolean {
        if (message == null) return false
        return "验证码" in message || "captcha" in message.lowercase()
    }

    /**
     * 图形验证码会话：保持同一 cookie 会话，用于先加载验证码图片再提交登录。
     *
     * 用法：
     * 1. 构造 [CaptchaSession]（自动生成 captchaId 和共享 OkHttpClient）
     * 2. 用 [imageUrl] 加载验证码图片（或用 [fetchImageBytes] 直接拉取字节）
     * 3. 用户输入验证码后调用 [login]
     * 4. 验证码错误时可调用 [refresh] 换一张图
     */
    class CaptchaSession(private val from: String = "login") {

        var captchaId: String = generateCaptchaId()
            private set

        private val jar = MemCookieJar()
        private val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val imageUrl: String
            get() = "$BASE/login_check_code.php?id=$captchaId&from=$from"

        fun refresh() {
            captchaId = generateCaptchaId()
        }

        /** 下载验证码图片字节（使用会话 cookie）。 */
        fun fetchImageBytes(): ByteArray {
            val req = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", UA)
                .header("Referer", "$BASE/nuke.php?__lib=login&__act=account&login")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("验证码加载失败 HTTP ${resp.code}")
                return resp.body?.bytes() ?: throw RuntimeException("验证码图片为空")
            }
        }

        /**
         * 使用图形验证码登录。
         *
         * @param captchaText 用户输入的验证码文本（通常 6 个字符）
         */
        fun login(name: String, type: String, password: String, captchaText: String): LoginResult {
            val encPwd = RsaCipher.encrypt(password.trim())
            val prid = "P" + Random.nextDouble().toString().substring(2)
            val form = FormBody.Builder(null)
                .add("__lib", "login")
                .add("__output", "1")
                .add("__act", "login")
                .add("name", name.trim())
                .add("type", type)
                .add("password", encPwd)
                .add("rid", captchaId)
                .add("captcha", captchaText.trim())
                .add("prid", prid)
                .add("__inchst", "UTF-8")
                .build()

            val loginReq = Request.Builder()
                .url("$BASE/nuke.php")
                .header("User-Agent", UA)
                .header("Referer", "$BASE/nuke.php?__lib=login&__act=account&login")
                .post(form)
                .build()

            val loginResp = client.newCall(loginReq).execute()
            val bodyBytes = loginResp.body?.bytes() ?: ByteArray(0)
            val body = String(bodyBytes, Charset.forName("GBK"))
            val httpCode = loginResp.code
            loginResp.close()
            if (httpCode !in 200..299) throw LoginException("网络错误 HTTP $httpCode")

            val (uid, token, username) = parseLoginResult(body, bodyBytes)

            val setForm = FormBody.Builder(null)
                .add("uid", uid)
                .add("cid", token)
                .build()
            val setReq = Request.Builder()
                .url("$BASE/nuke.php?__lib=login&__act=login_set_cookie_quick&__output=9")
                .header("User-Agent", UA)
                .header("Referer", "$BASE/nuke.php?__lib=login&__act=account&login")
                .post(setForm)
                .build()
            client.newCall(setReq).execute().close()

            val cookieHeader = jar.cookieHeader()
            val cookie = if (cookieHeader.isBlank()) {
                "ngaPassportUid=$uid; ngaPassportCid=$token"
            } else {
                cookieHeader
            }
            return LoginResult(cookie = cookie, username = username)
        }

        private fun generateCaptchaId(): String =
            from + Random.nextDouble().toString().substring(2)
    }

    /** 临时 CookieJar，用于捕获登录流程中服务端下发的 Set-Cookie。 */
    internal class MemCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()

        /** 把捕获到的 cookie 拼成请求头格式的字符串。 */
        fun cookieHeader(): String =
            store.distinctBy { it.name }
                .filter { it.name.isNotBlank() }
                .joinToString("; ") { "${it.name}=${it.value}" }
    }
}
