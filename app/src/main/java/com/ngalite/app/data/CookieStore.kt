package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/** Cookie 持久化：用户粘贴的 Cookie 字符串保存到 SharedPreferences */
object CookieStore {

    private const val PREFS = "nga_prefs"
    private const val KEY_COOKIE = "cookie"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun get(): String = prefs.getString(KEY_COOKIE, "") ?: ""

    fun save(cookie: String) {
        prefs.edit().putString(KEY_COOKIE, cookie.trim()).apply()
    }

    fun isLogin(): Boolean = get().isNotEmpty()
}
