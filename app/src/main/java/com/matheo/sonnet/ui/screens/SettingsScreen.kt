package com.matheo.sonnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.matheo.sonnet.BuildConfig
import com.matheo.sonnet.data.PlayerPrefs
import com.matheo.sonnet.data.SessionStore
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.data.WebPrefs
import com.matheo.sonnet.jellyfin.DeviceProfile
import com.matheo.sonnet.ui.theme.Accents
import kotlinx.coroutines.launch

private data class Link(val label: String, val route: String, val icon: ImageVector, val hint: String? = null)

/** Réglages utilisateur de Jellyfin (routes vérifiées dans jellyfin-web 10.11). */
private val userLinks = listOf(
    Link("Profil et mot de passe", "#/userprofile", Icons.Outlined.Person),
    Link("Affichage", "#/mypreferencesdisplay", Icons.Outlined.ViewQuilt, "Langue, thème Jellyfin, bandes-annonces, fonds animés"),
    Link("Écran d'accueil", "#/mypreferenceshome", Icons.Outlined.Home, "Ordre des sections, médiathèques affichées"),
    Link("Lecture", "#/mypreferencesplayback", Icons.Outlined.PlayCircle, "Langue audio, qualité, épisode suivant, segments"),
    Link("Sous-titres", "#/mypreferencessubtitles", Icons.Outlined.Subtitles, "Langue, mode, style du lecteur web"),
    Link("Contrôles", "#/mypreferencescontrols", Icons.Outlined.SettingsRemote),
    Link("Quick Connect", "#/quickconnect", Icons.Outlined.QrCode, "Connecter une TV avec un code"),
    Link("Tous mes réglages", "#/mypreferencesmenu", Icons.Outlined.Settings),
)

/** Administration du serveur (tableau de bord 10.11, réservé aux comptes admin). */
private val adminLinks = listOf(
    Link("Tableau de bord", "#/dashboard", Icons.Outlined.Dashboard, "Serveur, activité, appareils connectés"),
    Link("Général", "#/dashboard/settings", Icons.Outlined.Tune, "Nom du serveur, langue, chemins"),
    Link("Slogan et personnalisation", "#/dashboard/branding", Icons.Outlined.Brush, "CSS personnalisé côté serveur, message de connexion"),
    Link("Utilisateurs", "#/dashboard/users", Icons.Outlined.People),
    Link("Médiathèques", "#/dashboard/libraries", Icons.Outlined.Storage),
    Link("Affichage des médiathèques", "#/dashboard/libraries/display", Icons.Outlined.ViewQuilt),
    Link("Métadonnées", "#/dashboard/libraries/metadata", Icons.Outlined.Description),
    Link("Fichiers NFO", "#/dashboard/libraries/nfo", Icons.Outlined.Description),
    Link("Transcodage", "#/dashboard/playback/transcoding", Icons.Outlined.Speed, "Accélération matérielle, codecs"),
    Link("Reprise de lecture", "#/dashboard/playback/resume", Icons.Outlined.History),
    Link("Streaming", "#/dashboard/playback/streaming", Icons.Outlined.Movie, "Limite de débit à distance"),
    Link("Trickplay (vignettes)", "#/dashboard/playback/trickplay", Icons.Outlined.Slideshow, "Aperçus utilisés par la barre de Sonnet"),
    Link("Plugins", "#/dashboard/plugins", Icons.Outlined.Extension, "Installés + catalogue"),
    Link("Dépôts de plugins", "#/dashboard/plugins/repositories", Icons.Outlined.Extension),
    Link("Tâches planifiées", "#/dashboard/tasks", Icons.Outlined.Schedule),
    Link("Journaux", "#/dashboard/logs", Icons.Outlined.Description),
    Link("Activité", "#/dashboard/activity", Icons.Outlined.History),
    Link("Appareils", "#/dashboard/devices", Icons.Outlined.Devices),
    Link("Réseau", "#/dashboard/networking", Icons.Outlined.Router),
    Link("TV en direct", "#/dashboard/livetv", Icons.Outlined.LiveTv, "Tuners, guide des programmes"),
    Link("Enregistrements (DVR)", "#/dashboard/livetv/recordings", Icons.Outlined.LiveTv),
    Link("Clés API", "#/dashboard/keys", Icons.Outlined.VpnKey),
    Link("Sauvegardes", "#/dashboard/backups", Icons.Outlined.Backup),
    Link("Gestionnaire de métadonnées", "#/metadata", Icons.Outlined.Description),
)

@Composable
fun SettingsScreen(onOpenRoute: (String) -> Unit, onOpenPlayerSettings: () -> Unit, onOpenFeatures: () -> Unit) {
    val serverUrl by SettingsRepository.serverUrl.collectAsState(initial = SettingsRepository.DEFAULT_SERVER)
    val bridgeUrl by SettingsRepository.bridgeUrl.collectAsState(initial = SettingsRepository.DEFAULT_BRIDGE)
    val accentIndex by SettingsRepository.accentIndex.collectAsState(initial = 0)
    val playerPrefs by SettingsRepository.playerPrefs.collectAsState(initial = PlayerPrefs())
    val webPrefs by SettingsRepository.webPrefs.collectAsState(initial = WebPrefs())
    val session by SessionStore.session.collectAsState()
    val scope = rememberCoroutineScope()

    var serverField by remember(serverUrl) { mutableStateOf(serverUrl) }
    var bridgeField by remember(bridgeUrl) { mutableStateOf(bridgeUrl) }
    var cssField by remember(webPrefs.customCss) { mutableStateOf(webPrefs.customCss) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Réglages", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (session != null) "Connecté à Jellyfin" else "Pas encore connecté — ouvre l'onglet Accueil",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ------------------------------------------------ Lecteur
        item {
            Card(title = "Lecteur Sonnet", icon = Icons.Outlined.PlayCircle) {
                SwitchLine(
                    title = "Lire avec le lecteur Sonnet",
                    subtitle = "Position haut/bas, ±10 s au double-tap, gestes, PiP, mode tabletop…",
                    checked = playerPrefs.useNativePlayer,
                ) { scope.launch { SettingsRepository.set(SettingsRepository.Keys.USE_NATIVE, it) } }
                NavLine("Tous les réglages du lecteur", Icons.Outlined.Tune, onClick = onOpenPlayerSettings)
            }
        }

        // ------------------------------------------------ Options Sonnet (animations, gestes, thèmes)
        item {
            Card(title = "Options Sonnet", icon = Icons.Outlined.AutoAwesome) {
                Text(
                    "Animations GSAP, gestes, thèmes sombre / AMOLED / clair, teinte libre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavLine("Ouvrir les options", Icons.Outlined.Tune, onClick = onOpenFeatures)
            }
        }

        // ------------------------------------------------ Serveurs
        item {
            Card(title = "Serveur Jellyfin", icon = Icons.Outlined.Dns) {
                UrlField(serverField, "URL du serveur (ex. http://192.168.1.10:8096)") { serverField = it }
                Spacer(Modifier.height(10.dp))
                SaveButton(enabled = serverField.isNotBlank() && serverField != serverUrl) {
                    scope.launch { SettingsRepository.setServerUrl(serverField) }
                }
            }
        }
        item {
            Card(title = "Bridge Hub", icon = Icons.Outlined.Hub) {
                Text(
                    "Ton espace pour accéder aux Jellyfin des autres.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                UrlField(bridgeField, "URL du Bridge Hub") { bridgeField = it }
                Spacer(Modifier.height(10.dp))
                SaveButton(enabled = bridgeField != bridgeUrl) {
                    scope.launch { SettingsRepository.setBridgeUrl(bridgeField) }
                }
            }
        }

        // ------------------------------------------------ Apparence
        item {
            Card(title = "Apparence", icon = Icons.Outlined.Palette) {
                Text("Couleur d'accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Accents.size) { index ->
                        val palette = Accents[index]
                        val selected = index == accentIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { scope.launch { SettingsRepository.setAccentIndex(index) } },
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(palette.primary, CircleShape)
                                    .border(
                                        if (selected) 2.dp else 0.dp,
                                        if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, null, tint = palette.onPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                palette.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Couleur d'accent dans Jellyfin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(0 to "Jamais", 1 to "Réglages et admin", 2 to "Partout"),
                    selected = webPrefs.themeScope,
                ) { v -> scope.launch { SettingsRepository.set(SettingsRepository.Keys.WEB_THEME_SCOPE, v) } }
                Spacer(Modifier.height(14.dp))
                Text("CSS personnalisé (injecté dans Jellyfin)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = cssField,
                    onValueChange = { cssField = it },
                    placeholder = { Text(".headerTabs { display: none; }", fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SaveButton(enabled = cssField != webPrefs.customCss) {
                    scope.launch { SettingsRepository.set(SettingsRepository.Keys.WEB_CUSTOM_CSS, cssField) }
                }
            }
        }

        // ------------------------------------------------ Jellyfin : mes réglages
        item {
            Card(title = "Mes réglages Jellyfin", icon = Icons.Outlined.Person) {
                userLinks.forEach { link -> NavLine(link.label, link.icon, link.hint) { onOpenRoute(link.route) } }
            }
        }

        // ------------------------------------------------ Jellyfin : administration
        item {
            Card(title = "Administration du serveur", icon = Icons.Outlined.AdminPanelSettings) {
                Text(
                    "Toutes les pages du tableau de bord Jellyfin, ouvertes directement dans Sonnet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                adminLinks.forEach { link -> NavLine(link.label, link.icon, link.hint) { onOpenRoute(link.route) } }
            }
        }

        // ------------------------------------------------ À propos
        item {
            Card(title = "À propos", icon = Icons.Outlined.Info) {
                InfoLine("Application", "Sonnet")
                InfoLine("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                InfoLine("Package", BuildConfig.APPLICATION_ID)
                InfoLine("Serveur", serverUrl.ifBlank { "—" })
                InfoLine("Bridge Hub", bridgeUrl.ifBlank { "—" })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Décodeurs détectés", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(DeviceProfile.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Le jeton Jellyfin reste en mémoire, Sonnet ne l'écrit jamais sur le téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- Briques partagées

@Composable
fun Card(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 840.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun NavLine(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun UrlField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
    )
}

@Composable
internal fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text("Enregistrer") }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)
