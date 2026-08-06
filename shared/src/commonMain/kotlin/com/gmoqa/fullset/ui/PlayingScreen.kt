package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.AsyncImagePainter
import androidx.compose.runtime.collectAsState
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.coverModel

@Composable
fun PlayingScreen(
    onOpenTimeline: () -> Unit,
    /** Alta física: se elige del catálogo y queda marcada como que la estás jugando. */
    onAddPhysical: () -> Unit,
    /**
     * Si preguntar físico o digital. En falso —modo "Diary only"— el botón va directo al alta
     * digital: sin colección, "físico" no significa nada y preguntarlo es una decisión de más.
     */
    askGameType: Boolean,
    games: List<Game>,
    onOpenGame: (Long) -> Unit,
    onAddDigital: () -> Unit,
) {
    val visorCaratula = rememberCoverViewer()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Playing",
            subtitle = if (games.isEmpty()) null else "${games.size} in progress",
            trailing = {
                // El timeline vive acá y no en Collection: es una vista del **diario**, y en modo
                // "Diary only" Collection no existe — ahí quedaba inalcanzable.
                IconButton(onClick = onOpenTimeline) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Timeline")
                }
                AddPlayingButton(
                    askGameType = askGameType,
                    onAddPhysical = onAddPhysical,
                    onAddDigital = onAddDigital,
                )
            },
        )
        if (games.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.PlayCircle,
                title = "Nothing playing",
                subtitle = "Open a game and mark it as playing.",
            )
        } else {
            // Todo mezclado (sin agrupar), un card a todo el ancho por juego.
            // Filas sobre el fondo, separadas por un filete: la card gris redondeada era un
            // contenedor que no contenía nada —más del 60% era vacío— y siete de esas apiladas se
            // leen como bloques, no como una lista.
            // El ancho se mide **una vez** y no por fila: 600dp es el escalón de Material donde
            // deja de ser un teléfono. Debajo, la fila es cover + título + una línea; encima entra
            // una segunda columna, porque a 668dp más de la mitad del ancho quedaba vacío.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val conColumnas = maxWidth >= 600.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                    if (i > 0) {
                        HorizontalDivider(
                            // Sangrado hasta donde empieza el texto: el filete acompaña a la
                            // columna, no corta la página al medio.
                            modifier = Modifier.padding(start = 20.dp + COVER_ANCHO + 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    PlayingCard(
                        game = game,
                        conColumnas = conColumnas,
                        onClick = { onOpenGame(game.id) },
                        onOpenCover = { visorCaratula.show(game.coverModel, game.name) },
                    )
                }
            }
            }
        }
    }

    if (visorCaratula.model != null) {
        CoverViewer(visorCaratula.model, visorCaratula.description) { visorCaratula.dismiss() }
    }
}

/** Menú "⋮" del header de Playing: por ahora, dar de alta un juego digital (no poseído). */
/**
 * El alta desde Playing pregunta **qué** estás por agregar, porque acá conviven las dos cosas: un
 * cartucho que tenés en la mano y un juego digital que no poseés. En Collection no hace falta —esa
 * pantalla *es* la colección física— y por eso ahí el botón va directo al catálogo.
 *
 * El físico entra al catálogo y además queda marcado como que lo estás jugando: si no, el alta
 * saldría desde acá y el juego no aparecería, que se lee como que no pasó nada.
 */
@Composable
private fun AddPlayingButton(
    askGameType: Boolean,
    onAddPhysical: () -> Unit,
    onAddDigital: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Sin colección que llevar, la única alta posible es la digital: se dispara sola. Es el mismo
    // criterio de `PhotoSourceButton` cuando la plataforma no puede sacar fotos.
    val alTocar = { if (askGameType) open = true else onAddDigital() }
    AddGameButton(onClick = alTocar)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            shape = Tokens.Shape.dialog,
            title = { Text("Add game") },
            text = {
                // Tope de ancho: las tarjetas son cuadradas, así que en una tablet el diálogo se
                // estira y quedan dos cuadrados grandes con el ícono flotando en el medio.
                Row(
                    modifier = Modifier.widthIn(max = Tokens.Size.contentMax),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xl),
                ) {
                    ChoiceCard(
                        vector = Icons.Filled.VideogameAsset,
                        title = "Physical",
                        subtitle = "Our lists",
                    ) { open = false; onAddPhysical() }
                    ChoiceCard(
                        vector = Icons.Filled.CloudQueue,
                        title = "Digital",
                        subtitle = "SteamGridDB",
                    ) { open = false; onAddDigital() }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayingCard(
    game: Game,
    conColumnas: Boolean,
    onClick: () -> Unit,
    onOpenCover: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La carátula, a la izquierda y en una ranura de tamaño **fijo**. Antes estaba pegada al
        // borde opuesto al título, con el ancho del juego entre medio: el ojo tenía que cruzar un
        // vacío para relacionar un nombre con su tapa. Acá se leen juntos.
        //
        // La ranura es fija y la imagen se ajusta dentro: las tapas van de la caja vertical de NES
        // a la apaisada de Genesis, y sin ranura fija cada fila arrancaría a una altura distinta y
        // el borde izquierdo de la lista zigzaguearía.
        Box(
            modifier = Modifier
                .size(width = COVER_ANCHO, height = COVER_ALTO)
                // La ranura se pinta aunque no haya tapa. Sin fondo, una fila sin carátula deja un
                // hueco invisible y se lee como desalineada, no como "todavía sin tapa" — y el
                // ícono de reserva a media opacidad sobre un fondo casi negro no se ve.
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val model = game.coverModel
            // `SubcomposeAsyncImage` y no `AsyncImage`: hay que distinguir "sin carátula" de
            // "la carátula no cargó". Con `AsyncImage`, una URL que falla no dibuja nada y la
            // ranura queda en gris liso para siempre, sin decir por qué.
            SubcomposeAsyncImage(
                model = model,
                contentDescription = game.name,
                contentScale = ContentScale.Fit,
                // Sin redondear: es el escaneo de un objeto con esquinas rectas. Redondearlo
                // decora contradiciendo al dato.
                modifier = Modifier.fillMaxSize().clickable(onClick = onOpenCover),
            ) {
                val estado by painter.state.collectAsState()
                if (estado is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Toda la jerarquía la hace el tipo: el nombre a plena intensidad, lo demás un escalón
            // abajo y apagado. Sin cajas, sin color, sin badges compitiendo.
            Text(
                game.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Una sola línea para todo lo secundario, separada por puntos medios. "DIGITAL" entra
            // como una palabra más y no como una etiqueta: es un adjetivo del juego, no una alarma.
            val secundario = buildList {
                if (game.platform.isNotBlank()) add(game.platform)
                if (game.digital) add("Digital")
                // Con columna aparte, el año y la región salen de acá: repetirlos sería ruido.
                if (!conColumnas) {
                    game.releaseYear?.takeIf { it > 0 }?.let { add(it.toString()) }
                }
                if (game.noteCount > 0) add(plural(game.noteCount, "note"))
                if (game.photoCount > 0) add(plural(game.photoCount, "photo"))
            }.joinToString("  ·  ")
            if (secundario.isNotEmpty()) {
                Text(
                    secundario,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        // Columna de identidad, alineada a la derecha: el año arriba y la región debajo. Va a la
        // derecha y no pegada al título para que se pueda **recorrer en vertical** —es lo que hace
        // una discografía con el año— y para que el nombre del juego siga siendo lo único que
        // manda el borde izquierdo del bloque de texto.
        if (conColumnas) {
            val anio = game.releaseYear?.takeIf { it > 0 }?.toString()
            val region = game.region.takeIf { it.isNotBlank() }
            if (anio != null || region != null) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (anio != null) {
                        Text(
                            anio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (region != null) {
                        Text(
                            region,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ranura de la carátula. Fija para que todas las filas midan lo mismo y la lista tenga pulso: es lo
 * que separa un catálogo de una pila de recuadros.
 */
private val COVER_ANCHO = 52.dp
private val COVER_ALTO = 68.dp

private fun plural(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"

/**
 * Marca de que el juego es **digital** —no lo poseés, no está en tu colección—.
 *
 * Contorno y no relleno: es una nota al pie sobre la procedencia, y en ámbar sólido con negrita
 * máxima le ganaba en peso al título del juego, que es lo único que se lee de verdad en la lista.
 */
@Composable
private fun DigitalBadge() {
    Text(
        "DIGITAL",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), Tokens.Shape.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
