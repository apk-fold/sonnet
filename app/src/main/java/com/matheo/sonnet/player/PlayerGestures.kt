package com.matheo.sonnet.player

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/**
 * Tous les gestes du lecteur dans un seul détecteur (pas de conflit entre eux) :
 *  - tap / double-tap gauche-droite (-10 / +10, cumulable)        → onTap
 *  - glisser horizontal = avance / recul précis avec aperçu        → onSeekDrag*
 *  - glisser vertical gauche = luminosité, droite = volume          → onVertical*
 *  - 2 doigts : pincer = zoom, glisser = déplacer l'image haut/bas  → onTransform*
 *  - appui long = vitesse x2 tant que le doigt reste posé           → onLongPress*
 * Les callbacks sont des `var` : on les remplace à chaque recomposition sans relancer le détecteur.
 */
class PlayerGestureHandler {
    var enabled: Boolean = true
    var swipeSeekEnabled: Boolean = true
    var verticalEnabled: Boolean = true
    var transformEnabled: Boolean = true
    var longPressEnabled: Boolean = true

    var onTap: (Offset, IntSize) -> Unit = { _, _ -> }
    var onSeekDragStart: () -> Unit = {}
    var onSeekDrag: (Float) -> Unit = {}
    var onSeekDragEnd: () -> Unit = {}
    var onVerticalStart: (Boolean) -> Unit = {}
    var onVertical: (Boolean, Float) -> Unit = { _, _ -> }
    var onVerticalEnd: () -> Unit = {}
    var onTransform: (Float, Offset) -> Unit = { _, _ -> }
    var onTransformEnd: () -> Unit = {}
    var onLongPressStart: () -> Unit = {}
    var onLongPressEnd: () -> Unit = {}
}

private const val MODE_NONE = 0
private const val MODE_SEEK = 1
private const val MODE_VERTICAL = 2
private const val MODE_MULTI = 3
private const val MODE_IGNORED = -1

fun Modifier.playerGestures(handler: PlayerGestureHandler): Modifier = pointerInput(handler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!handler.enabled) {
            // Écran verrouillé : seul un tap est transmis (pour afficher le cadenas)
            var moved = false
            while (true) {
                val e = awaitPointerEvent()
                if (e.changes.any { it.positionChange().getDistance() > viewConfiguration.touchSlop }) moved = true
                if (e.changes.none { it.pressed }) break
            }
            if (!moved) handler.onTap(down.position, size)
            return@awaitEachGesture
        }

        val slop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val downTime = SystemClock.uptimeMillis()
        val leftSide = down.position.x < size.width / 2f
        var mode = MODE_NONE
        var total = Offset.Zero
        var trackedId = down.id
        var longPressed = false

        while (true) {
            val event: PointerEvent? = if (mode == MODE_NONE && !longPressed && handler.longPressEnabled) {
                val remaining = (longPressTimeout - (SystemClock.uptimeMillis() - downTime)).coerceAtLeast(1L)
                withTimeoutOrNull(remaining) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                longPressed = true
                handler.onLongPressStart()
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            // Deux doigts ou plus : zoom + déplacement de l'image
            if (pressed.size >= 2 && !longPressed && handler.transformEnabled &&
                (mode == MODE_NONE || mode == MODE_MULTI)
            ) {
                mode = MODE_MULTI
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) handler.onTransform(zoom, pan)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                continue
            }
            if (mode == MODE_MULTI || longPressed) continue

            val change = event.changes.firstOrNull { it.id == trackedId }
                ?: pressed.first().also { trackedId = it.id }
            val delta = change.positionChange()
            total += delta

            if (mode == MODE_NONE && total.getDistance() > slop) {
                mode = if (abs(total.x) > abs(total.y)) {
                    if (handler.swipeSeekEnabled) MODE_SEEK else MODE_IGNORED
                } else {
                    if (handler.verticalEnabled) MODE_VERTICAL else MODE_IGNORED
                }
                when (mode) {
                    MODE_SEEK -> handler.onSeekDragStart()
                    MODE_VERTICAL -> handler.onVerticalStart(leftSide)
                }
            }
            when (mode) {
                MODE_SEEK -> {
                    handler.onSeekDrag(total.x / size.width.coerceAtLeast(1))
                    change.consume()
                }
                MODE_VERTICAL -> {
                    handler.onVertical(leftSide, -delta.y / size.height.coerceAtLeast(1))
                    change.consume()
                }
            }
        }

        when {
            longPressed -> handler.onLongPressEnd()
            mode == MODE_NONE -> handler.onTap(down.position, size)
            mode == MODE_SEEK -> handler.onSeekDragEnd()
            mode == MODE_VERTICAL -> handler.onVerticalEnd()
            mode == MODE_MULTI -> handler.onTransformEnd()
        }
    }
}
