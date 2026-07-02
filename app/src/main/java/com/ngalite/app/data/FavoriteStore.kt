package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/** 收藏板块持久化：记录用户收藏的板块 FID 集合。首次启动时自动添加默认收藏。 */
object FavoriteStore {

    private const val PREFS = "nga_prefs"
    private const val KEY = "favorite_forums"
    private const val DEFAULTS_KEY = "favorites_defaults_applied"

    /** 默认收藏：网事杂谈(-7), 晴风村(-7955747), 二次元国家地理(-447601) */
    private val defaultFavorites = setOf("-7", "-7955747", "-447601")

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getFavorites(): Set<String> {
        val current = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        if (current.isEmpty() && !prefs.getBoolean(DEFAULTS_KEY, false)) {
            prefs.edit()
                .putStringSet(KEY, defaultFavorites)
                .putBoolean(DEFAULTS_KEY, true)
                .commit()
            return defaultFavorites
        }
        return current
    }

    fun toggle(fid: String) {
        val current = getFavorites().toMutableSet()
        if (fid in current) current.remove(fid) else current.add(fid)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun isFavorite(fid: String): Boolean = fid in getFavorites()
}
