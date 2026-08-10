package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/** NGA 站点域名配置：设置页可选择 bbs.nga.cn / ngabbs.com 作为请求 BASE。 */
object BaseConfig {

    /** 可选域名（顺序即设置页展示顺序），首个为默认 */
    val DOMAINS = listOf("bbs.nga.cn", "ngabbs.com")

    private const val PREFS = "nga_prefs"
    private const val KEY_BASE = "base_domain"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 当前选择的域名，未配置时默认 bbs.nga.cn */
    fun domain(): String = prefs.getString(KEY_BASE, DOMAINS.first()) ?: DOMAINS.first()

    fun setDomain(domain: String) {
        if (domain in DOMAINS) prefs.edit().putString(KEY_BASE, domain).apply()
    }

    /** 当前 BASE URL（含 https 协议） */
    val baseUrl: String
        get() = "https://${domain()}"
}
