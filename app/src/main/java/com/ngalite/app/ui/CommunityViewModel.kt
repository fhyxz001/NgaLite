package com.ngalite.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngalite.app.data.FavoriteStore
import com.ngalite.app.data.Forum
import com.ngalite.app.data.ForumCategory
import com.ngalite.app.data.ForumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CommunityUiState {
    data object Loading : CommunityUiState
    data class Success(
        val categories: List<ForumCategory>,
        val selectedCategory: ForumCategory,
        val favoriteFids: Set<String>,
        val query: String = "",
        val searchResults: List<Forum> = emptyList()
    ) : CommunityUiState

    data class Error(val message: String) : CommunityUiState
}

class CommunityViewModel : ViewModel() {

    private val _state = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val state: StateFlow<CommunityUiState> = _state

    /** 防抖时间戳，避免快速连续点击收藏。 */
    private var lastToggleTime = 0L
    private val toggleDebounceMs = 400L

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ForumRepository.load(context)
                }
                val categories = ForumRepository.categories
                if (categories.isEmpty()) {
                    _state.value = CommunityUiState.Error("板块数据加载失败")
                    return@launch
                }
                val favoriteFids = FavoriteStore.getFavorites()
                val displayCategories = buildDisplayCategories(categories, favoriteFids)
                val selected = displayCategories.firstOrNull { it.name == "我的收藏" }
                    ?: displayCategories.first()
                _state.value = CommunityUiState.Success(
                    categories = displayCategories,
                    selectedCategory = selected,
                    favoriteFids = favoriteFids
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = CommunityUiState.Error(t.message ?: "加载失败")
            }
        }
    }

    fun selectCategory(category: ForumCategory) {
        val s = _state.value as? CommunityUiState.Success ?: return
        _state.value = s.copy(selectedCategory = category)
    }

    fun updateQuery(query: String) {
        val s = _state.value as? CommunityUiState.Success ?: return
        val searchResults = if (query.isBlank()) {
            emptyList()
        } else {
            ForumRepository.allForums.filter { it.name.contains(query, ignoreCase = true) }
        }
        _state.value = s.copy(query = query, searchResults = searchResults)
    }

    fun toggleFavorite(fid: String) {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < toggleDebounceMs) return
        lastToggleTime = now
        FavoriteStore.toggle(fid)
        val s = _state.value as? CommunityUiState.Success ?: return
        val newFavorites = FavoriteStore.getFavorites()
        val newCategories = buildDisplayCategories(ForumRepository.categories, newFavorites)
        // 保持当前选中的分类在刷新后仍然有效；若当前选中的是「我的收藏」则继续选中它
        val newSelected = if (s.selectedCategory.name == "我的收藏") {
            newCategories.firstOrNull { it.name == "我的收藏" } ?: newCategories.first()
        } else {
            newCategories.firstOrNull { it.name == s.selectedCategory.name } ?: newCategories.first()
        }
        _state.value = s.copy(
            categories = newCategories,
            selectedCategory = newSelected,
            favoriteFids = newFavorites
        )
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
