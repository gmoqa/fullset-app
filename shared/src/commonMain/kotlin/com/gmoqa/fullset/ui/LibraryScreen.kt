package com.gmoqa.fullset.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.domain.GameSearch
import com.gmoqa.fullset.data.coverModel
import com.gmoqa.fullset.data.SortOrder

/** La colección: estanterías de carátulas por plataforma, con buscador difuso. */
@Composable
fun LibraryScreen(
    games: List<Game>,
    onOpenGame: (Long) -> Unit,
    /**
     * Alta desde nuestros catálogos. Acá **siempre es física**: Collection *es* la colección
     * física, así que no hay nada que preguntar. El alta digital vive en Playing, que es donde un
     * juego que no poseés tiene sentido.
     */
    onAddPhysical: () -> Unit,
    focusGameId: Long? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenPlatform: (String) -> Unit = {},
    showLabels: Boolean = true,
    sortOrder: SortOrder = SortOrder.DEFAULT,
    onSortChange: (SortOrder) -> Unit = {},
    showConsoleTitles: Boolean = true,
) {
    // La búsqueda vive detrás de la lupa: mientras no la abrís no ocupa nada de pantalla.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = searchOpen && query.isNotBlank()
    // Con ~100 juegos el filtrado es instantáneo; se recalcula solo si cambia la lista o el texto.
    val results = remember(games, query) { GameSearch.filter(games, query) }
    val platformCount = remember(games) { games.map { it.platform }.distinct().size }

    fun closeSearch() {
        searchOpen = false
        query = ""
    }

    // Atrás cierra la búsqueda antes de salir de la pantalla.
    BackHandler(enabled = searchOpen) { closeSearch() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (searchOpen) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onClose = { closeSearch() },
            )
        } else {
            ScreenHeader(
                title = "Collection",
                subtitle = "${games.size} games · $platformCount platforms",
                trailing = {
                    if (games.isNotEmpty()) {
                        SortMenu(current = sortOrder, onSelect = onSortChange)
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search collection")
                        }
                    }
                    AddGameButton(onClick = onAddPhysical)
                },
            )
        }

        when {
            games.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.VideogameAsset,
                title = "No games yet",
                subtitle = "Add your first game to start your collection.",
                action = {
                    FilledTonalButton(onClick = onAddPhysical, shape = Tokens.Shape.control) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add game")
                    }
                },
            )

            searching && results.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.SearchOff,
                title = "No matches",
                subtitle = "Try another title, or search by platform.",
            )

            // Buscando mostramos una lista plana ordenada por relevancia: agrupar por plataforma
            // estorba cuando lo que querés es un juego puntual.
            searching -> SearchResults(results = results, onOpenGame = onOpenGame)

            else -> GameShelves(
                games = games,
                onOpenGame = onOpenGame,
                contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp),
                focusGameId = focusGameId,
                onFocusConsumed = onFocusConsumed,
                onOpenPlatform = onOpenPlatform,
                bleedHeaderIcon = true,
                showGameLabels = showLabels,
                showPlatformTitles = showConsoleTitles,
                sortOrder = sortOrder,
            )
        }
    }
}

/**
 * "Add game" abre un menú en vez de ir directo al catálogo, porque las dos altas van por caminos
 * distintos: **físico** se elige de nuestras listas por consola, y **digital** se busca en
 * SteamGridDB, que es donde están los juegos modernos que ningún catálogo retro tiene.
 *
 * Antes el alta digital vivía escondida en el menú de Playing, así que desde Collection no había
 * forma de llegar.
 */
/**
 * Barra de búsqueda que reemplaza al header mientras buscás: así no roba espacio permanente.
 * Toma el foco sola al abrirse para poder tipear de una.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search your collection") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = Tokens.Shape.pill,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
    }
}

@Composable
private fun SearchResults(results: List<Game>, onOpenGame: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(results, key = { it.id }) { game ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGame(game.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Alto fijo y ancho según el aspecto de la plataforma: las filas quedan parejas
                // y la carátula se ve completa, sin recortes.
                CoverThumb(
                    model = game.coverModel,
                    contentDescription = game.name,
                    modifier = Modifier
                        .height(54.dp)
                        .aspectRatio(coverAspectRatio(game.platform, game.region)),
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        game.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            game.platform.takeIf { it.isNotBlank() },
                            game.releaseYear?.toString(),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
