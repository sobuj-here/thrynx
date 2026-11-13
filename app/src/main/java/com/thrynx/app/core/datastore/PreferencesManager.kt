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
        val KEY_DEFAULT_SPEED = floatPreferencesKey("key_default_playback_speed")
        val KEY_DOUBLE_TAP_SEEK_SEC = intPreferencesKey("key_double_tap_seek_sec")
        val KEY_RESUME_PLAYBACK = booleanPreferencesKey("key_resume_playback")
        val KEY_RESUME_MODE = stringPreferencesKey("key_resume_mode") // "ask", "resume", "beginning"
        val KEY_SHOW_PLAYER_INFO_HUD = booleanPreferencesKey("key_show_player_info_hud")
        val KEY_HIDDEN_FOLDERS = stringSetPreferencesKey("key_hidden_folders")
        val KEY_BACKGROUND_AUDIO = booleanPreferencesKey("key_background_audio")
        val KEY_DEFAULT_RESIZE_MODE = stringPreferencesKey("key_default_resize_mode")
        val KEY_DEFAULT_ORIENTATION = stringPreferencesKey("key_default_orientation")
        val KEY_GESTURES_ENABLED = booleanPreferencesKey("key_gestures_enabled")
        val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("key_auto_play_next")
        val KEY_HW_DECODER_MODE = stringPreferencesKey("key_hw_decoder_mode")
        
        // Private Folder & Security
        val KEY_PRIVATE_VAULT_PIN = stringPreferencesKey("key_private_vault_pin")
        val KEY_PRIVATE_BIOMETRIC_ENABLED = booleanPreferencesKey("key_private_biometric")
        val KEY_SHOW_HIDDEN_FOLDERS = booleanPreferencesKey("key_show_hidden_folders")

        // Subtitles
        val KEY_SUBTITLE_FONT_SIZE = floatPreferencesKey("key_sub_font_size")
        val KEY_SUBTITLE_TEXT_COLOR = stringPreferencesKey("key_sub_text_color")
        val KEY_SUBTITLE_BG_OPACITY = floatPreferencesKey("key_sub_bg_opacity")
        val KEY_SUBTITLE_DELAY_MS = longPreferencesKey("key_sub_delay_ms")

        // Sleep Timer
        val KEY_SLEEP_TIMER_MINUTES = intPreferencesKey("key_sleep_timer_minutes")

        // List & View Options
        val KEY_SHOW_PLAYED_TIME = booleanPreferencesKey("key_show_played_time")
        val KEY_VIDEO_VIEW_MODE = stringPreferencesKey("key_video_view_mode") // "files" or "folders"
        val KEY_AUDIO_VIEW_MODE = stringPreferencesKey("key_audio_view_mode") // "files" or "folders"
        val KEY_PAUSE_ON_HEADSET_DISCONNECT = booleanPreferencesKey("key_pause_on_headset_disconnect")
    }

    val languageFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LANGUAGE] ?: "en" }

    val themeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_THEME] ?: "dark" }

    val defaultSpeedFlow: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_SPEED] ?: 1.0f }

    val doubleTapSeekSecFlow: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DOUBLE_TAP_SEEK_SEC] ?: 10 }

    val resumePlaybackFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_RESUME_PLAYBACK] ?: true }

    val resumeModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_RESUME_MODE] ?: "ask" }

    val showPlayerInfoHudFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SHOW_PLAYER_INFO_HUD] ?: true }

    val hiddenFoldersFlow: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_HIDDEN_FOLDERS] ?: emptySet() }

    val backgroundAudioFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_BACKGROUND_AUDIO] ?: false }

    val backgroundPlaybackFlow: Flow<Boolean> get() = backgroundAudioFlow

    val defaultResizeModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_RESIZE_MODE] ?: "fit" }

    val defaultOrientationFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_DEFAULT_ORIENTATION] ?: "auto" }

    val gesturesEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_GESTURES_ENABLED] ?: true }

    val autoPlayNextFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTO_PLAY_NEXT] ?: true }

    val hwDecoderModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_HW_DECODER_MODE] ?: "auto" }

    val privateVaultPinFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PRIVATE_VAULT_PIN] ?: "" }

    val privateBiometricFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PRIVATE_BIOMETRIC_ENABLED] ?: false }

    val showHiddenFoldersFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SHOW_HIDDEN_FOLDERS] ?: false }

    val subtitleStyleFlow: Flow<SubtitleStyle> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            SubtitleStyle(
                fontSizeSp = it[KEY_SUBTITLE_FONT_SIZE] ?: 18f,
                textColorHex = it[KEY_SUBTITLE_TEXT_COLOR] ?: "#FFFFFF",
                backgroundOpacity = it[KEY_SUBTITLE_BG_OPACITY] ?: 0.5f,
                delayMs = it[KEY_SUBTITLE_DELAY_MS] ?: 0L
            )
        }

    val sleepTimerMinutesFlow: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SLEEP_TIMER_MINUTES] ?: 0 }

    val showPlayedTimeFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SHOW_PLAYED_TIME] ?: true }

    val videoViewModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_VIDEO_VIEW_MODE] ?: "files" }

    val audioViewModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUDIO_VIEW_MODE] ?: "files" }

    val pauseOnHeadsetDisconnectFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PAUSE_ON_HEADSET_DISCONNECT] ?: true }

    suspend fun setLanguage(langCode: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = langCode }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_DEFAULT_SPEED] = speed }
    }

    suspend fun setDoubleTapSeekSec(seconds: Int) {
        context.dataStore.edit { it[KEY_DOUBLE_TAP_SEEK_SEC] = seconds }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_PLAYBACK] = enabled }
    }

    suspend fun setResumeMode(mode: String) {
        context.dataStore.edit { 
            it[KEY_RESUME_MODE] = mode
            it[KEY_RESUME_PLAYBACK] = (mode != "beginning")
        }
    }

    suspend fun setShowPlayerInfoHud(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PLAYER_INFO_HUD] = enabled }
    }

    suspend fun setHiddenFolders(folders: Set<String>) {
        context.dataStore.edit { it[KEY_HIDDEN_FOLDERS] = folders }
    }

    suspend fun hideFolder(folderName: String) {
        context.dataStore.edit {
            val current = it[KEY_HIDDEN_FOLDERS]?.toMutableSet() ?: mutableSetOf()
            current.add(folderName)
            it[KEY_HIDDEN_FOLDERS] = current
        }
    }

    suspend fun unhideFolder(folderName: String) {
        context.dataStore.edit {
            val current = it[KEY_HIDDEN_FOLDERS]?.toMutableSet() ?: mutableSetOf()
            current.remove(folderName)
            it[KEY_HIDDEN_FOLDERS] = current
        }
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

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_PLAY_NEXT] = enabled }
    }

    suspend fun setHwDecoderMode(mode: String) {
        context.dataStore.edit { it[KEY_HW_DECODER_MODE] = mode }
    }

    suspend fun setPrivateVaultPin(pin: String) {
        context.dataStore.edit { it[KEY_PRIVATE_VAULT_PIN] = pin }
    }

    suspend fun setPrivateBiometric(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PRIVATE_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setShowHiddenFolders(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_HIDDEN_FOLDERS] = show }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_MINUTES] = minutes }
    }

    suspend fun setShowPlayedTime(show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PLAYED_TIME] = show }
    }

    suspend fun setVideoViewMode(mode: String) {
        context.dataStore.edit { it[KEY_VIDEO_VIEW_MODE] = mode }
    }

    suspend fun setAudioViewMode(mode: String) {
        context.dataStore.edit { it[KEY_AUDIO_VIEW_MODE] = mode }
    }

    suspend fun setPauseOnHeadsetDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAUSE_ON_HEADSET_DISCONNECT] = enabled }
    }

    suspend fun updateSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit {
            it[KEY_SUBTITLE_FONT_SIZE] = style.fontSizeSp
            it[KEY_SUBTITLE_TEXT_COLOR] = style.textColorHex
            it[KEY_SUBTITLE_BG_OPACITY] = style.backgroundOpacity
            it[KEY_SUBTITLE_DELAY_MS] = style.delayMs
        }
    }
}
