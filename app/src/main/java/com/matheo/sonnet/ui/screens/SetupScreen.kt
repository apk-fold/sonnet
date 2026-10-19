package com.matheo.sonnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matheo.sonnet.data.SettingsRepository
import kotlinx.coroutines.launch

/** Premier lancement : Sonnet ne connaît aucune adresse, on demande celle de ton serveur Jellyfin. */
@Composable
fun SetupScreen() {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var bridge by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Bienvenue dans Sonnet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Entre l'adresse de ton serveur Jellyfin. Elle reste sur ton téléphone et tu pourras la changer à tout moment dans Réglages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UrlField(server, "Serveur Jellyfin (ex. http://192.168.1.10:8096)") {
                server = it
                error = null
            }
            UrlField(bridge, "Bridge Hub (facultatif)") { bridge = it }
            Text(
                "Sans http:// ni https://, Sonnet ajoute http:// tout seul. Si ton serveur est derrière un VPN (Tailscale, WireGuard…), connecte-le d'abord.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val s = SettingsRepository.normalizeUrl(server)
                    if (s.isEmpty()) {
                        error = "Entre l'adresse de ton serveur Jellyfin."
                    } else {
                        scope.launch {
                            if (bridge.isNotBlank()) SettingsRepository.setBridgeUrl(bridge)
                            SettingsRepository.setServerUrl(s)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Commencer") }
        }
    }
}
