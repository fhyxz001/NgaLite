package com.ngalite.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngalite.app.data.FavoriteStore
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumCategory
import com.ngalite.app.data.ForumRepository
import com.ngalite.app.data.ThreadRecordsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CommunityUiState {
    data object Loading : CommunityUiState
    data class Success(
        val categories: List<ForumCategory>,
        val selectedCategoryName: String,
        val favoriteFids: Set<String>,
        val recentForums: List<Forum>,
        val query: String = "",
        val searchResults: List<Forum> = emptyList()
    ) : CommunityUiState {
        val selectedCategory: ForumCategory
            get() = categories.firstOrNull { it.name == selectedCategoryName } ?: categories.first()
    }

    data class Error(val message: String) : CommunityUiState
}

class CommunityViewModel : ViewModel() {

    private val _state = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val state: StateFlow<CommunityUiState> = _state

    private val selectedCategoryName = MutableStateFlow("我的收藏")
    private val searchQuery = MutableStateFlow("")
    private var isInitialized = false

    /** 防抖时间戳，避免快速连续点击收藏。 */
    private var lastToggleTime = 0L
    private val toggleDebounceMs = 250L

    fun load(context: Context) {
        if (isInitialized && _state.value is CommunityUiState.Success) return
        viewModelScope.launch {
            _state.value = CommunityUiState.Loading
            try {
                withContext(Dispatchers.IO) {
                    ForumRepository.load(context)
                }
                val categories = ForumRepository.categories
                if (categories.isEmpty()) {
                    _state.value = CommunityUiState.Error("板块数据加载失败")
                    return@launch
                }
                isInitialized = true
                // 持续同步收藏与最近浏览板块变化
                combine(
                    FavoriteStore.favorites,
                    ThreadRecordsStore.recentForumFids,
                    selectedCategoryName,
                    searchQuery
                ) { favFids, recentFids, selectedName, query ->
                    val displayCategories = buildDisplayCategories(categories, favFids)
                    val validSelectedName = if (displayCategories.any { it.name == selectedName }) {
                        selectedName
                    } else {
                        displayCategories.first().name
                    }
                    val forumMap = ForumRepository.allForums.associateBy { it.fid }
                    val recent = recentFids.mapNotNull { forumMap[it] }
                    val results = if (query.isBlank()) {
                        emptyList()
                    } else {
                        val q = query.trim()
                        ForumRepository.allForums.filter {
                            it.name.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true) ||
                                it.fid == q
                        }
                    }
                    CommunityUiState.Success(
                        categories = displayCategories,
                        selectedCategoryName = validSelectedName,
                        favoriteFids = favFids,
                        recentForums = recent,
                        query = query,
                        searchResults = results
                    )
                }.collect { newState ->
                    _state.value = newState
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = CommunityUiState.Error(t.message ?: "加载失败")
            }
        }
    }

    fun selectCategory(category: ForumCategory) {
        selectedCategoryName.value = category.name
    }

    fun updateQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleFavorite(fid: String) {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < toggleDebounceMs) return
        lastToggleTime = now
        FavoriteStore.toggle(fid)
    }

    /** 构建展示用分类列表：第一项为「我的收藏」，其后为原始分类。 */
    private fun buildDisplayCategories(
        categories: List<ForumCategory>,
        favoriteFids: Set<String>
    ): List<ForumCategory> {
        val favoriteForums = if (favoriteFids.isEmpty()) {
            emptyList()
        } else {
            ForumRepository.allForums.filter { it.fid in favoriteFids }
        }
        return listOf(ForumCategory("我的收藏", favoriteForums)) + categories
    }
}
