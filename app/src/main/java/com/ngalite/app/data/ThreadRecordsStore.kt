package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 已收藏或最近浏览的帖子摘要记录 */
data class ThreadRecord(
    val tid: String,
    val title: String,
    val forumName: String = "",
    val author: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val page: Int = 1
)

/**
 * 帖子足迹与收藏存储：
 * - 收藏帖子（Bookmarked threads）
 * - 最近浏览帖子历史（Thread history，最多保留 80 条）
 * - 最近常逛板块 FID（Recent forums，最多保留 10 个）
 */
object ThreadRecordsStore {

    private const val PREFS = "nga_prefs"
    private const val KEY_BOOKMARKS = "bookmarked_threads_json"
    private const val KEY_HISTORY = "history_threads_json"
    private const val KEY_RECENT_FORUMS = "recent_forums_json"
    private const val MAX_HISTORY = 80
    private const val MAX_RECENT_FORUMS = 10

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _bookmarks = MutableStateFlow(loadRecords(KEY_BOOKMARKS))
    val bookmarks: StateFlow<List<ThreadRecord>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow(loadRecords(KEY_HISTORY))
    val history: StateFlow<List<ThreadRecord>> = _history.asStateFlow()

    private val _recentForumFids = MutableStateFlow(loadStringList(KEY_RECENT_FORUMS))
    val recentForumFids: StateFlow<List<String>> = _recentForumFids.asStateFlow()

    fun isBookmarked(tid: String): Boolean = _bookmarks.value.any { it.tid == tid }

    fun toggleBookmark(
        tid: String,
        title: String,
        forumName: String = "",
        author: String = "",
        page: Int = 1
    ): Boolean {
        val current = _bookmarks.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.tid == tid }
        val nowSaved: Boolean
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            nowSaved = false
        } else {
            current.add(
                0,
                ThreadRecord(
                    tid = tid,
                    title = title.ifBlank { "NGA 帖子 #$tid" },
                    forumName = forumName,
                    author = author,
                    timestamp = System.currentTimeMillis(),
                    page = page
                )
            )
            nowSaved = true
        }
        _bookmarks.value = current
        saveRecords(KEY_BOOKMARKS, current)
        return nowSaved
    }

    fun removeBookmark(tid: String) {
        val updated = _bookmarks.value.filterNot { it.tid == tid }
        _bookmarks.value = updated
        saveRecords(KEY_BOOKMARKS, updated)
    }

    fun clearBookmarks() {
        _bookmarks.value = emptyList()
        prefs.edit().remove(KEY_BOOKMARKS).apply()
    }

    fun recordVisit(
        tid: String,
        title: String,
        forumName: String = "",
        author: String = "",
        page: Int = 1
    ) {
        if (tid.isBlank() || title.isBlank()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.tid == tid }
        current.add(
            0,
            ThreadRecord(
                tid = tid,
                title = title,
                forumName = forumName,
                author = author,
                timestamp = System.currentTimeMillis(),
                page = page
            )
        )
        val trimmed = current.take(MAX_HISTORY)
        _history.value = trimmed
        saveRecords(KEY_HISTORY, trimmed)
    }

    fun removeHistory(tid: String) {
        val updated = _history.value.filterNot { it.tid == tid }
        _history.value = updated
        saveRecords(KEY_HISTORY, updated)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun recordForumVisit(fid: String) {
        if (fid.isBlank()) return
        val current = _recentForumFids.value.toMutableList()
        current.remove(fid)
        current.add(0, fid)
        val trimmed = current.take(MAX_RECENT_FORUMS)
        _recentForumFids.value = trimmed
        saveStringList(KEY_RECENT_FORUMS, trimmed)
    }

    private fun loadRecords(key: String): List<ThreadRecord> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        ThreadRecord(
                            tid = obj.optString("tid"),
                            title = obj.optString("title"),
                            forumName = obj.optString("forumName"),
                            author = obj.optString("author"),
                            timestamp = obj.optLong("timestamp", 0L),
                            page = obj.optInt("page", 1)
                        )
                    )
                }
            }.filter { it.tid.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun saveRecords(key: String, records: List<ThreadRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("tid", r.tid)
                    put("title", r.title)
                    put("forumName", r.forumName)
                    put("author", r.author)
                    put("timestamp", r.timestamp)
                    put("page", r.page)
                }
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadStringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
