package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(games, key = { it.id }) { game ->
                    PlayingCard(
                        game = game,
                        onClick = { onOpenGame(game.id) },
                        onOpenCover = { visorCaratula.show(game.coverModel, game.name) },
                    )
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
private fun PlayingCard(game: Game, onClick: () -> Unit, onOpenCover: () -> Unit) {
    val model = game.coverModel
    // Antes el fondo era la propia carátula desenfocada a 28dp con un degradado negro encima. La
    // idea era darle a cada card el color del juego; lo que daba era barro, distinto en cada una, y
    // la misma imagen apareciendo dos veces —borrosa de fondo y nítida al costado—. Superficie
    // sólida: se gana contraste para el texto y la lista recupera ritmo.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(Tokens.Shape.control)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // El nombre es el protagonista, y por eso el badge va **después**: relleno ámbar y
                // arriba del título, le ganaba en peso visual a lo único que importa leer. Además no
                // todos los juegos lo llevan, así que arriba desalineaba los títulos entre cards.
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Metadata sutil en una línea: plataforma (logo) + notas/fotos.
                val counts = buildList {
                    if (game.noteCount > 0) add(plural(game.noteCount, "note"))
                    if (game.photoCount > 0) add(plural(game.photoCount, "photo"))
                }.joinToString("  ·  ")
                // FlowRow y no Row: en un teléfono angosto "PlayStation 5" más "5 notes · 2 photos"
                // no entran juntos, y en una fila el conteo se cortaba a "2 …". Que baje de línea.
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (game.platform.isNotBlank()) {
                        PlatformLabel(
                            platform = game.platform,
                            iconSize = 14.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            nameStyle = MaterialTheme.typography.labelMedium,
                            nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (game.digital) DigitalBadge()
                    if (counts.isNotBlank()) {
                        Text(
                            counts,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Ancho acotado (weight) y no fijo: una tapa apaisada —Sega CD, algunos PC— si no se
            // come el ancho del texto. Se ajusta (Fit) dentro del hueco.
            Box(
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = game.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterEnd,
                        // Clickable propio: gana sobre el de la card entera, así que tocar la tapa
                        // la amplía en vez de abrir el juego.
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(Tokens.Shape.small)
                            .clickable(onClick = onOpenCover),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxHeight().aspectRatio(0.75f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = Tokens.Overlay.placeholderIcon,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

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
