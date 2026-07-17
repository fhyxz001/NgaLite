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
        val favorit