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
import androidx.compose.material3.Surface
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
    /** Alta desde el catálogo: se elige de nuestras listas y queda marcada como que la estás jugando. */
    onAddPhysical: () -> Unit,
    /**
     * Si la app lleva colección. Solo cambia **cómo se llama** cada opción del alta —físico/digital
     * con colección, de-la-lista/a-mano sin ella—: las dos existen siempre, porque elegir del
     * catálogo no es un lujo de la colección, es de donde salen el año, la editora y la carátula.
     */
    collectionEnabled: Boolean,
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
                    collectionEnabled = collectionEnabled,
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
            // Sin filetes: ahora cada juego es una tarjeta y el aire entre ellas ya las separa.
            // Un filete **más** el borde de la tarjeta sería decir dos veces lo mismo — y encima el
            // sangrado del filete seguía a una ranura que ya no tiene ancho fijo.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
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

/**
 * El alta desde Playing **siempre pregunta de dónde sale el juego**, porque las dos formas de
 * cargarlo no son intercambiables: una lo elige de nuestras listas —con año, editora, región y
 * carátula ya resueltas— y la otra lo escribe a mano.
 *
 * Lo que cambia con el modo es **la pregunta**, no si se hace. Con colección la disyuntiva es
 * *físico o digital*: dice si el juego entra o no a Collection. En "Diary only" esa pregunta no
 * significa nada —no hay colección— pero la otra sí sigue: **de la lista o a mano**. Antes se
 * asumía que sin colección no quedaba nada que preguntar y el botón iba derecho a escribir a mano,
 * y eso dejaba las 18 consolas del catálogo **inalcanzables** justo en el modo donde la app es solo
 * el diario. Ese era el bug.
 *
 * El alta desde la lista además marca el juego como que lo estás jugando: si no, el alta saldría
 * desde acá y el juego no aparecería, que se lee como que no pasó nada.
 */
@Composable
private fun AddPlayingButton(
    /** Con colección la pregunta es físico/digital; sin ella, de la lista o a mano. */
    collectionEnabled: Boolean,
    onAddPhysical: () -> Unit,
    onAddDigital: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    AddGameButton(onClick = { open = true })
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
                        title = if (collectionEnabled) "Physical" else "From a list",
                        subtitle = "Our lists",
                    ) { open = false; onAddPhysical() }
                    ChoiceCard(
                        vector = Icons.Filled.CloudQueue,
                        title = if (collectionEnabled) "Digital" else "Not listed",
                        subtitle = "Type it in",
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
    // Cada juego en su tarjeta. Antes eran filas sueltas sobre el fondo, y con carátulas de formas
    // tan distintas la lista se leía como una pila de recortes: la tarjeta le da a cada uno un
    // borde propio, y la variación de las tapas pasa a ocurrir **adentro** de algo, no contra el
    // vacío.
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = Tokens.Shape.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val alto = if (conColumnas) COVER_ALTO_AMPLIO else COVER_ALTO
            // **Alto fijo, ancho según la forma real de la tapa.**
            //
            // Antes la ranura era fija en las dos medidas y la imagen se ajustaba adentro. Con
            // aspectos que van de 0,60 en Saturn a 1,41 en SNES —más del doble— eso dejaba barras
            // grises enormes a los costados o arriba y abajo, según la consola. Una caja de SNES es
            // apaisada y una de Saturn es alta: esa forma **es** información sobre el objeto, y
            // recortarla a un molde común la borraba.
            //
            // Fijando solo el alto, todas las filas siguen midiendo lo mismo —la lista conserva su
            // pulso— y cada tapa ocupa exactamente su rectángulo, sin relleno.
            // Un juego **digital** no tiene caja: su tapa sale de SteamGridDB, que sirve pósters
            // de 2:3. Usar ahí el aspecto de la caja de la consola recortaba el arte — se veía en
            // *Vampire Survivors*, cortado arriba y abajo.
            val aspecto =
                if (game.digital) ASPECTO_POSTER else coverAspectRatio(game.platform, game.region)
            val ancho = alto * aspecto
            Box(
                modifier = Modifier
                    .size(width = ancho, height = alto)
                    .clip(Tokens.Shape.small)
                    // El fondo se pinta igual: una fila sin tapa tiene que verse como "todavía sin
                    // carátula" y no como un hueco.
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                // `SubcomposeAsyncImage` y no `AsyncImage`: hay que distinguir "sin carátula" de
                // "la carátula no cargó". Con `AsyncImage`, una URL que falla no dibuja nada y la
                // ranura queda en gris liso para siempre, sin decir por qué.
                SubcomposeAsyncImage(
                    model = game.coverModel,
                    contentDescription = game.name,
                    // `Fit` y no `Crop`. La ranura ya viene con la proporción esperada, así que en
                    // el caso normal no sobra nada; pero cuando la tapa real no coincide —una
                    // reedición con otra caja, una imagen de otra fuente— `Crop` **recorta el arte**
                    // y `Fit` deja un filo del fondo. Perder un borde de la tapa es peor que un filo.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable(onClick = onOpenCover),
                ) {
                    val estado by painter.state.collectAsState()
                    if (estado is AsyncImagePainter.State.Success) {
                        SubcomposeAsyncImageContent()
                    } else {
                        Icon(
                            Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // **Quién lo hizo y quién lo publicó**, que hasta ahora no se mostraba en esta
                // pantalla. Si coinciden va una sola vez: repetir "Nintendo · Nintendo" es ruido.
                val empresas = listOfNotNull(
                    game.developer.ifBlank { null },
                    game.publisher.ifBlank { null }.takeIf { !it.equals(game.developer, true) },
                ).joinToString("  ·  ")
                if (empresas.isNotEmpty()) {
                    Text(
                        empresas,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // La procedencia: consola, si es digital, y en pantalla angosta también el año.
                val procedencia = buildList {
                    if (game.platform.isNotBlank()) add(game.platform)
                    if (game.digital) add("Digital")
                    if (!conColumnas) game.releaseYear?.takeIf { it > 0 }?.let { add(it.toString()) }
                }.joinToString("  ·  ")
                if (procedencia.isNotEmpty()) {
                    Text(
                        procedencia,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Lo que escribiste, en su propia línea: es lo único de la fila que hiciste vos, y
                // mezclado entre la consola y el año se perdía.
                val diario = buildList {
                    if (game.noteCount > 0) add(plural(game.noteCount, "note"))
                    if (game.photoCount > 0) add(plural(game.photoCount, "photo"))
                }.joinToString("  ·  ")
                if (diario.isNotEmpty()) {
                    Text(
                        diario,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Columna de identidad, alineada a la derecha: el año arriba y la región debajo. Va a la
            // derecha y no pegada al título para que se pueda **recorrer en vertical** —es lo que
            // hace una discografía con el año— y para que el nombre del juego siga siendo lo único
            // que manda el borde izquierdo del bloque de texto.
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
}

/**
 * Ranura de la carátula: **fija dentro de un ancho, distinta entre anchos**.
 *
 * Fija, para que todas las filas midan lo mismo y la lista tenga pulso — es lo que separa un
 * catálogo de una pila de recuadros. Pero fija en 52dp para todos, la tapa pasaba del 12,7% del
 * ancho en un teléfono al 7,8% en la tablet y al 4,9% en horizontal: se achicaba justo cuando había
 * más lugar. En una lista donde la carátula es el ancla, eso la degrada a viñeta.
 */
/** Póster 2:3, el formato que sirve SteamGridDB para los juegos sin caja física. */
private const val ASPECTO_POSTER = 0.667f

private val COVER_ANCHO = 52.dp
private val COVER_ALTO = 68.dp
private val COVER_ANCHO_AMPLIO = 76.dp
private val COVER_ALTO_AMPLIO = 100.dp

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
