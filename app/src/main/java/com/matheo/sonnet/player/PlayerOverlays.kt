package com.matheo.sonnet.player

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.matheo.sonnet.jellyfin.EpisodeInfo
import com.matheo.sonnet.jellyfin.MediaSegment
import kotlinx.coroutines.delay

// ------------------------------------------------------------- Double-tap ±10

/** Mémorise les taps successifs : 3 taps à droite = +30 s, comme sur YouTube. */
class SeekFeedbackState {
    var side by mutableIntStateOf(0)
    var seconds by mutableIntStateOf(0)
    var stamp by mutableLongStateOf(0L)
    private var lastAt = 0L

    fun isActive(now: Long = SystemClock.uptimeMillis()): Boolean = now - lastAt < 750L

    fun add(direction: Int, sec: Int) {
        val now = SystemClock.uptimeMillis()
        if (side == direction && isActive(now)) seconds += sec else {
            side = direction
            seconds = sec
        }
        lastAt = now
        stamp += 1
    }
}

@Composable
fun BoxScope.SeekFeedbackOverlay(state: SeekFeedbackState) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.stamp) {
        if (state.stamp > 0) {
            visible = true
            delay(750)
            visible = false
        }
    }
    val left = state.side < 0
    AnimatedVisibility(
        visible = visible && state.side != 0,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(300)),
        modifier = Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.38f),
    ) {
        val shape = if (left) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
        else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (left) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    (if (left) "−" else "+") + "${state.seconds} s",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------ Luminosité / volume

data class GestureIndicator(val isBrightness: Boolean, val value: Float)

@Composable
fun BoxScope.GestureIndicatorOverlay(indicator: GestureIndicator?) {
    AnimatedVisibility(
        visible = indicator != null,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
    ) {
        val ind = indicator ?: GestureIndicator(true, 0f)
        Row(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @Suppress("DEPRECATION")
            Icon(
                if (ind.isBrightness) Icons.Rounded.BrightnessMedium else Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(140.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ind.value.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("${(ind.value * 100).toInt()} %", color = Color.White, fontSize = 13.sp)
        }
    }
}

// ------------------------------------------------------------- Aperçu scrub

@Composable
fun BoxScope.ScrubPreview(targetMs: Long?, originMs: Long, thumbnail: Bitmap?) {
    AnimatedVisibility(
        visible = targetMs != null,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val t = targetMs ?: 0L
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(260.dp)
                        .aspectRatio(thumbnail.width.toFloat() / thumbnail.height.coerceAtLeast(1))
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }
            val delta = t - originMs
            val sign = if (delta >= 0) "+" else "−"
            Text(
                "${formatTime(t)}   ($sign${formatTime(kotlin.math.abs(delta))})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// ------------------------------------------------------------- Passer l'intro

@Composable
fun BoxScope.SkipSegmentButton(segment: MediaSegment?, controlsVisible: Boolean, onSkip: () -> Unit) {
    AnimatedVisibility(
        visible = segment != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .safeDrawingPadding()
            .padding(end = 24.dp, bottom = if (controlsVisible) 140.dp else 32.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .clickable(onClick = onSkip)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(segment?.label ?: "Passer", color = Color.Black, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.SkipNext, null, tint = Color.Black)
        }
    }
}

// ------------------------------------------------------------- Épisode suivant

@Composable
fun BoxScope.UpNextCard(
    visible: Boolean,
    episode: EpisodeInfo?,
    imageUrl: String?,
    secondsLeft: Int,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible && episode != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        val ep = episode ?: return@AnimatedVisibility
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0150C26))
                .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text("ÉPISODE SUIVANT DANS $secondsLeft s", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ep.code, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(ep.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Lire")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Annuler", color = Color.White)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Erreur

@Composable
fun ErrorOverlay(message: String, onRetry: () -> Unit, onOpenInWeb: () -> Unit, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Lecture impossible", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Réessayer")
            }
            OutlinedButton(onClick = onOpenInWeb) {
                Icon(Icons.Rounded.OpenInBrowser, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Lire avec le lecteur Jellyfin web", color = Color.White)
            }
            Text(
                "Le prochain appui sur Lecture passera par le lecteur web.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Fermer", color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------- Verrouillage

@Composable
fun BoxScope.LockedOverlay(visible: Boolean, onUnlock: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LockOpen, "Déverrouiller", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Écran verrouillé — appuie pour déverrouiller", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}
