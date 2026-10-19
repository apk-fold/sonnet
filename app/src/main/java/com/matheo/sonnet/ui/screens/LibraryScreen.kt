package com.matheo.sonnet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matheo.sonnet.data.SessionStore
import com.matheo.sonnet.jellyfin.JellyfinApi
import com.matheo.sonnet.jellyfin.LibraryView

/** Route Jellyfin web d'une bibliothèque (même logique que appRouter.getRouteUrl de jellyfin-web 10.11). */
fun libraryRoute(view: LibraryView): String = when (view.collectionType) {
    "movies" -> "#/movies?topParentId=${view.id}&collectionType=movies"
    "tvshows" -> "#/tv?topParentId=${view.id}&collectionType=tvshows"
    "music" -> "#/music?topParentId=${view.id}&collectionType=music"
    "livetv" -> "#/livetv?collectionType=livetv"
    else -> "#/list?parentId=${view.id}" + (view.serverId?.let { "&serverId=$it" } ?: "")
}

private fun iconFor(type: String?): ImageVector = when (type) {
    "movies" -> Icons.Rounded.Movie
    "tvshows" -> Icons.Rounded.Tv
    "music" -> Icons.Rounded.MusicNote
    "photos", "homevideos" -> Icons.Rounded.Photo
    "livetv" -> Icons.Rounded.LiveTv
    "boxsets" -> Icons.Rounded.VideoLibrary
    else -> Icons.Rounded.Folder
}

@Composable
fun LibraryScreen(photosOnly: Boolean, onOpenRoute: (String) -> Unit) {
    val session by SessionStore.session.collectAsState()
    var views by remember { mutableStateOf<List<LibraryView>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(session, reloadKey) {
        val s = session ?: return@LaunchedEffect
        error = null
        views = null
        runCatching { JellyfinApi(s.server, s.token).userViews(s.userId) }
            .onSuccess { views = it }
            .onFailure { error = it.message ?: "Erreur" }
    }

    val api = session?.let { JellyfinApi(it.server, it.token) }
    val shown = views?.let { list ->
        if (photosOnly) list.filter { it.collectionType == "photos" || it.collectionType == "homevideos" } else list
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            session == null -> CenteredMessage(
                "Connecte-toi d'abord dans l'onglet Accueil : Sonnet récupère automatiquement ta session Jellyfin.",
            )
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Impossible de charger les bibliothèques", color = MaterialTheme.colorScheme.onBackground)
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { reloadKey++ }) { Text("Réessayer") }
            }
            shown == null -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            if (photosOnly) "Photos et vidéos perso" else "Bibliothèques",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (!photosOnly) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                QuickChip("Recherche", Icons.Rounded.Search) { onOpenRoute("#/search") }
                                QuickChip("Favoris", Icons.Rounded.Favorite) { onOpenRoute("#/home?tab=1") }
                                QuickChip("TV en direct", Icons.Rounded.LiveTv) { onOpenRoute("#/livetv") }
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (photosOnly) "Aucune bibliothèque de photos sur ce serveur. Crée-en une (type « Photos ») dans Administration → Médiathèques."
                            else "Aucune bibliothèque visible pour ce compte.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(shown, key = { it.id }) { view ->
                    LibraryCard(
                        view = view,
                        imageUrl = view.primaryTag?.let { tag -> api?.imageUrl(view.id, "Primary", tag, 640) },
                        onClick = { onOpenRoute(libraryRoute(view)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(view: LibraryView, imageUrl: String?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = view.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                )
            } else {
                Icon(iconFor(view.collectionType), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            view.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
