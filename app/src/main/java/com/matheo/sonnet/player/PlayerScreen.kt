package com.matheo.sonnet.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DOUBLE_TAP_MS = 280L

/** Posture du Fold quand il est à moitié plié. */
data class FoldPosture(val horizontal: Boolean, val bounds: Rect)

@Composable
fun rememberFoldPosture(activity: Activity): FoldPosture? {
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsState(initial = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    if (fold.state != FoldingFeature.State.HALF_OPENED) return null
    return FoldPosture(fold.orientation == FoldingFeature.Orientation.HORIZONTAL, fold.bounds)
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Pour les taps : distingue simple tap (afficher les contrôles) et double tap (±10 s). */
private class TapTracker {
    var lastTapTime = 0L
    var lastZone = 99
    var pending: Job? = null
}

@Composable
fun PlayerScreen(
    controller: PlayerController,
    prefs: PlayerPrefs,
    inPip: Boolean,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenInWeb: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val largeScreen = config.smallestScreenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // ---------------------------------------------------- Position / zoom
    var biasY by remember { mutableFloatStateOf(if (largeScreen) prefs.positionLargeScreen else prefs.positionSmallScreen) }
    var biasX by remember { mutableFloatStateOf(0f) }
    var zoomMode by remember { mutableIntStateOf(prefs.zoomMode) }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var transforming by remember { mutableStateOf(false) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    // Plier / déplier : on reprend la position mémorisée pour cet écran
    LaunchedEffect(largeScreen) {
        if (prefs.rememberPosition) {
            biasY = if (largeScreen) prefs.positionLargeScreen else prefs.positionSmallScreen
            biasX = 0f
        }
    }

    val springSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    val animBiasY by animateFloatAsState(biasY, if (transforming) snap() else springSpec, label = "biasY")
    val animBiasX by animateFloatAsState(biasX, if (transforming) snap() else springSpec, label = "biasX")
    val animPinch by animateFloatAsState(pinchScale, if (transforming) snap() else springSpec, label = "pinch")

    fun persistPosition(value: Float) {
        if (!prefs.rememberPosition) return
        val key = if (largeScreen) SettingsRepository.Keys.POS_LARGE else SettingsRepository.Keys.POS_SMALL
        scope.launch { SettingsRepository.set(key, value) }
    }

    fun setPosition(value: Float) {
        biasY = value
        persistPosition(value)
    }

    fun setZoom(mode: Int) {
        zoomMode = mode
        pinchScale = 1f
        biasX = 0f
        scope.launch { SettingsRepository.set(SettingsRepository.Keys.ZOOM, mode) }
        controller.showToast(
            when (mode) {
                PlayerPrefs.ZOOM_FILL -> "Remplir l'écran"
                PlayerPrefs.ZOOM_STRETCH -> "Étirer"
                else -> "Ajuster à l'écran"
            }
        )
    }

    // ---------------------------------------------------- UI
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionStamp by remember { mutableLongStateOf(0L) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var orientationMode by remember { mutableIntStateOf(0) }
    val seekFeedback = remember { SeekFeedbackState() }
    var indicator by remember { mutableStateOf<GestureIndicator?>(null) }
    var indicatorJob by remember { mutableStateOf<Job?>(null) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var scrubOrigin by remember { mutableLongStateOf(0L) }
    var scrubThumb by remember { mutableStateOf<Bitmap?>(null) }
    var speedBoost by remember { mutableStateOf(false) }
    val tapTracker = remember { TapTracker() }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }

    val posture = activity?.let { rememberFoldPosture(it) }
    val flex = prefs.flexMode && posture != null && !inPip

    fun touch() {
        interactionStamp = SystemClock.uptimeMillis()
    }

    fun showControls() {
        controlsVisible = true
        touch()
    }

    fun seekStep(direction: Int) {
        val sec = if (direction < 0) prefs.seekBackSec else prefs.seekForwardSec
        controller.seekBy(direction * sec * 1000L)
        seekFeedback.add(direction, sec)
        if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun showIndicator(value: GestureIndicator) {
        indicator = value
        indicatorJob?.cancel()
        indicatorJob = scope.launch {
            delay(900)
            indicator = null
        }
    }

    // Masquage automatique des contrôles
    LaunchedEffect(controlsVisible, interactionStamp, controller.isPlaying, panel, scrubMs, flex) {
        if (controlsVisible && controller.isPlaying && panel == null && scrubMs == null && !flex) {
            delay(prefs.controlsTimeoutSec * 1000L)
            controlsVisible = false
        }
    }

    // Aperçu trickplay pendant qu'on cherche
    LaunchedEffect(scrubMs?.div(2000L)) {
        val target = scrubMs
        scrubThumb = if (target == null) null else controller.thumbnailAt(target)
    }

    // Orientation
    LaunchedEffect(orientationMode) {
        activity?.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            locked -> {
                showControls()
                controller.showToast("Écran verrouillé")
            }
            else -> onExit()
        }
        Unit
    }

    // ---------------------------------------------------- Gestes
    val gestures = remember { PlayerGestureHandler() }
    gestures.enabled = !locked
    gestures.swipeSeekEnabled = prefs.gestureSwipeSeek
    gestures.verticalEnabled = prefs.gestureBrightnessVolume
    gestures.transformEnabled = prefs.gesturePinchZoom || prefs.gestureTwoFingerMove
    gestures.longPressEnabled = true

    gestures.onTap = { pos, size ->
        when {
            locked -> {
                controlsVisible = !controlsVisible
                touch()
            }
            !prefs.doubleTapSeek -> {
                controlsVisible = !controlsVisible
                touch()
            }
            else -> {
                val w = size.width.toFloat()
                val zone = when {
                    pos.x < w * 0.38f -> -1
                    pos.x > w * 0.62f -> 1
                    else -> 0
                }
                val now = SystemClock.uptimeMillis()
                if (zone != 0 && seekFeedback.side == zone && seekFeedback.isActive(now)) {
                    // Rafale : chaque tap supplémentaire ajoute encore ±10 s
                    tapTracker.pending?.cancel()
                    tapTracker.lastTapTime = 0L
                    seekStep(zone)
                } else if (now - tapTracker.lastTapTime < DOUBLE_TAP_MS && zone == tapTracker.lastZone) {
                    tapTracker.pending?.cancel()
                    tapTracker.lastTapTime = 0L
                    if (zone == 0) controller.togglePlay() else seekStep(zone)
                } else {
                    tapTracker.lastTapTime = now
                    tapTracker.lastZone = zone
                    tapTracker.pending?.cancel()
                    tapTracker.pending = scope.launch {
                        delay(DOUBLE_TAP_MS)
                        controlsVisible = !controlsVisible
                        touch()
                    }
                }
            }
        }
        Unit
    }
    gestures.onSeekDragStart = {
        scrubOrigin = controller.positionMs
        scrubMs = scrubOrigin
        showControls()
    }
    gestures.onSeekDrag = { fraction ->
        // Toute la largeur de l'écran = 100 s : précis même sur le grand écran du Fold
        val max = if (controller.durationMs > 0) controller.durationMs else Long.MAX_VALUE / 4
        scrubMs = (scrubOrigin + (fraction * 100_000f).toLong()).coerceIn(0L, max)
    }
    gestures.onSeekDragEnd = {
        scrubMs?.let { controller.seekTo(it) }
        scrubMs = null
        touch()
    }
    gestures.onVerticalStart = { left ->
        if (left) {
            brightness = currentBrightness(activity)
        } else {
            volume = currentVolume(audioManager)
        }
        Unit
    }
    gestures.onVertical = { left, dy ->
        if (left) {
            brightness = (brightness + dy * 1.3f).coerceIn(0.01f, 1f)
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
            showIndicator(GestureIndicator(true, brightness))
        } else {
            volume = (volume + dy * 1.3f).coerceIn(0f, 1f)
            setVolume(audioManager, volume)
            showIndicator(GestureIndicator(false, volume))
        }
    }
    gestures.onVerticalEnd = { touch() }
    gestures.onTransform = { zoom, pan ->
        transforming = true
        if (prefs.gesturePinchZoom && zoom != 1f) {
            if (zoomMode != PlayerPrefs.ZOOM_FIT) {
                zoomMode = PlayerPrefs.ZOOM_FIT
            }
            pinchScale = (pinchScale * zoom).coerceIn(1f, 4f)
        }
        if (prefs.gestureTwoFingerMove && areaSize.width > 0 && areaSize.height > 0) {
            val rect = computeVideoRect(
                areaSize.width.toFloat(), areaSize.height.toFloat(),
                controller.videoAspect, zoomMode, pinchScale, biasX, biasY,
            )
            val freeY = rect.boxHeight - rect.height
            val freeX = rect.boxWidth - rect.width
            if (abs(freeY) > 1f) biasY = (biasY + 2f * pan.y / freeY).coerceIn(-1f, 1f)
            if (abs(freeX) > 1f && pinchScale > 1.01f) biasX = (biasX + 2f * pan.x / freeX).coerceIn(-1f, 1f)
        }
        Unit
    }
    gestures.onTransformEnd = {
        transforming = false
        // Aimant vers haut / centre / bas
        listOf(-1f, 0f, 1f).firstOrNull { abs(it - biasY) < 0.12f }?.let { biasY = it }
        if (pinchScale < 1.04f) {
            pinchScale = 1f
            biasX = 0f
        }
        persistPosition(biasY)
    }
    gestures.onLongPressStart = {
        if (controller.isPlaying) {
            speedBoost = true
            controller.player.setPlaybackSpeed(2f)
            if (prefs.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    gestures.onLongPressEnd = {
        if (speedBoost) {
            speedBoost = false
            controller.player.setPlaybackSpeed(controller.speed)
        }
    }

    // ---------------------------------------------------- Actions
    val actions = PlayerActions(
        onBack = onExit,
        onSeekBack = { seekStep(-1); touch() },
        onSeekForward = { seekStep(1); touch() },
        onTogglePlay = { controller.togglePlay(); touch() },
        onPrevious = { controller.playPrevious() },
        onNext = { controller.playNext() },
        onCyclePosition = {
            val next = when {
                biasY < -0.5f -> 0f
                biasY > 0.5f -> -1f
                else -> 1f
            }
            setPosition(next)
            controller.showToast(
                when (next) {
                    -1f -> "Image en haut — bandes noires en bas"
                    1f -> "Image en bas — bandes noires en haut"
                    else -> "Image centrée"
                }
            )
            touch()
        },
        onCycleZoom = {
            setZoom((zoomMode + 1) % 3)
            touch()
        },
        onPanel = { panel = it },
        onPip = onEnterPip,
        onLock = {
            locked = true
            controlsVisible = false
            controller.showToast("Écran verrouillé")
        },
        onRotate = {
            orientationMode = (orientationMode + 1) % 3
            controller.showToast(
                when (orientationMode) {
                    1 -> "Paysage verrouillé"
                    2 -> "Portrait verrouillé"
                    else -> "Rotation automatique"
                }
            )
            touch()
        },
        onScrubStart = {
            scrubOrigin = controller.positionMs
            touch()
        },
        onScrub = { scrubMs = it },
        onScrubEnd = { ms ->
            controller.seekTo(ms)
            scrubMs = null
            touch()
        },
    )

    val item = controller.item
    val controlsState = ControlsState(
        title = item?.displayTitle ?: "Chargement…",
        subtitle = item?.displaySubtitle.orEmpty(),
        isPlaying = controller.isPlaying,
        positionMs = controller.positionMs,
        durationMs = controller.durationMs,
        bufferedMs = controller.bufferedMs,
        scrubMs = scrubMs,
        chapters = controller.chapters,
        segments = controller.segments,
        biasY = biasY,
        zoomMode = zoomMode,
        orientationMode = orientationMode,
        hasPrevious = controller.previousEpisode != null,
        hasNext = controller.nextEpisode != null,
        isEpisode = item?.isEpisode == true,
        speed = controller.speed,
        sleepActive = controller.sleepTimerEnd != 0L,
        seekBackSec = prefs.seekBackSec,
        seekForwardSec = prefs.seekForwardSec,
        showClock = prefs.showClock,
        showRemaining = prefs.showRemaining,
    )

    val subtitleStyle = subtitleStyleFrom(prefs, largeScreen)

    // ---------------------------------------------------- Zone vidéo
    val videoArea: @Composable (Boolean) -> Unit = { withOverlayControls ->
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { areaSize = it }
        ) {
            VideoSurface(
                player = controller.player,
                aspect = controller.videoAspect,
                zoomMode = zoomMode,
                pinchScale = animPinch,
                biasX = animBiasX,
                biasY = animBiasY,
                cues = controller.cues,
                subtitlesInBars = prefs.subtitlesInBars && !inPip,
                subtitleStyle = subtitleStyle,
            )
            if (!inPip) {
                // Couche de gestes au-dessus de la vidéo et des sous-titres
                Box(Modifier.fillMaxSize().playerGestures(gestures))

                SeekFeedbackOverlay(seekFeedback)
                GestureIndicatorOverlay(indicator)
                ScrubPreview(scrubMs, scrubOrigin, scrubThumb)

                if ((controller.loading || controller.buffering) && controller.error == null) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center).size(56.dp),
                    )
                }
                if (speedBoost) FloatingPill("2×  ▶▶", Alignment.TopCenter, Modifier.padding(top = 24.dp))

                if (withOverlayControls) {
                    AnimatedVisibility(
                        visible = controlsVisible && !locked,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        OverlayControls(controlsState, actions, compact = !largeScreen && config.screenHeightDp < 500)
                    }
                }
                LockedOverlay(visible = locked && controlsVisible) {
                    locked = false
                    showControls()
                }
                SkipSegmentButton(
                    segment = controller.activeSegment,
                    controlsVisible = controlsVisible && withOverlayControls,
                    onSkip = { controller.skipActiveSegment() },
                )
                UpNextCard(
                    visible = controller.upNextVisible,
                    episode = controller.nextEpisode,
                    imageUrl = controller.nextEpisode?.let { controller.episodeImageUrl(it) },
                    secondsLeft = controller.upNextSecondsLeft,
                    onPlay = { controller.playNext() },
                    onCancel = { controller.dismissUpNext() },
                )
            }
        }
    }

    // ---------------------------------------------------- Mise en page
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val p = posture
        if (flex && p != null) {
            val hinge = with(density) {
                (if (p.horizontal) p.bounds.height() else p.bounds.width()).toDp()
            }
            if (p.horizontal) {
                // Tabletop : vidéo en haut, télécommande en bas
                val top = with(density) { p.bounds.top.toDp() }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(top)) { videoArea(false) }
                    Spacer(Modifier.height(hinge))
                    FlexPanel(controlsState, actions, Modifier.fillMaxWidth().weight(1f))
                }
            } else {
                // Livre : vidéo à gauche, télécommande à droite
                val left = with(density) { p.bounds.left.toDp() }
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(left).fillMaxHeight()) { videoArea(false) }
                    Spacer(Modifier.width(hinge))
                    FlexPanel(controlsState, actions, Modifier.fillMaxHeight().weight(1f))
                }
            }
        } else {
            videoArea(true)
        }

        if (!inPip) {
            controller.toast?.let { msg ->
                FloatingPill(msg, Alignment.TopCenter, Modifier.safeDrawingPadding().padding(top = 64.dp))
            }

            PanelHost(panel = panel, onClose = { panel = null; touch() }) { current ->
                PanelContent(
                    panel = current,
                    controller = controller,
                    prefs = prefs,
                    picture = PictureControls(
                        biasY = biasY,
                        zoomMode = zoomMode,
                        setBias = { setPosition(it) },
                        setZoom = { setZoom(it) },
                    ),
                    onClose = { panel = null; touch() },
                )
            }

            controller.error?.let { message ->
                ErrorOverlay(
                    message = message,
                    onRetry = { controller.retry() },
                    onOpenInWeb = onOpenInWeb,
                    onClose = onExit,
                )
            }
        }
    }
}

private fun currentBrightness(activity: Activity?): Float {
    val window = activity?.window ?: return 0.5f
    val current = window.attributes.screenBrightness
    if (current in 0f..1f) return current
    return runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0.01f, 1f)
}

private fun currentVolume(audio: AudioManager?): Float {
    audio ?: return 0.5f
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun setVolume(audio: AudioManager?, value: Float) {
    audio ?: return
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0) }
}
