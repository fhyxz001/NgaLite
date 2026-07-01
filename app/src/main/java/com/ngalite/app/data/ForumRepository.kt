package com.ngalite.app.data

import android.content.Context
import org.json.JSONObject

/** 按分类分组的板块列表 */
data class ForumCategory(
    val name: String,
    val forums: List<Forum>
)

/** 从 assets/bk.json 加载板块数据的单例仓库 */
object ForumRepository {

    private var _categories: List<ForumCategory> = emptyList()
    private var _allForums: List<Forum> = emptyList()

    val categories: List<ForumCategory> get() = _categories
    val allForums: List<Forum> get() = _allForums
    val isLoaded: Boolean get() = _categories.isNotEmpty()

    /** 同步加载（已在 IO 线程调用） */
    fun load(context: Context) {
        if (isLoaded) return
        val json = context.assets.open("bk.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val cats = mutableListOf<ForumCategory>()
        val forums = mutableListOf<Forum>()

        for (key in root.keys()) {
            val arr = root.getJSONArray(key)
            val forumList = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Forum(
                    fid = o.getInt("id").toString(),
                    name = o.getString("name"),
                    description = o.optString("description", "")
                )
            }
            cats.add(ForumCategory(key, forumList))
            forums.addAll(forumList)
        }

        _categories = cats
        _allForums = forums
    }

    /** 确保已加载，未加载则同步阻塞加载（用于 ViewModel init） */
    fun ensureLoaded(context: Context) {
        if (!isLoaded) load(context)
    }
}
