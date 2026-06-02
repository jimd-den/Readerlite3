package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import com.example.domain.model.ColorThemeOption
import com.example.domain.model.MixProfile
import com.example.ui.util.FontDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Split ViewModel handling configuration settings: theme, fonts, custom profiles, and API credentials.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    private val _openRouterKey = MutableStateFlow(prefs.getString("open_router_key", "") ?: "")
    val openRouterKey: StateFlow<String> = _openRouterKey.asStateFlow()

    private val _openRouterModel = MutableStateFlow(prefs.getString("open_router_model", "google/gemini-2.5-flash") ?: "google/gemini-2.5-flash")
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    private val _activeTheme = MutableStateFlow(
        try {
            ColorThemeOption.valueOf(prefs.getString("active_theme", ColorThemeOption.COSMIC_SLATE.name) ?: ColorThemeOption.COSMIC_SLATE.name)
        } catch (e: Exception) {
            ColorThemeOption.COSMIC_SLATE
        }
    )
    val activeTheme: StateFlow<ColorThemeOption> = _activeTheme.asStateFlow()

    private val _activeFontName = MutableStateFlow(prefs.getString("active_font_name", "System") ?: "System")
    val activeFontName: StateFlow<String> = _activeFontName.asStateFlow()

    private val _activeFontFamily = MutableStateFlow<FontFamily?>(null)
    val activeFontFamily: StateFlow<FontFamily?> = _activeFontFamily.asStateFlow()

    init {
        updateLoadedFontFamily(_activeFontName.value)
    }

    fun setOpenRouterKey(key: String) {
        _openRouterKey.value = key
        prefs.edit().putString("open_router_key", key).apply()
    }

    fun setOpenRouterModel(model: String) {
        _openRouterModel.value = model
        prefs.edit().putString("open_router_model", model).apply()
    }

    fun setTheme(theme: ColorThemeOption) {
        _activeTheme.value = theme
        prefs.edit().putString("active_theme", theme.name).apply()
    }

    fun setFont(fontName: String) {
        _activeFontName.value = fontName
        prefs.edit().putString("active_font_name", fontName).apply()
        updateLoadedFontFamily(fontName)
    }

    private fun updateLoadedFontFamily(name: String) {
        if (name == "System") {
            _activeFontFamily.value = null
        } else {
            val file = com.example.ui.util.FontDownloader.getFontFile(getApplication(), name)
            if (file.exists()) {
                try {
                    _activeFontFamily.value = FontFamily(androidx.compose.ui.text.font.Font(file))
                } catch (e: Exception) {
                    e.printStackTrace()
                    _activeFontFamily.value = null
                }
            } else {
                _activeFontFamily.value = null
            }
        }
    }
}
