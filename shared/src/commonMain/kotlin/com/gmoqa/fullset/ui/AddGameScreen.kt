package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.data.CoverArt
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.GameCatalog
import com.gmoqa.fullset.data.Platform
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SteamGridGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Algo que ya está registrado (en la colección o en la wishlist) y que se marca en la lista del
 * catálogo para no agregarlo dos veces. [dot] es el color del punto (estado de conservación); si
 * es null el punto va neutro, porque lo que importa es que ya lo tenés anotado.
 */
data class CatalogMark(
    val platform: String,
    val slug: String,
    val title: String,
    val label: String,
    val dot: Long? = null,
    /** Si además impide volver a tocar la fila (duplicaría el registro). */
    val blocks: Boolean = true,
    /** Región de la copia que tenés. Vacío = sin dato (cargas viejas o altas a mano). */
    val region: String = "",
)

/**
 * Flujo "elegir un juego" en **2 pasos**. La usan la Biblioteca y la Wishlist:
 *  1. Plataforma — grilla de cubos cuadrados.
 *  2a. Título — buscador del catálogo (plataformas con catálogo: retro), entrega vía [onPicked].
 *  2b. A mano — título + carátula opcional (plataformas sin catálogo, como PS5), vía [onAddManual].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameScreen(
    title: String,
    platforms: List<Platform>,
    catalog: GameCatalog,
    onCancel: () -> Unit,
    onPicked: (platform: Platform, entry: CatalogEntry, coverUrl: String) -> Unit,
    /** Lo ya registrado: se marca en la lista del catálogo (punto + etiqueta). */
    marks: List<CatalogMark> = emptyList(),
    onAddManual: (platform: Platform, title: String, coverUrl: String, cover: PlatformImage?) -> Unit,
    coverSearchEnabled: Boolean = false,
    onSearchGames: suspend (title: String) -> List<SteamGridGame> = { emptyList() },
    onCoversFor: suspend (gameId: Int) -> List<String> = { emptyList() },
    region: RegionFilter = RegionFilter.NTSC_U,
) {
    var selected by remember { mutableStateOf<Platform?>(null) }
    // Arranca en tu región (la de Settings) y se puede cambiar acá mismo para este alta, sin tocar
    // la preferencia global: agregar un import japonés no debería obligar a ir y volver de Settings.
    var pickedRegion by remember(region) { mutableStateOf(region) }
    val platform = selected
    // Ficha técnica de la plataforma elegida (misma ⓘ que en Collection, en la franja del paso 2).
    var showInfo by remember { mutableStateOf(false) }

    // Estando en el paso 2, "atrás" vuelve al paso 1 (no cancela). En el paso 1 lo maneja el
    // BackHandler de MainActivity (cierra el flujo).
    BackHandler(enabled = platform != null) { selected = null }

    Scaffold(
        topBar = {
            if (platform == null) {
                // Paso 1: barra normal con el título del flujo; atrás cancela.
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            } else {
                // Paso 2: la franja de la plataforma ES el encabezado, con la flecha adentro.
                PlatformBandHeader(
                    platform = platform.name,
                    // Sin catálogo (PS5…) no hay nada que contar: sin badge.
                    count = remember(platform, pickedRegion) {
                        if (platform.catalogFile.isBlank()) null
                        else catalog.entries(platform, pickedRegion).size
                    },
                    onBack = { selected = null },
                    // Misma ficha que en Collection: solo si la plataforma la trae.
                    onInfo = if (platform.info != null) ({ showInfo = true }) else null,
                    windowInsets = WindowInsets.statusBars,
                    trailing = {
                        val regions = remember(platform) { platform.selectableRegions() }
                        if (regions.size > 1) {
                            RegionPicker(
                                current = pickedRegion,
                                options = regions,
                                onSelect = { pickedRegion = it },
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (platform == null) {
                PlatformStep(platforms = platforms, catalog = catalog, region = region, onSelect = { selected = it })
            } else if (platform.catalogFile.isNotBlank()) {
                TitleStep(
                    platform = platform, catalog = catalog, region = pickedRegion,
                    marks = marks, onPicked = onPicked,
                )
            } else {
                // Plataforma sin catálogo (PS5…): se carga a mano.
                ManualEntryStep(
                    platform = platform,
                    coverSearchEnabled = coverSearchEnabled,
                    onSearchGames = onSearchGames,
                    onCoversFor = onCoversFor,
                    onAdd = { t, url, uri -> onAddManual(platform, t, url, uri) },
                )
            }
        }

        // `info` en un val local: `platform.info` es de otro módulo (:shared) y no admite smart-cast.
        val info = platform?.info
        if (showInfo && info != null) {
            PlatformInfoSheet(
                platform = platform.name,
                info = info,
                region = region,
                onDismiss = { showInfo = false },
            )
        }
    }
}

// ------------------------------------------------------- Paso 1: plataforma

@Composable
private fun PlatformStep(
    platforms: List<Platform>,
    catalog: GameCatalog,
    region: RegionFilter,
    onSelect: (Platform) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(platforms) { _, p ->
            if (p.enabled) {
                PlatformCube(
                    platform = p,
                    // Offline: el conteo sale del JSON de catálogo empaquetado.
                    count = remember(p.id, region) { catalog.entries(p, region).size },
                    onClick = { onSelect(p) },
                )
            } else {
                // Bloqueada: sin catálogo aún, no se toca su JSON ni se puede seleccionar.
                LockedCube(platform = p)
            }
        }
    }
}

/**
 * Cubo de una plataforma soportada: se pinta con **su color de identidad** ([platformBandColor],
 * el mismo de las franjas de estantería), con el logo blanco centrado y el nombre + conteo al pie.
 */
@Composable
private fun PlatformCube(platform: Platform, count: Int, onClick: () -> Unit) {
    val band = platformBandColor(platform.name) ?: MaterialTheme.colorScheme.surfaceVariant
    CubeSurface(container = band, content = Color.White, onClick = onClick) {
        // Ícono proporcional al cubo (no un tamaño fijo): escala con el ancho disponible y se acota
        // al alto libre para no invadir el nombre. Se ve grande en tablet y bien en teléfono.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val glyphSize = (maxWidth * 0.62f).coerceAtMost(maxHeight)
            PlatformGlyph(
                platform = platform.name,
                size = glyphSize,
                tint = Color.White,
                fallback = { Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(glyphSize)) },
            )
        }
        // Sin catálogo (PS5…) no hay conteo: se anuncia que se carga a mano.
        val tag = if (platform.catalogFile.isBlank()) "Add by hand" else "$count games"
        CubeCaption(name = platform.name, tag = tag, tagColor = Color.White.copy(alpha = 0.7f))
    }
}

/** Cubo bloqueado (plataforma aún no soportada): apagado, candado + "Soon", no clicable. */
@Composable
private fun LockedCube(platform: Platform) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant.copy(alpha = 0.5f)
    CubeSurface(container = scheme.surfaceVariant.copy(alpha = 0.35f), content = muted, onClick = null) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(22.dp), tint = muted)
        }
        CubeCaption(name = platform.name, tag = "Soon", tagColor = muted)
    }
}

/** Marco cuadrado común de los cubos. Si [onClick] es null el cubo no reacciona al toque. */
@Composable
private fun CubeSurface(
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val base = Modifier.fillMaxWidth().aspectRatio(1f)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = clickable,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = body,
        )
    }
}

@Composable
private fun CubeCaption(name: String, tag: String, tagColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(tag, style = MaterialTheme.typography.bodySmall, color = tagColor)
    }
}

// ----------------------------------------------------------- Paso 2: título

@Composable
private fun TitleStep(
    platform: Platform,
    catalog: GameCatalog,
    region: RegionFilter,
    marks: List<CatalogMark>,
    onPicked: (platform: Platform, entry: CatalogEntry, coverUrl: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val results = remember(platform, query, region) { catalog.search(platform, region, query) }
    // Lo ya registrado de esta plataforma, indexado por slug y por título (los juegos viejos del
    // Excel pueden no tener slug). Como `marks` es reactivo, al agregar uno la etiqueta sale sola.
    // Las que bloquean se indexan últimas: si un juego está en la colección y en la wishlist, manda
    // la marca del destino en el que estás.
    val markIndex = remember(marks, platform) {
        val index = HashMap<String, CatalogMark>()
        marks.filter { it.platform == platform.name }.sortedBy { it.blocks }.forEach { mark ->
            mark.slug.ifBlank { null }?.let { index[it] = mark }
            index[mark.title.lowercase()] = mark
        }
        index
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search titles…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )

    if (saving) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(results) { entry ->
            val owned = markIndex[entry.slug] ?: markIndex[entry.title.lowercase()]
            // Tener la edición americana no impide querer la japonesa: son piezas distintas. Solo
            // bloquea la copia de la MISMA región; la de otra se muestra como dato, sin trabar.
            val sameRegion = owned == null || owned.region.isBlank() || owned.region == entry.region
            val mark = when {
                owned == null -> null
                sameRegion -> owned
                else -> owned.copy(label = "Have ${owned.region}", blocks = false, dot = null)
            }
            ResultRow(
                platform = platform,
                entry = entry,
                mark = mark,
                // Ya agregado: no se puede tocar de nuevo (evita duplicados).
                enabled = !saving && mark?.blocks != true,
                onClick = {
                    saving = true
                    scope.launch {
                        // Carátula horneada en el catálogo; si no la trae, se deriva del título.
                        val url = entry.coverUrl.ifBlank { CoverArt.resolve(platform, entry.title) ?: "" }
                        onPicked(platform, entry, url)
                        // No se cierra el flujo: quedás en la lista para seguir agregando.
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ResultRow(
    platform: Platform,
    entry: CatalogEntry,
    mark: CatalogMark?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            model = entry.coverUrl.ifBlank { CoverArt.primaryUrl(platform, entry.title) },
            contentDescription = null,
            modifier = Modifier.size(width = 46.dp, height = 62.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Los que ya están registrados quedan atenuados: el foco va a los que podés agregar.
                color = if (mark != null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Ya registrado: punto + etiqueta ("Added" en tu colección/wishlist, "Owned" si lo tenés).
        if (mark != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                // El punto dice "ya está"; el color, en qué estado. Sin estado cargado va neutro.
                val dot = mark.dot?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dot))
                Text(
                    mark.label,
                    style = MaterialTheme.typography.labelMedium,
                    // La marca informativa ("Owned" en la wishlist) va apagada; la que bloquea, viva.
                    color = if (mark.blocks) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------- Paso 2 (sin catálogo): a mano

/**
 * Alta de un juego físico para plataformas sin catálogo (PS5…), con la **misma forma** que las de
 * catálogo: un buscador arriba y la lista de juegos de SteamGridDB debajo. Elegís el juego, después
 * una de sus carátulas (o "sin carátula"), y entra a la colección. El destino digital va aparte,
 * desde Playing.
 */
@Composable
private fun ManualEntryStep(
    platform: Platform,
    coverSearchEnabled: Boolean,
    onSearchGames: suspend (String) -> List<SteamGridGame>,
    onCoversFor: suspend (Int) -> List<String>,
    onAdd: (title: String, coverUrl: String, cover: PlatformImage?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var games by remember { mutableStateOf<List<SteamGridGame>>(emptyList()) }
    var chosen by remember { mutableStateOf<SteamGridGame?>(null) }
    var covers by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Viendo las carátulas, "atrás" vuelve a la lista de juegos (no sale del flujo).
    BackHandler(enabled = chosen != null) { chosen = null; covers = emptyList() }

    // Busca mientras escribís, con una pausa para no pegarle a la API en cada tecla.
    LaunchedEffect(query) {
        chosen = null
        covers = emptyList()
        val q = query.trim()
        if (!coverSearchEnabled || q.length < 2) {
            games = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        games = onSearchGames(q)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search ${platform.name}…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // Juego elegido → elegí una de sus carátulas (o agregá sin ninguna).
                chosen != null -> ChooseCoverStep(
                    game = chosen!!,
                    covers = covers,
                    loading = loading,
                    onPick = { url -> onAdd(chosen!!.name, url, null) },
                    onSkip = { onAdd(chosen!!.name, "", null) },
                )

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                // La lista de juegos que coinciden: tocás el correcto → trae sus carátulas.
                games.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(games) { game ->
                        Text(
                            game.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = game
                                    scope.launch {
                                        loading = true
                                        covers = onCoversFor(game.id)
                                        loading = false
                                    }
                                }
                                .padding(vertical = 14.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }

                // Escribiste algo pero no hay match (o no hay buscador): agregar por el nombre tipeado.
                query.trim().length >= 2 -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.SearchOff,
                    title = "Not in the list",
                    subtitle = "Add it by the name you typed — you can set a cover later.",
                    action = {
                        FilledTonalButton(onClick = { onAdd(query.trim(), "", null) }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add “${query.trim()}”")
                        }
                    },
                )

                else -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.Search,
                    title = "Search ${platform.name}",
                    subtitle = "Type a game name to find it and pick a cover.",
                )
            }
        }
    }
}

/** Paso final del alta sin catálogo: elegir una carátula del juego (o agregarlo sin ninguna). */
@Composable
private fun ChooseCoverStep(
    game: SteamGridGame,
    covers: List<String>,
    loading: Boolean,
    onPick: (String) -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Pick a cover for “${game.name}”",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            covers.isEmpty() -> Text(
                "No covers found for this game.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Las carátulas de SteamGridDB son 600×900 (2:3).
                itemsIndexed(covers) { _, url ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(url) },
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Add without a cover")
        }
    }
}

/**
 * Selector de carátula reutilizable (alta manual y digital). Con [searchEnabled], **"Search game"**
 * busca el título en SteamGridDB y muestra la **lista de juegos** para elegir el correcto; al elegir
 * uno se traen **sus carátulas**. También **"From gallery"** para una imagen propia. El padre guarda
 * la selección ([onChange]) y puede autocompletar el título con el juego elegido ([onGamePicked]).
 */
@Composable
fun CoverPickerField(
    queryTitle: String,
    aspect: Float,
    searchEnabled: Boolean,
    onSearchGames: suspend (String) -> List<SteamGridGame>,
    onCoversFor: suspend (Int) -> List<String>,
    onGamePicked: (name: String) -> Unit,
    cover: PlatformImage?,
    coverUrl: String,
    onChange: (coverUrl: String, cover: PlatformImage?) -> Unit,
) {
    var gameHits by remember { mutableStateOf<List<SteamGridGame>>(emptyList()) }
    var covers by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickImage = rememberImagePicker { image ->
        if (image != null) { onChange("", image); gameHits = emptyList(); covers = emptyList() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cover (optional)", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverBox(model = cover?.model ?: coverUrl.ifBlank { null }, aspect = aspect)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searchEnabled) {
                    Button(
                        onClick = {
                            scope.launch {
                                searching = true
                                covers = emptyList()
                                gameHits = onSearchGames(queryTitle.trim())
                                searching = false
                            }
                        },
                        enabled = queryTitle.isNotBlank() && !searching,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search game")
                    }
                }
                OutlinedButton(
                    onClick = { pickImage() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("From gallery")
                }
            }
        }

        when {
            searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Searching…", style = MaterialTheme.typography.bodyMedium)
            }

            // Paso 1: la lista de juegos que coinciden. Elegís el correcto → trae sus carátulas.
            gameHits.isNotEmpty() -> Column {
                Text(
                    "Pick the game:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                gameHits.take(8).forEach { game ->
                    Text(
                        game.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onGamePicked(game.name)
                                scope.launch {
                                    searching = true
                                    gameHits = emptyList()
                                    covers = onCoversFor(game.id)
                                    searching = false
                                }
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }

            // Paso 2: las carátulas del juego elegido.
            covers.isNotEmpty() -> Column {
                Text(
                    "Pick a cover:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(covers) { url ->
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(aspect)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onChange(url, null); covers = emptyList() },
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Miniatura de la carátula elegida (URL o Uri local); placeholder si aún no hay ninguna. */
@Composable
private fun CoverBox(model: Any?, aspect: Float) {
    Box(
        modifier = Modifier
            .width(Tokens.Size.coverTileCompact)
            .aspectRatio(aspect)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
