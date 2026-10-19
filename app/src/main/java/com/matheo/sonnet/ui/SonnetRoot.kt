package com.matheo.sonnet.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.player.findActivity
import com.matheo.sonnet.ui.screens.FeaturesScreen
import com.matheo.sonnet.ui.screens.LibraryScreen
import com.matheo.sonnet.ui.screens.PlayerSettingsScreen
import com.matheo.sonnet.ui.screens.SettingsScreen
import com.matheo.sonnet.ui.screens.SetupScreen
import com.matheo.sonnet.web.WebHost

enum class Dest(val label: String, val icon: ImageVector?) {
    Home("Accueil", Icons.Outlined.Home),
    Libraries("Bibliothèques", Icons.Outlined.VideoLibrary),
    Photos("Photos", Icons.Outlined.PhotoLibrary),
    Bridge("Bridges", Icons.Outlined.Hub),
    Settings("Réglages", Icons.Outlined.Settings),
    PlayerSettings("Lecteur", null),
    Features("Options Sonnet", null),
}

private val tabs = listOf(Dest.Home, Dest.Libraries, Dest.Photos, Dest.Bridge, Dest.Settings)

@Composable
fun SonnetRoot(web: WebHost) {
    var dest by rememberSaveable { mutableStateOf(Dest.Home) }
    val serverUrl by SettingsRepository.serverUrl.collectAsState(initial = null)
    val bridgeUrl by SettingsRepository.bridgeUrl.collectAsState(initial = null)
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(serverUrl) { serverUrl?.let { web.loadJellyfin(it) } }
    LaunchedEffect(dest, bridgeUrl) {
        val url = bridgeUrl
        if (dest == Dest.Bridge && url != null) web.loadBridge(url)
    }

    // Grand écran (Fold déplié) : rail de navigation à gauche ; petit écran : barre en bas
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val selectedTab = if (dest == Dest.PlayerSettings || dest == Dest.Features) Dest.Settings else dest
    val serverNow = serverUrl
    val bridgeNow = bridgeUrl
    val needsSetup = serverNow != null && serverNow.isBlank()
    val bridgeMissing = bridgeNow != null && bridgeNow.isBlank()

    BackHandler {
        when {
            web.fullscreenActive -> web.exitFullscreen()
            dest == Dest.Home && web.jellyfin.canGoBack() -> web.jellyfin.goBack()
            dest == Dest.Bridge && web.bridge.canGoBack() -> web.bridge.goBack()
            dest == Dest.PlayerSettings || dest == Dest.Features -> dest = Dest.Settings
            dest != Dest.Home -> dest = Dest.Home
            else -> activity?.moveTaskToBack(true)
        }
        Unit
    }

    val openRoute: (String) -> Unit = { route ->
        web.navigateJellyfin(route)
        dest = Dest.Home
    }

    // Premier lancement : aucune adresse n'est livrée avec l'appli, on la demande.
    if (needsSetup) {
        SetupScreen()
        return
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (wide) Modifier.safeDrawingPadding() else Modifier)
    ) {
        if (wide) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxHeight(),
            ) {
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { dest = onTabClick(tab, dest, web) },
                            icon = { Icon(tab.icon!!, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (wide) Modifier
                        else Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                    )
                    .imePadding()
            ) {
                // Les deux WebViews restent vivantes : on ne fait que les montrer / cacher
                PersistentWebView(web.jellyfin, visible = dest == Dest.Home)
                PersistentWebView(web.bridge, visible = dest == Dest.Bridge)

                if (dest == Dest.Home) {
                    WebStatus(web.jellyfinLoading, web.jellyfinError, onRetry = { web.reloadJellyfin() })
                }
                if (dest == Dest.Bridge) {
                    if (bridgeMissing) {
                        BridgeEmpty(onOpenSettings = { dest = Dest.Settings })
                    } else {
                        WebStatus(web.bridgeLoading, web.bridgeError, onRetry = { web.reloadBridge() })
                    }
                }

                when (dest) {
                    Dest.Libraries -> LibraryScreen(photosOnly = false, onOpenRoute = openRoute)
                    Dest.Photos -> LibraryScreen(photosOnly = true, onOpenRoute = openRoute)
                    Dest.Settings -> SettingsScreen(
                        onOpenRoute = openRoute,
                        onOpenPlayerSettings = { dest = Dest.PlayerSettings },
                        onOpenFeatures = { dest = Dest.Features },
                    )
                    Dest.PlayerSettings -> PlayerSettingsScreen(onBack = { dest = Dest.Settings })
                    Dest.Features -> FeaturesScreen(onBack = { dest = Dest.Settings })
                    else -> Unit
                }
            }
            if (!wide) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { dest = onTabClick(tab, dest, web) },
                            icon = { Icon(tab.icon!!, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Re-toucher l'onglet Accueil quand on y est déjà = revenir à l'accueil Jellyfin. */
private fun onTabClick(tab: Dest, current: Dest, web: WebHost): Dest {
    if (tab == Dest.Home && current == Dest.Home) web.navigateJellyfin("#/home")
    return tab
}

@Composable
private fun PersistentWebView(webView: WebView, visible: Boolean) {
    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        update = { it.visibility = if (visible) View.VISIBLE else View.GONE },
        onRelease = { /* La WebView vit avec l'activité : on ne la détruit pas ici */ },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BridgeEmpty(onOpenSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Aucun Bridge Hub configuré", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Ajoute l'adresse de ton Bridge Hub dans Réglages pour accéder aux Jellyfin des autres.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenSettings) { Text("Ouvrir les réglages") }
        }
    }
}

@Composable
private fun WebStatus(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (error != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Impossible de joindre le serveur", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(
                    "Vérifie l'adresse du serveur (Réglages) et ta connexion : Wi-Fi, données mobiles ou VPN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry) { Text("Réessayer") }
            }
        }
    } else if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
    }
}
