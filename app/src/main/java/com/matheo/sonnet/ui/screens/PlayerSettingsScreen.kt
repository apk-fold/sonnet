package com.matheo.sonnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.data.SettingsRepository.Keys
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsScreen(onBack: () -> Unit) {
    val p by SettingsRepository.playerPrefs.collectAsState(initial = PlayerPrefs())
    val scope = rememberCoroutineScope()
    fun <T> save(key: Preferences.Key<T>, value: T) {
        scope.launch { SettingsRepository.set(key, value) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text("Lecteur Sonnet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        item {
            Card("Avance et recul rapides", Icons.Outlined.PlayCircle) {
                Label("Recul (bouton et double-tap à gauche)")
                Segmented(listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"), p.seekBackSec) { save(Keys.SEEK_BACK, it) }
                Spacer(Modifier.height(12.dp))
                Label("Avance (bouton et double-tap à droite)")
                Segmented(listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s"), p.seekForwardSec) { save(Keys.SEEK_FWD, it) }
                Spacer(Modifier.height(8.dp))
                SwitchLine(
                    "Double-tap gauche / droite sur la vidéo",
                    "Chaque tap en plus ajoute encore la même durée (3 taps = 30 s). Double-tap au centre = pause.",
                    p.doubleTapSeek,
                ) { save(Keys.DOUBLE_TAP, it) }
            }
        }

        item {
            Card("Gestes", Icons.Outlined.Gesture) {
                SwitchLine("Luminosité et volume", "Glisser à gauche = luminosité, à droite = volume", p.gestureBrightnessVolume) { save(Keys.G_BRIGHT_VOL, it) }
                SwitchLine("Glisser pour avancer / reculer", "Avec aperçu de l'image (trickplay)", p.gestureSwipeSeek) { save(Keys.G_SWIPE_SEEK, it) }
                SwitchLine("Pincer pour zoomer", null, p.gesturePinchZoom) { save(Keys.G_PINCH, it) }
                SwitchLine("Déplacer l'image à deux doigts", "Monte ou descend la vidéo librement", p.gestureTwoFingerMove) { save(Keys.G_TWO_FINGER, it) }
                SwitchLine("Vibrations", null, p.haptics) { save(Keys.HAPTICS, it) }
                Text(
                    "Appui long sur la vidéo : vitesse 2× tant que le doigt reste posé.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card("Image (Fold)", Icons.Outlined.Fullscreen) {
                Label("Position sur l'écran déplié")
                Segmented(listOf(-1f to "Haut", 0f to "Centre", 1f to "Bas"), snap(p.positionLargeScreen)) { save(Keys.POS_LARGE, it) }
                Spacer(Modifier.height(12.dp))
                Label("Position sur l'écran extérieur / téléphone")
                Segmented(listOf(-1f to "Haut", 0f to "Centre", 1f to "Bas"), snap(p.positionSmallScreen)) { save(Keys.POS_SMALL, it) }
                Spacer(Modifier.height(12.dp))
                Label("Remplissage par défaut")
                Segmented(
                    listOf(PlayerPrefs.ZOOM_FIT to "Ajuster", PlayerPrefs.ZOOM_FILL to "Remplir", PlayerPrefs.ZOOM_STRETCH to "Étirer"),
                    p.zoomMode,
                ) { save(Keys.ZOOM, it) }
                Spacer(Modifier.height(8.dp))
                SwitchLine("Mémoriser la dernière position", "Retenue séparément pour chaque écran", p.rememberPosition) { save(Keys.REMEMBER_POS, it) }
            }
        }

        item {
            Card("Sous-titres", Icons.Outlined.Subtitles) {
                SwitchLine("Dans la bande noire", "Affichés sous l'image au lieu de la recouvrir quand il y a la place", p.subtitlesInBars) { save(Keys.SUBS_IN_BARS, it) }
                Label("Taille")
                Segmented(listOf(0.8f to "Petits", 1f to "Normaux", 1.25f to "Grands", 1.5f to "Très grands"), p.subtitleScale) { save(Keys.SUB_SCALE, it) }
                Spacer(Modifier.height(12.dp))
                Label("Style")
                Segmented(listOf(0 to "Contour", 1 to "Fond léger", 2 to "Fond noir"), p.subtitleBackground) { save(Keys.SUB_BG, it) }
                Spacer(Modifier.height(12.dp))
                Label("Couleur")
                Segmented(listOf(0 to "Blanc", 1 to "Jaune", 2 to "Violet"), p.subtitleColor) { save(Keys.SUB_COLOR, it) }
            }
        }

        item {
            Card("Lecture", Icons.Outlined.Timer) {
                Label("Intro, générique, récap (segments Jellyfin)")
                Segmented(
                    listOf(PlayerPrefs.SKIP_OFF to "Rien", PlayerPrefs.SKIP_BUTTON to "Bouton", PlayerPrefs.SKIP_AUTO to "Auto"),
                    p.skipSegmentsMode,
                ) { save(Keys.SKIP_MODE, it) }
                Spacer(Modifier.height(8.dp))
                SwitchLine("Épisode suivant automatique", "Carte avec compte à rebours pendant le générique", p.autoplayNext) { save(Keys.AUTOPLAY_NEXT, it) }
                Label("Compte à rebours")
                Segmented(listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 20 to "20 s"), p.nextCountdownSec) { save(Keys.NEXT_COUNTDOWN, it) }
                Spacer(Modifier.height(12.dp))
                Label("Vitesse par défaut")
                Segmented(listOf(1f to "1×", 1.25f to "1,25×", 1.5f to "1,5×", 2f to "2×"), p.defaultSpeed) { save(Keys.SPEED, it) }
                Spacer(Modifier.height(12.dp))
                Label("Reculer un peu à la reprise")
                Segmented(listOf(0 to "Non", 3 to "3 s", 5 to "5 s", 10 to "10 s"), p.resumeRewindSec) { save(Keys.RESUME_REWIND, it) }
                Spacer(Modifier.height(12.dp))
                Label("Boost du volume (haut-parleurs du téléphone)")
                Segmented(listOf(0 to "Non", 3 to "+3 dB", 6 to "+6 dB", 10 to "+10 dB"), p.audioBoostDb) { save(Keys.AUDIO_BOOST, it) }
                Spacer(Modifier.height(12.dp))
                Label("Qualité maximale")
                Segmented(listOf(0 to "Max", 40 to "40 Mb", 20 to "20 Mb", 8 to "8 Mb", 4 to "4 Mb"), p.maxBitrateMbps) { save(Keys.MAX_BITRATE, it) }
                Spacer(Modifier.height(8.dp))
                SwitchLine("Toujours transcoder", "À n'activer que si un fichier refuse de se lire", p.forceTranscode) { save(Keys.FORCE_TRANSCODE, it) }
            }
        }

        item {
            Card("Fold et interface", Icons.Outlined.TabletAndroid) {
                SwitchLine("Mode tabletop / livre", "Fold à moitié plié : vidéo en haut, télécommande en bas", p.flexMode) { save(Keys.FLEX_MODE, it) }
                SwitchLine("Image dans l'image automatique", "En quittant l'app pendant la lecture", p.pipAuto) { save(Keys.PIP_AUTO, it) }
                SwitchLine("Afficher l'heure", null, p.showClock) { save(Keys.SHOW_CLOCK, it) }
                SwitchLine("Afficher le temps restant", "Sinon la durée totale", p.showRemaining) { save(Keys.SHOW_REMAINING, it) }
                Label("Masquer les contrôles après")
                Segmented(listOf(3 to "3 s", 4 to "4 s", 6 to "6 s", 10 to "10 s"), p.controlsTimeoutSec) { save(Keys.CONTROLS_TIMEOUT, it) }
            }
        }

        item {
            TextButton(onClick = { scope.launch { SettingsRepository.resetPlayerPrefs() } }) {
                Icon(Icons.Outlined.RestartAlt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Réinitialiser les réglages du lecteur", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun snap(bias: Float): Float = when {
    bias < -0.5f -> -1f
    bias > 0.5f -> 1f
    else -> 0f
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
