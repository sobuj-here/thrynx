package com.thrynx.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.thrynx.app.core.model.SubtitleStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "thrynx_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("key_app_language")
        val KEY_THEME = stringPreferencesKey("key_app_theme")
        val KEY_ACCENT = stringPreferencesKey("key_accent_color")
        val KEY_DEFAULT_SPEED = floatPreferencesKey("key_default_playback_speed")
        val KEY_RESUME_PLAYBACK = booleanPreferencesKey("key_resume_playback")
        val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("key_background_audio")
        val KEY_DEFAULT_RESIZE_MODE = stringPreferencesKey("key_default_resize_mode")
        val KEY_DEFAULT_ORIENTATION = stringPreferencesKey("key_default_orientation")
        val KEY_GESTURES_ENABLED = booleanPreferencesKey("key_gestures_enabled")
        val KEY_STARTUP_SCREEN = stringPreferencesKey("key_startup_screen")
        val KEY_EDITOR_RESOLUTION = stringPreferencesKey("key_editor_resolution")
        val KEY_EDITOR_FPS = intPreferencesKey("key_editor_fps")
        val KEY_AUTOSAVE_INTERVAL_SEC = intPreferencesKey("key_autosave_interval")
        val KEY_EXPORT_LOCATION = stringPreferencesKey("key_export_location")
        
        // Subtitle Styles
        val KEY_SUBTITLE_FONT_SIZE = floatPreferencesKey("key_sub_font_size")
        val KEY_SUBTITLE_TEXT_COLOR = stringPreferencesKey("key_sub_text_color")
        val KEY_SUBTITLE_BG_OPACITY = floatPreferencesKey("key_sub_bg_opacity")
    }

    val languageFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LANGUAGE] ?: "en" }

    val themeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME] ?: "dark" }

    val accentFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_ACCENT] ?: "cyan" }

    val defaultSpeedFlow: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_SPEED] ?: 1.0f }

    val resumePlaybackFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_RESUME_PLAYBACK] ?: true }

    val backgroundAudioFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKGROUND_AUDIO] ?: true }

    val defaultResizeModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_RESIZE_MODE] ?: "fit" }

    val defaultOrientationFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_ORIENTATION] ?: "auto" }

    val gesturesEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GESTURES_ENABLED] ?: true }

    val startupScreenFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_STARTUP_SCREEN] ?: "home" }

    val autosaveIntervalFlow: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTOSAVE_INTERVAL_SEC] ?: 30 }

    val exportLocationFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EXPORT_LOCATION] ?: "Movies/THRYNX" }

    val editorResolutionFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EDITOR_RESOLUTION] ?: "1080p" }

    val editorFpsFlow: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EDITOR_FPS] ?: 30 }

    val subtitleStyleFlow: Flow<SubtitleStyle> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            SubtitleStyle(
                fontSizeSp = it[KEY_SUBTITLE_FONT_SIZE] ?: 18f,
                textColorHex = it[KEY_SUBTITLE_TEXT_COLOR] ?: "#FFFFFF",
                backgroundOpacity = it[KEY_SUBTITLE_BG_OPACITY] ?: 0.5f
            )
        }

    suspend fun setLanguage(langCode: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = langCode }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setAccent(accent: String) {
        context.dataStore.edit { it[KEY_ACCENT] = accent }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_DEFAULT_SPEED] = speed }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_PLAYBACK] = enabled }
    }

    suspend fun setBackgroundAudio(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setDefaultResizeMode(mode: String) {
        context.dataStore.edit { it[KEY_DEFAULT_RESIZE_MODE] = mode }
    }

    suspend fun setDefaultOrientation(orientation: String) {
        context.dataStore.edit { it[KEY_DEFAULT_ORIENTATION] = orientation }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GESTURES_ENABLED] = enabled }
    }

    suspend fun setStartupScreen(screen: String) {
        context.dataStore.edit { it[KEY_STARTUP_SCREEN] = screen }
    }

    suspend fun setAutosaveInterval(sec: Int) {
        context.dataStore.edit { it[KEY_AUTOSAVE_INTERVAL_SEC] = sec }
    }

    suspend fun setExportLocation(loc: String) {
        context.dataStore.edit { it[KEY_EXPORT_LOCATION] = loc }
    }

    suspend fun setEditorResolution(res: String) {
        context.dataStore.edit { it[KEY_EDITOR_RESOLUTION] = res }
    }

    suspend fun setEditorFps(fps: Int) {
        context.dataStore.edit { it[KEY_EDITOR_FPS] = fps }
    }

    suspend fun updateSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit {
            it[KEY_SUBTITLE_FONT_SIZE] = style.fontSizeSp
            it[KEY_SUBTITLE_TEXT_COLOR] = style.textColorHex
            it[KEY_SUBTITLE_BG_OPACITY] = style.backgroundOpacity
        }
    }
}
