package com.matheo.sonnet.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.matheo.sonnet.SonnetApp
import com.matheo.sonnet.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages du lecteur natif Sonnet. Les valeurs par défaut sont pensées pour le Fold. */
data class PlayerPrefs(
    val useNativePlayer: Boolean = true,
    // Avance / recul rapide
    val seekBackSec: Int = 10,
    val seekForwardSec: Int = 10,
    val doubleTapSeek: Boolean = true,
    // Gestes
    val gestureBrightnessVolume: Boolean = true,
    val gestureSwipeSeek: Boolean = true,
    val gesturePinchZoom: Boolean = true,
    val gestureTwoFingerMove: Boolean = true,
    val haptics: Boolean = true,
    // Position de l'image : -1 = haut, 0 = centre, 1 = bas
    val positionLargeScreen: Float = 0f,
    val positionSmallScreen: Float = 0f,
    val rememberPosition: Boolean = true,
    val zoomMode: Int = ZOOM_FIT,
    // Sous-titres
    val subtitlesInBars: Boolean = true,
    val subtitleScale: Float = 1f,
    val subtitleBackground: Int = 0,
    val subtitleColor: Int = 0,
    // Lecture
    val skipSegmentsMode: Int = SKIP_BUTTON,
    val autoplayNext: Boolean = true,
    val nextCountdownSec: Int = 10,
    val defaultSpeed: Float = 1f,
    val maxBitrateMbps: Int = 0,
    val forceTranscode: Boolean = false,
    val resumeRewindSec: Int = 0,
    val audioBoostDb: Int = 0,
    // Interface
    val pipAuto: Boolean = true,
    val flexMode: Boolean = true,
    val showClock: Boolean = true,
    val showRemaining: Boolean = true,
    val controlsTimeoutSec: Int = 4,
) {
    companion object {
        const val ZOOM_FIT = 0
        const val ZOOM_FILL = 1
        const val ZOOM_STRETCH = 2

        const val SKIP_OFF = 0
        const val SKIP_BUTTON = 1
        const val SKIP_AUTO = 2
    }
}

/** Réglages de l'interface Jellyfin web affichée dans Sonnet. */
data class WebPrefs(
    /** 0 = jamais, 1 = réglages + administration, 2 = partout */
    val themeScope: Int = 1,
    val customCss: String = "",
)

/** Options « Sonnet FX » : animations GSAP, gestes, thèmes. Tout est activable à part. */
data class FxPrefs(
    val animations: Boolean = true,      // entrées en cascade, transitions de page (GSAP)
    val crazyFx: Boolean = true,         // « effets de fou » : aurore, 3D, étincelles, titres lettre par lettre
    val pressFeedback: Boolean = true,   // effet de ressort quand on touche une carte / un bouton
    val haptics: Boolean = true,         // petites vibrations
    val pullShortcuts: Boolean = true,   // tirer vers le bas en haut de page = raccourcis
    val doubleTapFavorite: Boolean = true, // double-tap sur l'affiche = favori
    val swipeRows: Boolean = true,       // glisser un épisode : → vu / ← favori
    val bridgeFx: Boolean = true,        // mêmes effets dans le Bridge Hub
    val themeMode: Int = THEME_DARK,
    /** -1 = utiliser la pastille d'accent ; 0..359 = teinte libre */
    val accentHue: Int = -1,
) {
    companion object {
        const val THEME_DARK = 0
        const val THEME_AMOLED = 1
        const val THEME_LIGHT = 2
    }
}

object SettingsRepository {

    object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val BRIDGE_URL = stringPreferencesKey("bridge_url")
        val ACCENT_INDEX = intPreferencesKey("accent_index")

        val USE_NATIVE = booleanPreferencesKey("p_use_native")
        val SEEK_BACK = intPreferencesKey("p_seek_back")
        val SEEK_FWD = intPreferencesKey("p_seek_fwd")
        val DOUBLE_TAP = booleanPreferencesKey("p_double_tap")
        val G_BRIGHT_VOL = booleanPreferencesKey("p_g_bright_vol")
        val G_SWIPE_SEEK = booleanPreferencesKey("p_g_swipe_seek")
        val G_PINCH = booleanPreferencesKey("p_g_pinch")
        val G_TWO_FINGER = booleanPreferencesKey("p_g_two_finger")
        val HAPTICS = booleanPreferencesKey("p_haptics")
        val POS_LARGE = floatPreferencesKey("p_pos_large")
        val POS_SMALL = floatPreferencesKey("p_pos_small")
        val REMEMBER_POS = booleanPreferencesKey("p_remember_pos")
        val ZOOM = intPreferencesKey("p_zoom")
        val SUBS_IN_BARS = booleanPreferencesKey("p_subs_bars")
        val SUB_SCALE = floatPreferencesKey("p_sub_scale")
        val SUB_BG = intPreferencesKey("p_sub_bg")
        val SUB_COLOR = intPreferencesKey("p_sub_color")
        val SKIP_MODE = intPreferencesKey("p_skip_mode")
        val AUTOPLAY_NEXT = booleanPreferencesKey("p_autoplay_next")
        val NEXT_COUNTDOWN = intPreferencesKey("p_next_countdown")
        val SPEED = floatPreferencesKey("p_speed")
        val MAX_BITRATE = intPreferencesKey("p_max_bitrate")
        val FORCE_TRANSCODE = booleanPreferencesKey("p_force_transcode")
        val RESUME_REWIND = intPreferencesKey("p_resume_rewind")
        val AUDIO_BOOST = intPreferencesKey("p_audio_boost")
        val PIP_AUTO = booleanPreferencesKey("p_pip_auto")
        val FLEX_MODE = booleanPreferencesKey("p_flex_mode")
        val SHOW_CLOCK = booleanPreferencesKey("p_show_clock")
        val SHOW_REMAINING = booleanPreferencesKey("p_show_remaining")
        val CONTROLS_TIMEOUT = intPreferencesKey("p_controls_timeout")

        val FX_ANIM = booleanPreferencesKey("fx_anim")
        val FX_MAX = booleanPreferencesKey("fx_max")
        val FX_PRESS = booleanPreferencesKey("fx_press")
        val FX_HAPTICS = booleanPreferencesKey("fx_haptics")
        val FX_PULL = booleanPreferencesKey("fx_pull")
        val FX_DBLTAP = booleanPreferencesKey("fx_dbltap")
        val FX_SWIPE = booleanPreferencesKey("fx_swipe")
        val FX_BRIDGE = booleanPreferencesKey("fx_bridge")
        val FX_THEME = intPreferencesKey("fx_theme")
        val FX_HUE = intPreferencesKey("fx_hue")

        val WEB_THEME_SCOPE = intPreferencesKey("w_theme_scope")
        val WEB_CUSTOM_CSS = stringPreferencesKey("w_custom_css")
    }

    /** Aucune adresse par défaut : l'écran de configuration demande ton serveur au premier lancement. */
    const val DEFAULT_SERVER = ""
    const val DEFAULT_BRIDGE = ""

    /** « 192.168.1.5:8096 » → « http://192.168.1.5:8096 » ; une adresse vide reste vide. */
    fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty()) return ""
        val lower = t.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) t else "http://$t"
    }

    private val store get() = SonnetApp.instance.dataStore

    val serverUrl: Flow<String> = store.data.map { it[Keys.SERVER_URL] ?: DEFAULT_SERVER }
    val bridgeUrl: Flow<String> = store.data.map { it[Keys.BRIDGE_URL] ?: DEFAULT_BRIDGE }
    val accentIndex: Flow<Int> = store.data.map { it[Keys.ACCENT_INDEX] ?: 0 }
    val fxPrefs: Flow<FxPrefs> = store.data.map {
        val d = FxPrefs()
        FxPrefs(
            animations = it[Keys.FX_ANIM] ?: d.animations,
            crazyFx = it[Keys.FX_MAX] ?: d.crazyFx,
            pressFeedback = it[Keys.FX_PRESS] ?: d.pressFeedback,
            haptics = it[Keys.FX_HAPTICS] ?: d.haptics,
            pullShortcuts = it[Keys.FX_PULL] ?: d.pullShortcuts,
            doubleTapFavorite = it[Keys.FX_DBLTAP] ?: d.doubleTapFavorite,
            swipeRows = it[Keys.FX_SWIPE] ?: d.swipeRows,
            bridgeFx = it[Keys.FX_BRIDGE] ?: d.bridgeFx,
            themeMode = (it[Keys.FX_THEME] ?: d.themeMode).coerceIn(0, 2),
            accentHue = (it[Keys.FX_HUE] ?: d.accentHue).coerceIn(-1, 359),
        )
    }
    val playerPrefs: Flow<PlayerPrefs> = store.data.map { it.toPlayerPrefs() }
    val webPrefs: Flow<WebPrefs> = store.data.map {
        WebPrefs(
            themeScope = it[Keys.WEB_THEME_SCOPE] ?: 1,
            customCss = it[Keys.WEB_CUSTOM_CSS] ?: "",
        )
    }

    suspend fun setServerUrl(url: String) {
        store.edit { it[Keys.SERVER_URL] = normalizeUrl(url) }
    }

    suspend fun setBridgeUrl(url: String) {
        store.edit { it[Keys.BRIDGE_URL] = normalizeUrl(url) }
    }

    suspend fun setAccentIndex(index: Int) {
        store.edit { it[Keys.ACCENT_INDEX] = index }
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun resetFxPrefs() {
        store.edit { prefs ->
            val fxKeys = prefs.asMap().keys.filter { it.name.startsWith("fx_") }
            for (key in fxKeys) {
                @Suppress("UNCHECKED_CAST")
                prefs.remove(key as Preferences.Key<Any>)
            }
        }
    }

    suspend fun resetPlayerPrefs() {
        store.edit { prefs ->
            val playerKeys = prefs.asMap().keys.filter { it.name.startsWith("p_") }
            for (key in playerKeys) {
                @Suppress("UNCHECKED_CAST")
                prefs.remove(key as Preferences.Key<Any>)
            }
        }
    }

    private fun Preferences.toPlayerPrefs(): PlayerPrefs {
        val d = PlayerPrefs()
        return PlayerPrefs(
            useNativePlayer = this[Keys.USE_NATIVE] ?: d.useNativePlayer,
            seekBackSec = this[Keys.SEEK_BACK] ?: d.seekBackSec,
            seekForwardSec = this[Keys.SEEK_FWD] ?: d.seekForwardSec,
            doubleTapSeek = this[Keys.DOUBLE_TAP] ?: d.doubleTapSeek,
            gestureBrightnessVolume = this[Keys.G_BRIGHT_VOL] ?: d.gestureBrightnessVolume,
            gestureSwipeSeek = this[Keys.G_SWIPE_SEEK] ?: d.gestureSwipeSeek,
            gesturePinchZoom = this[Keys.G_PINCH] ?: d.gesturePinchZoom,
            gestureTwoFingerMove = this[Keys.G_TWO_FINGER] ?: d.gestureTwoFingerMove,
            haptics = this[Keys.HAPTICS] ?: d.haptics,
            positionLargeScreen = this[Keys.POS_LARGE] ?: d.positionLargeScreen,
            positionSmallScreen = this[Keys.POS_SMALL] ?: d.positionSmallScreen,
            rememberPosition = this[Keys.REMEMBER_POS] ?: d.rememberPosition,
            zoomMode = this[Keys.ZOOM] ?: d.zoomMode,
            subtitlesInBars = this[Keys.SUBS_IN_BARS] ?: d.subtitlesInBars,
            subtitleScale = this[Keys.SUB_SCALE] ?: d.subtitleScale,
            subtitleBackground = this[Keys.SUB_BG] ?: d.subtitleBackground,
            subtitleColor = this[Keys.SUB_COLOR] ?: d.subtitleColor,
            skipSegmentsMode = this[Keys.SKIP_MODE] ?: d.skipSegmentsMode,
            autoplayNext = this[Keys.AUTOPLAY_NEXT] ?: d.autoplayNext,
            nextCountdownSec = this[Keys.NEXT_COUNTDOWN] ?: d.nextCountdownSec,
            defaultSpeed = this[Keys.SPEED] ?: d.defaultSpeed,
            maxBitrateMbps = this[Keys.MAX_BITRATE] ?: d.maxBitrateMbps,
            forceTranscode = this[Keys.FORCE_TRANSCODE] ?: d.forceTranscode,
            resumeRewindSec = this[Keys.RESUME_REWIND] ?: d.resumeRewindSec,
            audioBoostDb = this[Keys.AUDIO_BOOST] ?: d.audioBoostDb,
            pipAuto = this[Keys.PIP_AUTO] ?: d.pipAuto,
            flexMode = this[Keys.FLEX_MODE] ?: d.flexMode,
            showClock = this[Keys.SHOW_CLOCK] ?: d.showClock,
            showRemaining = this[Keys.SHOW_REMAINING] ?: d.showRemaining,
            controlsTimeoutSec = this[Keys.CONTROLS_TIMEOUT] ?: d.controlsTimeoutSec,
        )
    }
}
