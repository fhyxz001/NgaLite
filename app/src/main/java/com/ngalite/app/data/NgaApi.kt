package com.ngalite.app.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** NGA 网络请求：携带 Cookie + 桌面 UA，按 GBK 解码 */
object NgaApi {

    private const val BASE = "https://bbs.nga.cn"
    internal const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
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
            .header("Cookie", CookieStore.get())
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
        val body = loginResp.body?.string().orEmpty()
        val httpCode = loginResp.code
        loginResp.close()
        if (httpCode !in 200..299) throw LoginException("网络错误 HTTP $httpCode")

        val (uid, token, username) = parseLoginResult(body)

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
     */
    private fun parseLoginResult(body: String): Triple<String, String, String> {
        val json = extractJson(body) ?: throw LoginException("登录响应解析失败")
        // error 字段：["错误信息", "错误码"]
        val errorMatch = Regex(""""error"\s*:\s*\[(.*?)]""").find(json)
        if (errorMatch != null) {
            val msg = Regex(""""([^"]*)"""").find(errorMatch.groupValues[1])?.groupValues?.lastOrNull() ?: ""
            throw LoginException(msg.ifBlank { "登录失败，请稍后重试" })
        }
        // data = [ ... ]，用括号配平提取，避免数组内嵌 ] 被非贪婪正则提前截断
        val dataKeyIdx = json.indexOf(""""data"""")
        if (dataKeyIdx < 0) throw LoginException("登录响应解析失败")
        val dataArr = extractArrayAfter(json, dataKeyIdx)
            ?: throw LoginException("登录响应解析失败")
        // data[3] = {"uid":xxxxx,"token":"xxxxx","username":"...", ...}，本身是个对象
        val item3 = extractNthObject(dataArr, 3) ?: throw LoginException("登录响应解析失败")
        val uid = Regex(""""uid"\s*:\s*"?(\d+)"""").find(item3)?.groupValues?.lastOrNull()
            ?: throw LoginException("未获取到 uid")
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(item3)?.groupValues?.lastOrNull()
            ?: throw LoginException("未获取到 token")
        val username = Regex(""""username"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(item3)
            ?.groupValues?.lastOrNull()?.replace("\\\"", "\"").orEmpty()
        return Triple(uid, token, username)
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

    /** 登录失败异常，携带服务端/可读错误信息。 */
    class LoginException(message: String) : RuntimeException(message)

    /** 临时 CookieJar，仅用于捕获登录流程中服务端下发的 Set-Cookie。 */
    private class MemCookieJar : CookieJar {
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
