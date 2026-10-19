package com.matheo.sonnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matheo.sonnet.data.FxPrefs
import com.matheo.sonnet.data.SettingsRepository
import kotlinx.coroutines.launch

/** Options Sonnet : animations GSAP, gestes, thèmes — activables une par une. */
@Composable
fun FeaturesScreen(onBack: () -> Unit) {
    val fx by SettingsRepository.fxPrefs.collectAsState(initial = FxPrefs())
    val scope = rememberCoroutineScope()
    val keys = SettingsRepository.Keys

    // Le curseur de teinte suit le doigt ; on n'écrit dans les réglages qu'au relâchement.
    var hueDraft by remember(fx.accentHue) { mutableStateOf(if (fx.accentHue < 0) 270f else fx.accentHue.toFloat()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Retour", tint = MaterialTheme.colorScheme.onBackground)
                }
                Column {
                    Text("Options Sonnet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "S'applique tout de suite à Jellyfin et au Bridge Hub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(title = "Animations (GSAP)", icon = Icons.Outlined.AutoAwesome) {
                SwitchLine(
                    "Animations",
                    "Les affiches arrivent en cascade, les pages glissent en douceur.",
                    fx.animations,
                ) { scope.launch { SettingsRepository.set(keys.FX_ANIM, it) } }
                SwitchLine(
                    "Effets de fou",
                    "Aurore animée, cartes en 3D, étincelles, titres lettre par lettre, parallaxe, ondes au toucher.",
                    fx.crazyFx,
                ) { scope.launch { SettingsRepository.set(keys.FX_MAX, it) } }
                SwitchLine(
                    "Effet de ressort au toucher",
                    "Les cartes et boutons s'enfoncent puis rebondissent.",
                    fx.pressFeedback,
                ) { scope.launch { SettingsRepository.set(keys.FX_PRESS, it) } }
                SwitchLine(
                    "Vibrations légères",
                    "Au toucher et quand un geste est prêt.",
                    fx.haptics,
                ) { scope.launch { SettingsRepository.set(keys.FX_HAPTICS, it) } }
                SwitchLine(
                    "Aussi dans le Bridge Hub",
                    "Applique les mêmes effets à l'onglet Bridges.",
                    fx.bridgeFx,
                ) { scope.launch { SettingsRepository.set(keys.FX_BRIDGE, it) } }
            }
        }

        item {
            Card(title = "Gestes", icon = Icons.Outlined.TouchApp) {
                SwitchLine(
                    "Tirer vers le bas = raccourcis",
                    "En haut d'une page : Accueil, Recherche, Favoris, Actualiser…",
                    fx.pullShortcuts,
                ) { scope.launch { SettingsRepository.set(keys.FX_PULL, it) } }
                SwitchLine(
                    "Double-tap sur l'affiche = favori",
                    "Sur la fiche d'un film, d'une série ou d'un album.",
                    fx.doubleTapFavorite,
                ) { scope.launch { SettingsRepository.set(keys.FX_DBLTAP, it) } }
                SwitchLine(
                    "Glisser un épisode",
                    "Vers la droite : vu / non vu. Vers la gauche : favori.",
                    fx.swipeRows,
                ) { scope.launch { SettingsRepository.set(keys.FX_SWIPE, it) } }
            }
        }

        item {
            Card(title = "Thème", icon = Icons.Outlined.Palette) {
                Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(
                        FxPrefs.THEME_DARK to "Sombre",
                        FxPrefs.THEME_AMOLED to "AMOLED",
                        FxPrefs.THEME_LIGHT to "Clair",
                    ),
                    selected = fx.themeMode,
                ) { v -> scope.launch { SettingsRepository.set(keys.FX_THEME, v) } }
                Spacer(Modifier.height(4.dp))
                Text(
                    "AMOLED = noir pur, économise la batterie. Clair = inversion douce des couleurs de Jellyfin (images conservées).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Teinte libre",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(Color.hsv(hueDraft, 0.52f, 0.92f), CircleShape)
                    )
                }
                Slider(
                    value = hueDraft,
                    onValueChange = { hueDraft = it },
                    onValueChangeFinished = {
                        scope.launch { SettingsRepository.set(keys.FX_HUE, hueDraft.toInt().coerceIn(0, 359)) }
                    },
                    valueRange = 0f..359f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (fx.accentHue >= 0) "Teinte active" else "Teinte inactive : la pastille de Réglages > Apparence est utilisée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (fx.accentHue >= 0) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { scope.launch { SettingsRepository.set(keys.FX_HUE, -1) } }) {
                            Text("Désactiver")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { scope.launch { SettingsRepository.resetFxPrefs() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Rétablir les options par défaut") }
        }
    }
}
