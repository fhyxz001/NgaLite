package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp

/** 板块帖子列表的展示形式 */
enum class DisplayMode { TEXT, WATERFALL }

/** 板块展示形式持久化：按 fid 记录用户选择的展示形式，未选择过时默认为列表。 */
object DisplayModeStore {

    private const val PREFS = "nga_prefs"
    private const val KEY = "forum_display_modes"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getMode(fid: String): DisplayMode {
        val prefix = "$fid="
        val entry = (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            .firstOrNull { it.startsWith(prefix) }
        return when (entry?.removePrefix(prefix)) {
            "WATERFALL" -> DisplayMode.WATERFALL
            else -> DisplayMode.TEXT
        }
    }

    fun setMode(fid: String, mode: DisplayMode) {
        val prefix = "$fid="
        val entries = (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            .toMutableSet()
        entries.removeAll { it.startsWith(prefix) }
        entries.add("$fid=${mode.name}")
        prefs.edit().putStringSet(KEY, entries).apply()
    }
}
