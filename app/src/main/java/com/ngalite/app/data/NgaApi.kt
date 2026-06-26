package com.ngalite.app.data

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
}
