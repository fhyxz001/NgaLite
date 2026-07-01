package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/**
 * Cookie 持久化。
 *
 * - 账号密码登录成功后，由 [NgaApi.login] 捕获的会话 Cookie 保存于此；
 * - 也支持手动粘贴 Cookie 字符串（兜底入口）。
 *
 * 存储 Cookie 头字符串（name=value; name2=value2），请求时直接拼到 Cookie 头。
 */
object CookieStore {

    private const val PREFS = "nga_prefs"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_ACCOUNT = "account_name"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun get(): String = prefs.getString(KEY_COOKIE, "") ?: ""

    fun save(cookie: String) {
        val trimmed = cookie.trim()
        prefs.edit().putString(KEY_COOKIE, trimmed).apply()
        NgaApi.setLoginCookies(trimmed)
    }

    /** 保存账号名（仅用于登录态展示，非登录凭证）。 */
    fun saveAccountName(name: String) {
        prefs.edit().putString(KEY_ACCOUNT, name.trim()).apply()
    }

    fun getAccountName(): String = prefs.getString(KEY_ACCOUNT, "") ?: ""

    fun clear() {
        prefs.edit().remove(KEY_COOKIE).remove(KEY_ACCOUNT).apply()
        NgaApi.clearCookies()
    }

    fun isLogin(): Boolean = get().isNotEmpty()
}
