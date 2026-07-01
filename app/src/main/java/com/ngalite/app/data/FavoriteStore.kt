package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/** 收藏板块持久化：记录用户收藏的板块 FID 集合。 */
object FavoriteStore {

    private const val PREFS = "nga_prefs"
    private const val KEY = "favorite_forums"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun toggle(fid: String) {
        val current = getFavorites().toMutableSet()
        if (fid in current) current.remove(fid) else current.add(fid)
        prefs.edit().putStringSet(KEY, current).apply()
    }

    fun isFavorite(fid: String): Boolean = fid in getFavorites()
}
