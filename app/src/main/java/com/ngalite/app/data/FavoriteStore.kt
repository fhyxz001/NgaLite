package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 收藏板块持久化：记录用户收藏的板块 FID 集合，并通过 StateFlow 跨页面同步。 */
object FavoriteStore {

    private const val PREFS = "nga_prefs"
    private const val KEY = "favorite_forums"
    private const val DEFAULTS_KEY = "favorites_defaults_applied"

    /** 默认收藏：网事杂谈(-7), 晴风村(-7955747), 二次元国家地理(-447601) */
    private val defaultFavorites = setOf("-7", "-7955747", "-447601")

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _favorites = MutableStateFlow(loadInitialFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private fun loadInitialFavorites(): Set<String> {
        val current = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        if (current.isEmpty() && !prefs.getBoolean(DEFAULTS_KEY, false)) {
            prefs.edit()
                .putStringSet(KEY, defaultFavorites)
                .putBoolean(DEFAULTS_KEY, true)
                .commit()
            return defaultFavorites
        }
        return current.toSet()
    }

    fun getFavorites(): Set<String> = _favorites.value

    fun toggle(fid: String) {
        val current = _favorites.value.toMutableSet()
        if (fid in current) current.remove(fid) else current.add(fid)
        val snapshot = current.toSet()
        _favorites.value = snapshot
        prefs.edit().putStringSet(KEY, snapshot).apply()
    }

    fun isFavorite(fid: String): Boolean = fid in _favorites.value
}
