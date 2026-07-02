package com.ngalite.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 按分类分组的板块列表 */
data class ForumCategory(
    val name: String,
    val forums: List<Forum>
)

/**
 * 从 assets/bk.json 加载板块数据的单例仓库。
 *
 * 线程安全：[load] 内部使用 [synchronized] 保护，多线程并发调用只会执行一次实际解析，
 * 其余调用阻塞等待至加载完成后立即返回。读取 [_categories]/[_allForums] 时，
 * 由于 Kotlin 中 List 引用的赋值是原子的，读取方要么看到旧值要么看到新值，
 * 不会看到半完成状态。
 */
object ForumRepository {

    private val lock = Any()

    @Volatile
    private var _categories: List<ForumCategory> = emptyList()

    @Volatile
    private var _allForums: List<Forum> = emptyList()

    val categories: List<ForumCategory> get() = _categories
    val allForums: List<Forum> get() = _allForums
    val isLoaded: Boolean get() = _categories.isNotEmpty()

    /** 同步加载（已在 IO 线程调用）。多线程并发时由 [lock] 保证只解析一次。 */
    fun load(context: Context) {
        if (isLoaded) return
        synchronized(lock) {
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
                cats.add(ForumCategory(categoryDisplayNames[key] ?: key, forumList))
                forums.addAll(forumList)
            }

            // 按指定顺序排列分类：资讯综合、硬件科技、生活娱乐置顶
            val priorityOrder = listOf("资讯综合", "硬件科技", "生活娱乐")
            _categories = cats.sortedBy { cat ->
                val idx = priorityOrder.indexOf(cat.name)
                if (idx >= 0) idx else priorityOrder.size
            }
            _allForums = forums
        }
    }

    /** 板块分组显示名称映射（精简到 4 字以内） */
    private val categoryDisplayNames = mapOf(
        "魔兽世界专区" to "魔兽世界",
        "暴雪游戏专区" to "暴雪游戏",
        "二次元手游" to "二次元",
        "PC与主机单机" to "PC单机",
        "网络游戏与电竞" to "网游电竞",
        "射击与竞技游戏" to "射击竞技",
        "策略与战棋类" to "策略战棋",
        "卡牌与桌游跑团" to "卡牌桌游",
        "MMO与端游专区" to "MMO端游",
        "Galgame与虚拟偶像" to "Gal虚拟",
        "生活娱乐与休闲" to "生活娱乐",
        "硬件科技与消费" to "硬件科技",
        "资讯与社区综合" to "资讯综合",
        "文学与二次元创作" to "文学创作"
    )

    /**
     * 确保已加载，未加载则在 IO 线程同步阻塞加载。
     * 用于 ViewModel init 等需要在 Main 调度器调用但又不能阻塞主线程的场景。
     */
    suspend fun ensureLoadedAsync(context: Context) = withContext(Dispatchers.IO) {
        load(context)
    }
}
