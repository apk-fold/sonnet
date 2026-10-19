package com.matheo.sonnet.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.jellyfin.ChapterInfo
import com.matheo.sonnet.jellyfin.MediaSegment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PlayerPanel { Tracks, Speed, Sleep, Picture, Quality, Episodes, Chapters, Info }

/** Toutes les actions déclenchables depuis les contrôles (overlay ou panneau flex). */
class PlayerActions(
    val onBack: () -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCyclePosition: () -> Unit,
    val onCycleZoom: () -> Unit,
    val onPanel: (PlayerPanel) -> Unit,
    val onPip: () -> Unit,
    val onLock: () -> Unit,
    val onRotate: () -> Unit,
    val onScrubStart: () -> Unit,
    val onScrub: (Long) -> Unit,
    val onScrubEnd: (Long) -> Unit,
)

/** Ce que les contrôles affichent (lu depuis le contrôleur + l'état d'écran). */
class ControlsState(
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val scrubMs: Long?,
    val chapters: List<ChapterInfo>,
    val segments: List<MediaSegment>,
    val biasY: Float,
    val zoomMode: Int,
    val orientationMode: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val isEpisode: Boolean,
    val speed: Float,
    val sleepActive: Boolean,
    val seekBackSec: Int,
    val seekForwardSec: Int,
    val showClock: Boolean,
    val showRemaining: Boolean,
)

fun positionIcon(bias: Float): ImageVector = when {
    bias < -0.5f -> Icons.Rounded.VerticalAlignTop
    bias > 0.5f -> Icons.Rounded.VerticalAlignBottom
    else -> Icons.Rounded.VerticalAlignCenter
}

fun zoomIcon(mode: Int): ImageVector = when (mode) {
    PlayerPrefs.ZOOM_FILL -> Icons.Rounded.Fullscreen
    PlayerPrefs.ZOOM_STRETCH -> Icons.Rounded.AspectRatio
    else -> Icons.Rounded.FullscreenExit
}

fun orientationIcon(mode: Int): ImageVector = when (mode) {
    1 -> Icons.Rounded.ScreenLockLandscape
    2 -> Icons.Rounded.ScreenLockPortrait
    else -> Icons.Rounded.ScreenRotation
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

// --------------------------------------------------------------------- Overlay

/** Contrôles par-dessus la vidéo (mode normal, plein écran). */
@Composable
fun OverlayControls(state: ControlsState, actions: PlayerActions, compact: Boolean) {
    Box(Modifier.fillMaxSize()) {
        // Dégradés haut / bas pour la lisibilité
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(state, actions, Modifier.align(Alignment.TopCenter))
            CenterButtons(state, actions, big = !compact, modifier = Modifier.align(Alignment.Center))
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TimeRow(state)
                SeekBar(
                    positionMs = state.scrubMs ?: state.positionMs,
                    durationMs = state.durationMs,
                    bufferedMs = state.bufferedMs,
                    chapters = state.chapters,
                    segments = state.segments,
                    onScrubStart = actions.onScrubStart,
                    onScrub = actions.onScrub,
                    onScrubEnd = actions.onScrubEnd,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionRow(state, actions, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TopBar(state: ControlsState, actions: PlayerActions, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = actions.onBack)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                state.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.subtitle.isNotBlank()) {
                Text(
                    state.subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.showClock) {
            Text(
                SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        ControlIcon(positionIcon(state.biasY), "Position de l'image", onClick = actions.onCyclePosition)
        ControlIcon(zoomIcon(state.zoomMode), "Zoom", onClick = actions.onCycleZoom)
        ControlIcon(Icons.Rounded.Subtitles, "Audio et sous-titres", onClick = { actions.onPanel(PlayerPanel.Tracks) })
        ControlIcon(Icons.Rounded.PictureInPictureAlt, "Image dans l'image", onClick = actions.onPip)
    }
}

@Composable
fun CenterButtons(state: ControlsState, actions: PlayerActions, big: Boolean, modifier: Modifier = Modifier) {
    val main: Dp = if (big) 76.dp else 60.dp
    val side: Dp = if (big) 56.dp else 46.dp
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (big) 28.dp else 16.dp),
    ) {
        if (state.isEpisode) {
            RoundButton(Icons.Rounded.SkipPrevious, "Épisode précédent", side * 0.8f, enabled = state.hasPrevious, onClick = actions.onPrevious)
        }
        SeekButton(back = true, seconds = state.seekBackSec, size = side, onClick = actions.onSeekBack)
        RoundButton(
            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (state.isPlaying) "Pause" else "Lecture",
            main,
            filled = true,
            onClick = actions.onTogglePlay,
        )
        SeekButton(back = false, seconds = state.seekForwardSec, size = side, onClick = actions.onSeekForward)
        if (state.isEpisode) {
            RoundButton(Icons.Rounded.SkipNext, "Épisode suivant", side * 0.8f, enabled = state.hasNext, onClick = actions.onNext)
        }
    }
}

@Composable
private fun SeekButton(back: Boolean, seconds: Int, size: Dp, onClick: () -> Unit) {
    // Icônes officielles pour 10 s, sinon pastille avec le nombre
    if (seconds == 10) {
        RoundButton(
            if (back) Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
            if (back) "Reculer de 10 secondes" else "Avancer de 10 secondes",
            size,
            onClick = onClick,
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (back) "−$seconds" else "+$seconds",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
fun RoundButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) fg else fg.copy(alpha = 0.35f),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun ControlIcon(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TimeRow(state: ControlsState) {
    val pos = state.scrubMs ?: state.positionMs
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(pos), color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        val right = if (state.showRemaining && state.durationMs > 0) "−" + formatTime(state.durationMs - pos)
        else formatTime(state.durationMs)
        Text(right, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

@Composable
fun ActionRow(state: ControlsState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ControlIcon(Icons.Rounded.Lock, "Verrouiller l'écran", onClick = actions.onLock)
        PillButton(
            text = if (state.speed == 1f) "1×" else "${formatSpeed(state.speed)}×",
            icon = Icons.Rounded.Speed,
            active = state.speed != 1f,
            onClick = { actions.onPanel(PlayerPanel.Speed) },
        )
        ControlIcon(Icons.Rounded.Timer, "Minuteur de veille", active = state.sleepActive) { actions.onPanel(PlayerPanel.Sleep) }
        if (state.isEpisode) {
            ControlIcon(Icons.Rounded.VideoLibrary, "Épisodes") { actions.onPanel(PlayerPanel.Episodes) }
        }
        if (state.chapters.isNotEmpty()) {
            ControlIcon(Icons.Rounded.Bookmark, "Chapitres") { actions.onPanel(PlayerPanel.Chapters) }
        }
        ControlIcon(Icons.Rounded.Tune, "Image et sous-titres") { actions.onPanel(PlayerPanel.Picture) }
        ControlIcon(Icons.Rounded.HighQuality, "Qualité") { actions.onPanel(PlayerPanel.Quality) }
        ControlIcon(orientationIcon(state.orientationMode), "Rotation", active = state.orientationMode != 0, onClick = actions.onRotate)
        ControlIcon(Icons.Rounded.Info, "Infos de lecture") { actions.onPanel(PlayerPanel.Info) }
    }
}

fun formatSpeed(speed: Float): String {
    val r = (speed * 100).toInt()
    return if (r % 100 == 0) (r / 100).toString()
    else if (r % 10 == 0) String.format(Locale.FRANCE, "%.1f", speed)
    else String.format(Locale.FRANCE, "%.2f", speed)
}

@Composable
fun PillButton(text: String, icon: ImageVector?, active: Boolean = false, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) primary else Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (active) primary else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- Barre de lecture

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    chapters: List<ChapterInfo>,
    segments: List<MediaSegment>,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val segmentColor = Color(0xFFEF9F27)
    val start by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onScrubEnd)
    val duration by rememberUpdatedState(durationMs)

    Box(
        modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val d = duration
                    if (d > 0) {
                        val ms = (o.x / size.width * d).toLong().coerceIn(0L, d)
                        start()
                        end(ms)
                    }
                }
            }
            .pointerInput(Unit) {
                var last = 0L
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val d = duration
                        last = (o.x / size.width * d).toLong().coerceIn(0L, d.coerceAtLeast(0L))
                        start()
                        scrub(last)
                    },
                    onDragEnd = { end(last) },
                    onDragCancel = { end(last) },
                    onHorizontalDrag = { change, _ ->
                        val d = duration
                        last = (change.position.x / size.width * d).toLong().coerceIn(0L, d.coerceAtLeast(0L))
                        scrub(last)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val w = size.width
            val trackH = 4.dp.toPx()
            val cy = size.height / 2f
            val top = cy - trackH / 2f
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            fun xOf(ms: Long): Float = if (durationMs > 0) (ms.toFloat() / durationMs * w).coerceIn(0f, w) else 0f

            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, top), Size(w, trackH), radius)
            drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, top), Size(xOf(bufferedMs), trackH), radius)
            segments.forEach { s ->
                val x1 = xOf(s.startMs)
                val x2 = xOf(s.endMs)
                if (x2 > x1) drawRect(segmentColor.copy(alpha = 0.55f), Offset(x1, top), Size(x2 - x1, trackH))
            }
            drawRoundRect(primary, Offset(0f, top), Size(xOf(positionMs), trackH), radius)
            // Repères de chapitres
            chapters.drop(1).forEach { c ->
                val x = xOf(c.startMs)
                drawRect(Color.Black.copy(alpha = 0.7f), Offset(x - 1.dp.toPx(), top), Size(2.dp.toPx(), trackH))
            }
            drawCircle(primary, radius = 7.dp.toPx(), center = Offset(xOf(positionMs), cy))
        }
    }
}

// ----------------------------------------------------------------- Mode tabletop

/**
 * Fold à moitié plié (posture "tabletop" / "livre") : la vidéo reste dans la moitié haute,
 * tous les contrôles vivent en permanence dans la moitié basse, comme un mini-téléviseur.
 */
@Composable
fun FlexPanel(state: ControlsState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xFF0A0612))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = actions.onBack)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(state.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.subtitle.isNotBlank()) {
                    Text(state.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ControlIcon(Icons.Rounded.Subtitles, "Audio et sous-titres") { actions.onPanel(PlayerPanel.Tracks) }
            ControlIcon(Icons.Rounded.PictureInPictureAlt, "Image dans l'image", onClick = actions.onPip)
        }
        Column(Modifier.fillMaxWidth()) {
            TimeRow(state)
            SeekBar(
                positionMs = state.scrubMs ?: state.positionMs,
                durationMs = state.durationMs,
                bufferedMs = state.bufferedMs,
                chapters = state.chapters,
                segments = state.segments,
                onScrubStart = actions.onScrubStart,
                onScrub = actions.onScrub,
                onScrubEnd = actions.onScrubEnd,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CenterButtons(state, actions, big = false)
        ActionRow(state, actions)
    }
}

/** Pastille arrondie utilisée pour les infos flottantes (vitesse x2, toast…). */
@Composable
fun BoxScope.FloatingPill(text: String, alignment: Alignment, modifier: Modifier = Modifier) {
    Box(
        modifier
            .align(alignment)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
