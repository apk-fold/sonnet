package com.matheo.sonnet.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.matheo.sonnet.data.NativePlayerGate
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.ui.theme.SonnetTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Activité plein écran du lecteur. Survit au pliage/dépliage du Fold (configChanges),
 * gère le PiP automatique, la MediaSession (Bluetooth, PiP, Assistant) et l'immersion.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var controller: PlayerController? = null
    private var mediaSession: MediaSession? = null
    private val inPip = mutableStateOf(false)
    private var prefs = PlayerPrefs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = PlayRequest.fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        prefs = runBlocking { SettingsRepository.playerPrefs.first() }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        val c = PlayerController(this, prefs)
        controller = c
        mediaSession = runCatching { MediaSession.Builder(this, c.player).build() }.getOrNull()
        c.start(request)

        lifecycleScope.launch {
            SettingsRepository.playerPrefs.collect { p ->
                prefs = p
                c.updatePrefs(p)
                updatePipParams()
            }
        }
        lifecycleScope.launch {
            snapshotFlow { c.videoAspect to c.isPlaying }.collect { updatePipParams() }
        }

        setContent {
            val accent by SettingsRepository.accentIndex.collectAsState(initial = 0)
            val playerPrefs by SettingsRepository.playerPrefs.collectAsState(initial = prefs)
            SonnetTheme(accentIndex = accent, immersive = true) {
                PlayerScreen(
                    controller = c,
                    prefs = playerPrefs,
                    inPip = inPip.value,
                    onExit = { finish() },
                    onEnterPip = { enterPip() },
                    onOpenInWeb = {
                        NativePlayerGate.bypassOnce()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PlayRequest.fromIntent(intent)?.let { controller?.start(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ entre tout seul en PiP grâce à setAutoEnterEnabled
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && prefs.pipAuto && controller?.player?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // Fenêtre PiP fermée par l'utilisateur : on arrête proprement
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            finishAndRemoveTask()
        }
    }

    override fun onStop() {
        super.onStop()
        controller?.player?.pause()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        controller?.release()
        controller = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun pipParams(): PictureInPictureParams {
        val aspect = (controller?.videoAspect ?: (16f / 9f)).coerceIn(0.42f, 2.38f)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 1000).roundToInt(), 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(prefs.pipAuto && controller?.isPlaying == true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }
}
