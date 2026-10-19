package com.matheo.sonnet.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.jellyfin.DeviceProfile
import kotlinx.coroutines.launch

/** Ce dont les panneaux ont besoin pour modifier l'image (état tenu par PlayerScreen). */
class PictureControls(
    val biasY: Float,
    val zoomMode: Int,
    val setBias: (Float) -> Unit,
    val setZoom: (Int) -> Unit,
)

private val panelBg = Color(0xF20A0612)

private class PanelHolder {
    var last: PlayerPanel? = null
}

@Composable
fun PanelHost(
    panel: PlayerPanel?,
    onClose: () -> Unit,
    content: @Composable (PlayerPanel) -> Unit,
) {
    // Garde le dernier panneau affiché pendant l'animation de fermeture (pas un état Compose)
    val holder = remember { PanelHolder() }
    if (panel != null) holder.last = panel

    Box(Modifier.fillMaxSize()) {
        if (panel != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = if (maxWidth < 520.dp) maxWidth else 400.dp
            AnimatedVisibility(
                visible = panel != null,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Column(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .background(panelBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .safeDrawingPadding()
                        .padding(16.dp)
                ) {
                    val current = holder.last
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.panelTitle().orEmpty(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        ControlIcon(Icons.Rounded.Close, "Fermer", onClick = onClose)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (current != null) content(current)
                }
            }
        }
    }
}

private fun PlayerPanel.panelTitle(): String = when (this) {
    PlayerPanel.Tracks -> "Audio et sous-titres"
    PlayerPanel.Speed -> "Vitesse de lecture"
    PlayerPanel.Sleep -> "Minuteur de veille"
    PlayerPanel.Picture -> "Image et sous-titres"
    PlayerPanel.Quality -> "Qualité"
    PlayerPanel.Episodes -> "Épisodes"
    PlayerPanel.Chapters -> "Chapitres"
    PlayerPanel.Info -> "Infos de lecture"
}

@Composable
fun PanelContent(
    panel: PlayerPanel,
    controller: PlayerController,
    prefs: PlayerPrefs,
    picture: PictureControls,
    onClose: () -> Unit,
) {
    when (panel) {
        PlayerPanel.Tracks -> TracksPanel(controller)
        PlayerPanel.Speed -> SpeedPanel(controller, onClose)
        PlayerPanel.Sleep -> SleepPanel(controller, onClose)
        PlayerPanel.Picture -> PicturePanel(prefs, picture)
        PlayerPanel.Quality -> QualityPanel(controller, onClose)
        PlayerPanel.Episodes -> EpisodesPanel(controller, onClose)
        PlayerPanel.Chapters -> ChaptersPanel(controller, onClose)
        PlayerPanel.Info -> InfoPanel(controller)
    }
}

// --------------------------------------------------------------------- Pistes

@Composable
private fun TracksPanel(controller: PlayerController) {
    val audio = controller.audioStreams
    val subs = controller.subtitleStreams
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { SectionLabel("Audio") }
        if (audio.isEmpty()) item { Hint("Aucune piste audio détectée") }
        items(audio, key = { "a${it.index}" }) { s ->
            SelectRow(
                title = s.title,
                subtitle = listOfNotNull(
                    s.language?.uppercase(),
                    s.channels?.let { "$it canaux" },
                    if (s.isDefault) "par défaut" else null,
                ).joinToString(" · ").ifBlank { null },
                selected = controller.selectedAudioIndex == s.index,
                onClick = { controller.selectAudio(s) },
            )
        }
        item { Spacer(Modifier.height(12.dp)); SectionLabel("Sous-titres") }
        item {
            SelectRow(
                title = "Désactivés",
                subtitle = null,
                selected = controller.selectedSubtitleIndex < 0,
                onClick = { controller.selectSubtitle(null) },
            )
        }
        items(subs, key = { "s${it.index}" }) { s ->
            SelectRow(
                title = s.title,
                subtitle = listOfNotNull(
                    s.codec?.uppercase(),
                    if (s.isExternal) "fichier externe" else null,
                    if (s.isForced) "forcés" else null,
                    if (!s.isTextSubtitle) "image" else null,
                ).joinToString(" · ").ifBlank { null },
                selected = controller.selectedSubtitleIndex == s.index,
                onClick = { controller.selectSubtitle(s) },
            )
        }
    }
}

// --------------------------------------------------------------------- Vitesse

private val speeds = listOf(0.5f, 0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Composable
private fun SpeedPanel(controller: PlayerController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    LazyColumn {
        items(speeds) { v ->
            SelectRow(
                title = if (v == 1f) "Normale (1×)" else "${formatSpeed(v)}×",
                subtitle = null,
                selected = controller.speed == v,
                onClick = {
                    controller.changeSpeed(v)
                    onClose()
                },
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            TextAction("Utiliser ${formatSpeed(controller.speed)}× par défaut") {
                scope.launch { SettingsRepository.set(SettingsRepository.Keys.SPEED, controller.speed) }
                controller.showToast("Vitesse par défaut : ${formatSpeed(controller.speed)}×")
            }
            Hint("Astuce : garde le doigt appuyé sur la vidéo pour passer en 2× le temps de l'appui.")
        }
    }
}

// --------------------------------------------------------------------- Minuteur

@Composable
private fun SleepPanel(controller: PlayerController, onClose: () -> Unit) {
    val options = listOf(0 to "Désactivé", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes",
        60 to "1 heure", 90 to "1 h 30", -1 to "Fin de l'épisode / du film")
    LazyColumn {
        if (controller.sleepTimerEnd > 0) {
            item { Hint("Pause dans ${formatTime(controller.sleepRemainingMs)}") }
        }
        items(options) { (minutes, label) ->
            val selected = when {
                minutes == 0 -> controller.sleepTimerEnd == 0L
                minutes < 0 -> controller.sleepTimerEnd == -1L
                else -> false
            }
            SelectRow(label, null, selected) {
                controller.setSleepTimer(minutes)
                onClose()
            }
        }
    }
}

// --------------------------------------------------------------------- Image

@Composable
private fun PicturePanel(prefs: PlayerPrefs, picture: PictureControls) {
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            SectionLabel("Position de l'image")
            Hint("Quand la vidéo ne remplit pas l'écran (Fold déplié), colle-la en haut ou en bas pour regrouper les bandes noires d'un seul côté. Tu peux aussi la glisser à deux doigts.")
            ChoiceRow(
                options = listOf(-1f to "Haut", 0f to "Centre", 1f to "Bas"),
                selected = when {
                    picture.biasY < -0.5f -> -1f
                    picture.biasY > 0.5f -> 1f
                    else -> 0f
                },
                onSelect = picture.setBias,
            )
        }
        item {
            SectionLabel("Remplissage")
            ChoiceRow(
                options = listOf(
                    PlayerPrefs.ZOOM_FIT to "Ajuster",
                    PlayerPrefs.ZOOM_FILL to "Remplir",
                    PlayerPrefs.ZOOM_STRETCH to "Étirer",
                ),
                selected = picture.zoomMode,
                onSelect = picture.setZoom,
            )
        }
        item {
            ToggleRow(
                title = "Mémoriser la position",
                subtitle = "Une position pour l'écran extérieur, une pour l'écran déplié",
                checked = prefs.rememberPosition,
            ) { scope.launch { SettingsRepository.set(SettingsRepository.Keys.REMEMBER_POS, it) } }
        }
        item {
            ToggleRow(
                title = "Sous-titres dans la bande noire",
                subtitle = "Ne recouvrent plus l'image quand il y a de la place dessous",
                checked = prefs.subtitlesInBars,
            ) { scope.launch { SettingsRepository.set(SettingsRepository.Keys.SUBS_IN_BARS, it) } }
        }
        item {
            SectionLabel("Taille des sous-titres")
            ChoiceRow(
                options = listOf(0.8f to "Petits", 1f to "Normaux", 1.25f to "Grands", 1.5f to "Très grands"),
                selected = prefs.subtitleScale,
                onSelect = { v -> scope.launch { SettingsRepository.set(SettingsRepository.Keys.SUB_SCALE, v) } },
            )
        }
        item {
            SectionLabel("Style des sous-titres")
            ChoiceRow(
                options = listOf(0 to "Contour", 1 to "Fond léger", 2 to "Fond noir"),
                selected = prefs.subtitleBackground,
                onSelect = { v -> scope.launch { SettingsRepository.set(SettingsRepository.Keys.SUB_BG, v) } },
            )
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                options = listOf(0 to "Blanc", 1 to "Jaune", 2 to "Violet"),
                selected = prefs.subtitleColor,
                onSelect = { v -> scope.launch { SettingsRepository.set(SettingsRepository.Keys.SUB_COLOR, v) } },
            )
        }
    }
}

// --------------------------------------------------------------------- Qualité

private val bitrates = listOf(0, 80, 60, 40, 25, 20, 15, 10, 8, 6, 4, 3, 2, 1)

@Composable
private fun QualityPanel(controller: PlayerController, onClose: () -> Unit) {
    val source = controller.source
    LazyColumn {
        item {
            val method = when (source?.playMethod) {
                "DirectPlay" -> "Lecture directe (fichier original)"
                "DirectStream" -> "Flux direct (remuxé)"
                "Transcode" -> "Transcodage par le serveur"
                else -> "—"
            }
            Hint("Actuellement : $method")
        }
        item {
            ToggleRow(
                title = "Forcer le transcodage",
                subtitle = "Utile en 4G ou si un fichier ne passe pas",
                checked = controller.forceTranscode,
            ) { controller.setQuality(controller.maxBitrateMbps, it) }
        }
        items(bitrates) { mbps ->
            SelectRow(
                title = if (mbps == 0) "Maximum (original si possible)" else "$mbps Mbit/s",
                subtitle = when (mbps) {
                    80, 60 -> "4K"
                    40, 25, 20 -> "1080p haute qualité"
                    15, 10, 8 -> "1080p"
                    6, 4 -> "720p"
                    3, 2 -> "480p"
                    1 -> "Économie de données"
                    else -> null
                },
                selected = controller.maxBitrateMbps == mbps,
            ) {
                controller.setQuality(mbps, controller.forceTranscode)
                onClose()
            }
        }
    }
}

// --------------------------------------------------------------------- Épisodes

@Composable
private fun EpisodesPanel(controller: PlayerController, onClose: () -> Unit) {
    val episodes = controller.seasonEpisodes
    val currentId = controller.item?.id
    val listState = rememberLazyListState()
    LaunchedEffect(episodes.size) {
        val idx = episodes.indexOfFirst { it.id == currentId }
        if (idx > 0) listState.scrollToItem(idx)
    }
    if (episodes.isEmpty()) {
        Hint("Chargement des épisodes…")
        return
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(episodes, key = { it.id }) { ep ->
            val current = ep.id == currentId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = !current) {
                        controller.playEpisode(ep)
                        onClose()
                    }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(128.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF150C26))
                ) {
                    AsyncImage(
                        model = controller.episodeImageUrl(ep),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (ep.playedPercentage > 0f && !ep.played) {
                        LinearProgressIndicator(
                            progress = { ep.playedPercentage / 100f },
                            modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.5f),
                        )
                    }
                    if (ep.played) {
                        Icon(
                            Icons.Rounded.Check, "Vu",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${ep.code}  ${ep.name}",
                        color = Color.White,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ep.runtimeMs > 0) {
                        Text(formatTime(ep.runtimeMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- Chapitres

@Composable
private fun ChaptersPanel(controller: PlayerController, onClose: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(controller.chapters, key = { it.index }) { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        controller.seekTo(c.startMs)
                        onClose()
                    }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val url = controller.chapterImageUrl(c)
                Box(
                    Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF150C26))
                ) {
                    if (url != null) {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatTime(c.startMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

// --------------------------------------------------------------------- Infos

@OptIn(UnstableApi::class)
@Composable
private fun InfoPanel(controller: PlayerController) {
    val s = controller.source
    val video = controller.videoStream
    val audio = controller.audioStreams.firstOrNull { it.index == controller.selectedAudioIndex }
    val vf = controller.player.videoFormat
    val af = controller.player.audioFormat
    val rows = buildList {
        add("Méthode" to (s?.playMethod ?: "—"))
        add("Conteneur" to (s?.container ?: "—"))
        video?.let { v ->
            add("Vidéo" to listOfNotNull(v.codec?.uppercase(), if (v.width != null && v.height != null) "${v.width}×${v.height}" else null, v.videoRange).joinToString(" · "))
        }
        audio?.let { a -> add("Audio" to a.title) }
        s?.bitrate?.let { add("Débit source" to "${it / 1_000_000} Mbit/s") }
        add("Affiché" to if (controller.videoWidth > 0) "${controller.videoWidth}×${controller.videoHeight}" else "—")
        vf?.let { f -> add("Décodeur vidéo" to listOfNotNull(f.sampleMimeType, f.codecs, if (f.frameRate > 0) "${"%.2f".format(f.frameRate)} i/s" else null).joinToString(" · ")) }
        af?.let { f -> add("Décodeur audio" to listOfNotNull(f.sampleMimeType, if (f.channelCount > 0) "${f.channelCount} canaux" else null, if (f.sampleRate > 0) "${f.sampleRate} Hz" else null).joinToString(" · ")) }
        add("Tampon" to formatTime((controller.bufferedMs - controller.positionMs).coerceAtLeast(0L)))
        add("Session" to (s?.playSessionId?.take(12) ?: "—"))
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows) { (k, v) ->
            Column {
                Text(k, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(v.ifBlank { "—" }, color = Color.White, fontSize = 14.sp)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Décodeurs du téléphone")
            Hint(DeviceProfile.describe())
        }
    }
}

// --------------------------------------------------------------------- Briques

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Hint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun SelectRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
