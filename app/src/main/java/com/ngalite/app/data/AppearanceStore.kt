package com.ngalite.app.data

import android.content.Context
import com.ngalite.app.NgaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用主题模式 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

/** 阅读字号缩放档位 */
enum class FontScale(val scale: Float, val label: String) {
    COMPACT(0.92f, "紧凑"),
    NORMAL(1.0f, "标准"),
    COMFORTABLE(1.10f, "舒适"),
    LARGE(1.22f, "大号")
}

/**
 * 外观与阅读偏好持久化（主题模式、字号缩放），
 * 通过 StateFlow 驱动全局主题与排版实时响应变化。
 */
object AppearanceStore {

    private const val PREFS = "nga_prefs"
    private const val KEY_THEME = "appearance_theme_mode"
    private const val KEY_FONT_SCALE = "appearance_font_scale"

    private val prefs by lazy {
        NgaApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _fontScale = MutableStateFlow(loadFontScale())
    val fontScale: StateFlow<FontScale> = _fontScale.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    private fun loadFontScale(): FontScale {
        val raw = prefs.getString(KEY_FONT_SCALE, FontScale.NORMAL.name)
        return FontScale.entries.firstOrNull { it.name == raw } ?: FontScale.NORMAL
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setFontScale(scale: FontScale) {
        _fontScale.value = scale
        prefs.edit().putString(KEY_FONT_SCALE, scale.name).apply()
    }
}
