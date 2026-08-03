package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.gmoqa.fullset.data.Condition
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.sortedBy
import com.gmoqa.fullset.data.coverModel
import kotlinx.coroutines.delay

@Composable
fun GameListScreen(
    title: String,
    games: List<Game>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onOpenGame: (Long) -> Unit,
    onAddGame: (() -> Unit)?,
    subtitle: String? = null,
    sortOrder: SortOrder = SortOrder.DEFAULT,
    onSortChange: ((SortOrder) -> Unit)? = null,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (onAddGame != null) {
                FloatingActionButton(onClick = onAddGame) {
                    Icon(Icons.Filled.Add, contentDescription = "Add game")
                }
            }
        },
    ) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = title,
                subtitle = subtitle,
                trailing = if (onSortChange != null && games.isNotEmpty()) {
                    { SortMenu(current = sortOrder, onSelect = onSortChange) }
                } else null,
            )
            if (games.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = emptyIcon,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                )
            } else {
                GameShelves(
                    games = games,
                    onOpenGame = onOpenGame,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                )
            }
        }
    }
}

/** Estanterías por plataforma (reutilizable dentro de Library o standalone). */
@Composable
fun GameShelves(
    games: List<Game>,
    onOpenGame: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    focusGameId: Long? = null,
    onFocusConsumed: () -> Unit = {},
    /** Al tocar una franja: abre la vista propia de esa plataforma (ficha + juegos por lanzamiento). */
    onOpenPlatform: (String) -> Unit = {},
    /** Motivo de "comienzo": la franja muestra la mitad derecha del control a sangre (ver Collection). */
    bleedHeaderIcon: Boolean = false,
    /** Mostrar el título bajo cada carátula (Collection lo hace opcional desde Settings). */
    showGameLabels: Boolean = true,
    /** Mostrar la franja con el nombre de cada consola (Collection lo hace opcional desde Settings). */
    showPlatformTitles: Boolean = true,
    /** Orden de los juegos **dentro de cada estante**; la agrupación por consola no cambia. */
    sortOrder: SortOrder = SortOrder.DEFAULT,
) {
    val shelves = remember(games, sortOrder) {
        games.groupBy { it.platform }.mapValues { (_, list) -> list.sortedBy(sortOrder) }
    }
    // En teléfonos angostos achicamos el tile: con 140dp apenas entraban dos carátulas y media.
    val tileWidth = if (isCompactWidth()) Tokens.Size.coverTileCompact else Tokens.Size.coverTile
    val columnState = rememberLazyListState()
    // Un estado por franja: hace falta para poder correr la fila hasta el juego enfocado.
    val rowStates = remember { mutableMapOf<String, LazyListState>() }

    // Al agregar un juego, llevar la vista hasta él: subir a su franja y correr la fila. Sin esto
    // el scroll se queda donde estaba y el juego nuevo queda fuera de pantalla.
    // `games` va en la key a propósito: el id llega antes que la lista actualizada, así que si el
    // juego todavía no está se sale SIN consumir y el efecto vuelve a correr cuando la lista llega.
    LaunchedEffect(focusGameId, games) {
        val id = focusGameId ?: return@LaunchedEffect
        val game = games.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val shelfIndex = shelves.keys.indexOf(game.platform)
        if (shelfIndex < 0) return@LaunchedEffect
        // Cada franja ocupa 2 items del LazyColumn (banda + fila), o 1 si se ocultan las franjas.
        columnState.animateScrollToItem(shelfIndex * if (showPlatformTitles) 2 else 1)
        // La fila recién tiene estado cuando su franja entró en composición.
        delay(80)
        val gameIndex = shelves[game.platform]?.indexOfFirst { it.id == id } ?: 0
        rowStates[game.platform]?.animateScrollToItem(gameIndex)
        onFocusConsumed()
    }

    LazyColumn(
        state = columnState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shelves.forEach { (platform, platformGames) ->
            if (showPlatformTitles) {
                item(key = "header::$platform") {
                    // Franja a todo el ancho pintada con el color de la plataforma + logo blanco
                    // + badge contador a la derecha. Padding simétrico (+ spacedBy 4) para que la
                    // franja quede centrada en su aire, con el mismo espacio arriba y abajo.
                    PlatformBandHeader(
                        platform = platform,
                        count = platformGames.size,
                        modifier = Modifier.padding(vertical = 8.dp),
                        onClick = { onOpenPlatform(platform) },
                        bleedIcon = bleedHeaderIcon,
                    )
                }
            }
            item(key = "row::$platform") {
                val rowState = rowStates.getOrPut(platform) { LazyListState() }
                LazyRow(
                    state = rowState,
                    // Sin franja de consola, un aire extra separa una plataforma de la siguiente.
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = if (showPlatformTitles) 0.dp else 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(platformGames, key = { it.id }) { game ->
                        CoverTile(
                            game = game,
                            modifier = Modifier.width(tileWidth),
                            onClick = { onOpenGame(game.id) },
                            showLabel = showGameLabels,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverTile(
    game: Game,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    showLabel: Boolean = true,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        // Alto reservado según el aspecto típico de la plataforma: el tile mide lo mismo con o sin
        // imagen, así no salta al cargar. Encima, el punto de estado de conservación (si lo hay).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio(game.platform, game.region)),
        ) {
            SubcomposeAsyncImage(
                model = game.coverModel,
                contentDescription = game.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                // Coil 3: painter.state es un StateFlow, hay que observarlo con collectAsState().
                val state by painter.state.collectAsState()
                if (state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                } else {
                    CoverPlaceholder()
                }
            }
            game.conditionState?.let { cond ->
                ConditionDot(cond, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        if (showLabel) {
            // Alto fijo de dos líneas: la fila entera mide lo que su tile más alto, así que un
            // título largo entre dos cortos hacía crecer TODO el renglón al scrollear hasta él.
            Text(
                game.name,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** Estado de conservación como punto de color, con halo oscuro para leerse sobre cualquier carátula. */
@Composable
private fun ConditionDot(condition: Condition, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(15.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(Color(condition.dot)),
    )
}

@Composable
private fun CoverPlaceholder() {
    // Llena el contenedor (que ya reserva el alto por plataforma) y centra el icono.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.SportsEsports,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
