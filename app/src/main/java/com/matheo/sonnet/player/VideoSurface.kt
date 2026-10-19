package com.matheo.sonnet.player

import android.graphics.Color as AndroidColor
import android.util.TypedValue
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.matheo.sonnet.data.PlayerPrefs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rectangle réellement occupé par l'image, en px, dans la zone vidéo. */
data class VideoRect(val x: Float, val y: Float, val width: Float, val height: Float, val boxWidth: Float, val boxHeight: Float) {
    val visibleTop: Float get() = max(0f, y)
    val visibleBottom: Float get() = min(boxHeight, y + height)
    val barAbove: Float get() = visibleTop
    val barBelow: Float get() = boxHeight - visibleBottom
}

/**
 * Calcule la taille et la position de l'image.
 * biasY : -1 = collée en haut (toutes les bandes noires en bas), 0 = centrée, 1 = collée en bas.
 * Fonctionne aussi quand l'image dépasse (zoom) : le biais choisit alors la partie visible.
 */
fun computeVideoRect(
    boxW: Float,
    boxH: Float,
    aspect: Float,
    zoomMode: Int,
    pinchScale: Float,
    biasX: Float,
    biasY: Float,
): VideoRect {
    val a = if (aspect > 0f && aspect.isFinite()) aspect else 16f / 9f
    val fitW: Float
    val fitH: Float
    if (boxW / boxH > a) {
        fitH = boxH; fitW = boxH * a
    } else {
        fitW = boxW; fitH = boxW / a
    }
    val vw: Float
    val vh: Float
    when (zoomMode) {
        PlayerPrefs.ZOOM_FILL -> {
            val s = max(boxW / fitW, boxH / fitH)
            vw = fitW * s; vh = fitH * s
        }
        PlayerPrefs.ZOOM_STRETCH -> {
            vw = boxW; vh = boxH
        }
        else -> {
            vw = fitW * pinchScale; vh = fitH * pinchScale
        }
    }
    val x = (boxW - vw) * (biasX.coerceIn(-1f, 1f) + 1f) / 2f
    val y = (boxH - vh) * (biasY.coerceIn(-1f, 1f) + 1f) / 2f
    return VideoRect(x, y, vw, vh, boxW, boxH)
}

data class SubtitleStyle(
    val textSizeSp: Float,
    val foreground: Int,
    val background: Int,
    val edgeType: Int,
)

@OptIn(UnstableApi::class)
fun subtitleStyleFrom(prefs: PlayerPrefs, largeScreen: Boolean): SubtitleStyle {
    val base = if (largeScreen) 22f else 18f
    val fg = when (prefs.subtitleColor) {
        1 -> AndroidColor.rgb(255, 235, 59)
        2 -> AndroidColor.rgb(206, 203, 246)
        else -> AndroidColor.WHITE
    }
    val bg = when (prefs.subtitleBackground) {
        1 -> AndroidColor.argb(150, 0, 0, 0)
        2 -> AndroidColor.BLACK
        else -> AndroidColor.TRANSPARENT
    }
    val edge = if (prefs.subtitleBackground == 0) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE
    return SubtitleStyle(base * prefs.subtitleScale, fg, bg, edge)
}

/**
 * Surface vidéo (SurfaceView : HDR + économe) placée librement, plus les sous-titres
 * qui peuvent s'afficher dans la bande noire au lieu de recouvrir l'image.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player,
    aspect: Float,
    zoomMode: Int,
    pinchScale: Float,
    biasX: Float,
    biasY: Float,
    cues: List<Cue>,
    subtitlesInBars: Boolean,
    subtitleStyle: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        if (boxW <= 0f || boxH <= 0f) return@BoxWithConstraints
        val rect = computeVideoRect(boxW, boxH, aspect, zoomMode, pinchScale, biasX, biasY)

        // --- Image
        PlaceAt(rect.x, rect.y, rect.width, rect.height) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
                onRelease = { player.clearVideoSurfaceView(it) },
            )
        }

        // --- Sous-titres
        val minBar = with(density) { 56.dp.toPx() }
        val maxSubZone = with(density) { 190.dp.toPx() }
        val hasBitmapCue = cues.any { it.bitmap != null }
        val useBar = subtitlesInBars && !hasBitmapCue && rect.barBelow >= minBar
        val zoneTop: Float
        val zoneHeight: Float
        if (useBar) {
            zoneTop = rect.visibleBottom
            zoneHeight = min(rect.barBelow, maxSubZone)
        } else {
            zoneTop = rect.visibleTop
            zoneHeight = rect.visibleBottom - rect.visibleTop
        }
        val shownCues = if (useBar) cues.map { it.withoutPosition() } else cues

        PlaceAt(0f, zoneTop, boxW, zoneHeight.coerceAtLeast(1f)) {
            AndroidView(
                factory = { ctx -> SubtitleView(ctx) },
                update = { view ->
                    view.setStyle(
                        CaptionStyleCompat(
                            subtitleStyle.foreground,
                            subtitleStyle.background,
                            AndroidColor.TRANSPARENT,
                            subtitleStyle.edgeType,
                            AndroidColor.BLACK,
                            null,
                        )
                    )
                    view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleStyle.textSizeSp)
                    view.setApplyEmbeddedStyles(true)
                    view.setApplyEmbeddedFontSizes(false)
                    view.setBottomPaddingFraction(if (useBar) 0.18f else 0.08f)
                    view.setCues(shownCues)
                },
            )
        }
    }
}

/** Dans la bande noire, on ignore les positions imposées par le fichier (SSA \pos, VTT line…). */
@OptIn(UnstableApi::class)
private fun Cue.withoutPosition(): Cue {
    if (bitmap != null) return this
    return buildUpon()
        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
        .setLineAnchor(Cue.TYPE_UNSET)
        .setPosition(Cue.DIMEN_UNSET)
        .setPositionAnchor(Cue.TYPE_UNSET)
        .build()
}

/** Place un enfant à une position/taille précises en px (peut dépasser du parent). */
@Composable
fun PlaceAt(x: Float, y: Float, width: Float, height: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = width.roundToInt().coerceAtLeast(1)
        val h = height.roundToInt().coerceAtLeast(1)
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.place(x.roundToInt(), y.roundToInt()) }
        }
    }
}
