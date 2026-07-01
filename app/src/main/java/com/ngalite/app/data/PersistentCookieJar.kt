package com.ngalite.app.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 持久化 CookieJar：自动保存服务端下发的 Cookie，并在后续请求中携带。
 *
 * - 未登录时，NGA 会下发访客 Cookie，持久化后可避免 403。
 * - 登录后，由 [CookieStore] 保存的登录 Cookie 也会通过 [saveCookies] 注入到这里。
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        prefs.all.forEach { (key, value) ->
            val host = key.removePrefix(HOST_PREFIX)
            val cookies = (value as? String)
                ?.split("; ")
                ?.mapNotNull { parseCookie(it, host) }
                ?: emptyList()
            store[host] = cookies.toMutableList()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host, ::mutableListOf)
        cookies.forEach { new ->
            list.removeAll { it.name == new.name && it.domain == new.domain }
            list.add(new)
        }
        persist(host, list)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = list.filter { it.expiresAt > now }
        if (valid.size != list.size) {
            store[url.host] = valid.toMutableList()
            persist(url.host, valid)
        }
        return valid
    }

    /** 直接注入一串 Cookie（用于登录成功后把登录态写入 jar）。 */
    fun saveCookies(url: HttpUrl, cookieHeader: String) {
        val cookies = cookieHeader.split(";")
            .map { it.trim() }
            .mapNotNull { parseCookie(it, url.host) }
        saveFromResponse(url, cookies)
    }

    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }

    private fun persist(host: String, cookies: List<Cookie>) {
        val value = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        prefs.edit().putString("$HOST_PREFIX$host", value).apply()
    }

    private fun parseCookie(str: String, host: String): Cookie? {
        val eq = str.indexOf('=')
        if (eq <= 0) return null
        val name = str.substring(0, eq).trim()
        val value = str.substring(eq + 1).trim()
        if (name.isBlank() || value.isBlank()) return null
        return Cookie.Builder()
            .name(name)
            .value(value)
            .domain(host)
            .path("/")
            .build()
    }

    companion object {
        private const val PREFS_NAME = "nga_persistent_cookies"
        private const val HOST_PREFIX = "host_"
    }
}
