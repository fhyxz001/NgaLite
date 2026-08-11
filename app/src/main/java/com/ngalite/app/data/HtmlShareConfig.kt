package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/**
 * HTML 分享服务配置：
 *
 * - 选择分享服务：配置1 pad.genwebapp.com（默认）/ 配置2 htmlto.link；
 * - 可选配置 htmlto.link 站点 Cookie，配置后生成的分享链接有效期由 1 天提升到 3 天。
 */
object HtmlShareConfig {

    /** 分享服务配置项 */
    enum class Provider(val id: Int, val displayName: String) {
        PAD(1, "配置1 · Pad（pad.genwebapp.com）"),
        HTMLTO(2, "配置2 · HTML To Link（htmlto.link）"),
    }

    private const val PREFS = "nga_prefs"
    private const val KEY_PROVIDER = "html_share_provider"
    private const val KEY_HTMLTO_COOKIE = "htmlto_link_cookie"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 当前分享服务，未配置时默认使用配置1（pad） */
    fun provider(): Provider =
        when (prefs.getInt(KEY_PROVIDER, Provider.PAD.id)) {
            Provider.HTMLTO.id -> Provider.HTMLTO
            else -> Provider.PAD
        }

    fun setProvider(provider: Provider) {
        prefs.edit().putInt(KEY_PROVIDER, provider.id).apply()
    }

    /** htmlto.link 站点 Cookie（可选），配置后分享链接有效期延长至 3 天 */
    fun htmltoCookie(): String = prefs.getString(KEY_HTMLTO_COOKIE, "") ?: ""

    fun setHtmltoCookie(cookie: String) {
        prefs.edit().putString(KEY_HTMLTO_COOKIE, cookie.trim()).apply()
    }
}
