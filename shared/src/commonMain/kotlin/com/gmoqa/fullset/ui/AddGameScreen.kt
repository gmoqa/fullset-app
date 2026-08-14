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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import coil3.compose.AsyncImage
import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.domain.CatalogMark
import com.gmoqa.fullset.domain.cortesPorRegion
import com.gmoqa.fullset.domain.indiceDeMarcas
import com.gmoqa.fullset.data.CoverArt
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.GameCatalog
import com.gmoqa.fullset.data.Platform
import com.gmoqa.fullset.data.PlatformGeneration
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.groupedByGeneration
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SteamGridGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * Flujo "elegir un juego" en **2 pasos**. La usan la Biblioteca y la Wishlist:
 *  1. Plataforma — grilla de cubos cuadrados, solo las que tienen catálogo.
 *  2. Título — buscador del catálogo, entrega vía [onPicked].
 *
 * Sin catálogo no hay nada que elegir, así que esas consolas (la PS5) no aparecen acá: se cargan a
 * mano desde Playing, que es donde vive lo que jugás sin poseerlo.
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
    region: RegionFilter = RegionFilter.NTSC_U,
) {
    var selected by remember { mutableStateOf<Platform?>(null) }
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
                    // El total de la consola, sumando sus regiones: la lista de abajo las muestra
                    // todas de corrido, así que el número tiene que contar lo mismo que se ve.
                    // Sale del config precalculado; contarlo acá obligaba a parsear los catálogos
                    // solo para poner un número. Null (la PS5) = sin catálogo, sin badge.
                    count = catalog.countAllRegions(platform).takeIf { it > 0 },
                    onBack = { selected = null },
                    // Misma ficha que en Collection: solo si la plataforma la trae.
                    onInfo = if (platform.info != null) ({ showInfo = true }) else null,
                    windowInsets = WindowInsets.statusBars,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (platform == null) {
                PlatformStep(platforms = platforms, region = region, onSelect = { selected = it })
            } else {
                TitleStep(platform = platform, catalog = catalog, marks = marks, onPicked = onPicked)
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
    region: RegionFilter,
    onSelect: (Platform) -> Unit,
) {
    // Solo las que tienen de dónde elegir. Se filtra por catálogo en **cualquier** región y no en la
    // que está puesta: si no, una consola desaparecería al cambiar de región, que se lee como que la
    // app la perdió. Hoy esto deja afuera a la PS5, que se carga a mano desde Playing.
    val conCatalogo = remember(platforms) { platforms.filter { it.hasCatalog } }

    // Sobremesa y portátiles en solapas separadas. No es una taxonomía: es que se buscan aparte.
    // Ordenadas juntas por año, la Game Gear caía entre la Genesis y la SNES —correcto y difícil de
    // encontrar— y la 3DS quedaba sola inaugurando una 8ª generación con una sola consola.
    var portatiles by remember { mutableStateOf(false) }
    val visibles = remember(conCatalogo, portatiles) {
        conCatalogo.filter { it.isHandheld == portatiles }
    }
    val generaciones = remember(visibles) { visibles.groupedByGeneration() }

    // Las columnas salen del ancho, no de un número fijo. Con `Fixed(3)` la tablet en horizontal
    // (>1000dp) daba cubos de ~315dp: un glifo enorme flotando en el medio. Tres es el piso, así que
    // en teléfono no cambia nada; a partir de ahí entra una columna cada `CUBO_MIN`.
    Column(modifier = Modifier.fillMaxSize()) {
        // Solo si de verdad hay de las dos: con una sola familia la solapa sería una decisión falsa.
        if (conCatalogo.any { it.isHandheld } && conCatalogo.any { !it.isHandheld }) {
            PlatformFormTabs(handhelds = portatiles, onSelect = { portatiles = it })
        }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnas = maxOf(3, ((maxWidth - 32.dp) / CUBO_MIN).toInt())

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnas),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            generaciones.forEach { grupo ->
                // El encabezado ocupa la fila entera. Sin él el reordenamiento sería invisible: se
                // vería una grilla barajada y nadie sabría por qué la SG-1000 va primera.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GenerationHeader(grupo)
                }
                items(grupo.platforms) { p ->
                    if (p.enabled) {
                        PlatformCube(
                            platform = p,
                            region = region,
                            onClick = { onSelect(p) },
                        )
                    } else {
                        // Bloqueada: sin catálogo aún, no se toca su JSON ni se puede seleccionar.
                        LockedCube(platform = p)
                    }
                }
            }
        }
    }
    }
}

/**
 * Las dos solapas del paso 1: de sobremesa y de bolsillo.
 *
 * El **mismo control segmentado** que el tema, la región y el modelo de voz en Settings, con su
 * `Tokens.Shape.control`. Acá no se navega entre secciones, se filtra la misma grilla — que es
 * exactamente lo que ese control significa en el resto de la app— y reusarlo evita inventar una
 * forma nueva para decir lo mismo.
 */
@Composable
private fun PlatformFormTabs(handhelds: Boolean, onSelect: (Boolean) -> Unit) {
    val opciones = listOf(false to "Consoles", true to "Handhelds")
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        opciones.forEachIndexed { index, (esPortatil, texto) ->
            SegmentedButton(
                selected = handhelds == esPortatil,
                onClick = { onSelect(esPortatil) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index, count = opciones.size, baseShape = Tokens.Shape.control,
                ),
            ) {
                Text(texto)
            }
        }
    }
}

/** Ancho al que un cubo de consola se ve bien: por debajo el glifo queda chico, por encima flota. */
private val CUBO_MIN = 150.dp

/**
 * Opacidad del mando dibujado en cada cubo.
 *
 * En blanco puro competía con el nombre de la consola, que es el dato que se lee para elegir: los
 * colores de identidad son oscuros y desaturados, así que un blanco al 100% sobre ellos es el
 * contraste más alto de toda la pantalla —dieciocho veces— y el glifo terminaba pesando más que el
 * texto. Bajarlo lo devuelve a lo que es: la marca que te dice de qué consola se trata sin que
 * tengas que leer, no el titular del cubo.
 */
private const val GLIFO_ALPHA = 0.68f

/**
 * Encabezado de una región dentro del catálogo de una consola. Mismo idioma que el de generación:
 * ámbar para el rótulo, apagado para el dato de apoyo.
 *
 * Marca dónde termina una lista y empieza la otra. Sin él la transición sería invisible —el título
 * 93 es americano y el 94 japonés— y se leería como que la lista se desordenó sola.
 */
@Composable
private fun RegionSectionHeader(region: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            region.ifBlank { "Sin región" },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$count games",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Encabezado de generación: el mismo idioma que las secciones de Settings (ámbar + apoyo apagado). */
@Composable
private fun GenerationHeader(grupo: PlatformGeneration) {
    val años = when {
        grupo.firstYear == null -> ""
        grupo.firstYear == grupo.lastYear -> "${grupo.firstYear}"
        else -> "${grupo.firstYear}–${grupo.lastYear}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            grupo.generation?.let { "${ordinal(it)} generation" } ?: "Other",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        // El rango de años **explica el orden**: sin él el encabezado nombra el grupo pero no dice
        // por qué va donde va.
        if (años.isNotEmpty()) {
            Text(
                años,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${n}th"
}

/**
 * Cubo de una plataforma soportada: se pinta con **su color de identidad** ([platformBandColor],
 * el mismo de las franjas de estantería), con el logo blanco centrado y el nombre + conteo al pie.
 */
@Composable
private fun PlatformCube(platform: Platform, region: RegionFilter, onClick: () -> Unit) {
    val band = platformBandColor(platform.name) ?: MaterialTheme.colorScheme.surfaceVariant
    CubeSurface(container = band, content = Color.White, onClick = onClick) {
        // Ícono proporcional al cubo (no un tamaño fijo): escala con el ancho disponible y se acota
        // al alto libre para no invadir el nombre. Se ve grande en tablet y bien en teléfono.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val glyphSize = (maxWidth * 0.62f).coerceAtMost(maxHeight)
            PlatformGlyph(
                platform = platform.name,
                size = glyphSize,
                tint = Color.White.copy(alpha = GLIFO_ALPHA),
            )
        }
        // El total de la consola, no el de una región: es lo que se ve al entrar. Viene del config
        // ya calculado — abrir los dieciocho catálogos solo para poner "668 games" congelaba la
        // grilla casi medio segundo. Sin conteo (la PS5) se carga a mano.
        val tag = platform.totalCount().takeIf { it > 0 }?.let { "$it games" } ?: "Add by hand"
        // El mismo rótulo doble que la franja de adentro: si el cubo dice "TurboGrafx-16" y al
        // entrar dice "PC Engine / TurboGrafx-16", el que busca la PC Engine no la encuentra acá.
        CubeCaption(name = platformDisplayName(platform.name), tag = tag, tagColor = Color.White.copy(alpha = 0.7f))
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
        shape = Tokens.Shape.large,
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
    val estilo = MaterialTheme.typography.titleSmall
    // El nombre ocupa **siempre** dos líneas de alto, tenga una o dos, y se apoya abajo.
    //
    // Antes crecía y el glifo, que tiene `weight`, se comía la diferencia: en un teléfono la grilla
    // de tres columnas deja cubos de ~110dp, ahí "Super Nintendo" y "Sega Master System" ocupan dos
    // líneas y "NES" una, y sus glifos terminaban con casi el doble de tamaño unos que otros. La
    // grilla se veía desalineada sin que se pudiera señalar qué estaba mal. Reservando el alto, el
    // hueco del glifo mide igual en los dieciocho cubos; apoyando el texto abajo, un nombre de una
    // línea sigue pegado a su contador y no queda un renglón vacío en el medio.
    // El `+6dp` no es decorativo: un párrafo de dos líneas mide **más** que dos interlineados,
    // porque suma el ascenso de la primera y el descenso de la última. Con la altura exacta,
    // Compose ve que la segunda línea no entra, la descarta y elipsiza — "Sega Master System"
    // quedaba en "Sega Master …" con un renglón vacío arriba.
    val dosLineas = with(LocalDensity.current) {
        (estilo.lineHeight.takeOrElse { estilo.fontSize * 1.4f } * 2).toDp() + 6.dp
    }
    Column(modifier) {
        Box(modifier = Modifier.height(dosLineas), contentAlignment = Alignment.BottomStart) {
            Text(name, style = estilo, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(tag, style = MaterialTheme.typography.bodySmall, color = tagColor)
    }
}

// ----------------------------------------------------------- Paso 2: título

@Composable
private fun TitleStep(
    platform: Platform,
    catalog: GameCatalog,
    marks: List<CatalogMark>,
    onPicked: (platform: Platform, entry: CatalogEntry, coverUrl: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Sin texto tipeado se recorre **la consola entera**, región por región y en orden (NTSC-U,
    // NTSC-J, PAL), con un encabezado por bloque. Con texto se busca sobre todas juntas y el
    // resultado sale por relevancia, sin separar: quien escribe "castlevania" quiere ver los
    // Castlevania, no recorrer tres bloques para encontrarlos.
    val buscando = query.isNotBlank()
    val results = remember(platform, query) {
        if (buscando) catalog.searchAllRegions(platform, query) else catalog.entriesAllRegions(platform)
    }
    // Dónde arranca cada región dentro de la lista, para poner el encabezado justo ahí. Solo tiene
    // sentido navegando: buscando, el orden es el del ranking y las regiones quedan entremezcladas.
    val cortes = remember(results, buscando) {
        if (buscando) emptyMap() else cortesPorRegion(results)
    }
    // Lo ya registrado de esta plataforma, indexado por slug y por título (los juegos viejos del
    // Excel pueden no tener slug). Como `marks` es reactivo, al agregar uno la etiqueta sale sola.
    // Las que bloquean se indexan últimas: si un juego está en la colección y en la wishlist, manda
    // la marca del destino en el que estás.
    val markIndex = remember(marks, platform) { indiceDeMarcas(marks, platform.name) }

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
        itemsIndexed(results) { indice, entry ->
            cortes[indice]?.let { (region, cuantos) ->
                RegionSectionHeader(region = region, count = cuantos)
            }
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
        // Alto fijo, ancho según el aspecto real de la plataforma y región: el ritmo de la lista se
        // mantiene parejo, pero una carátula cuadrada (PC Engine, jewel case) no se recorta dentro
        // de un hueco alto pensado para un cartucho. El 46×62 fijo que había recortaba los lados.
        val alto = 62.dp
        CoverThumb(
            model = entry.coverUrl.ifBlank { CoverArt.primaryUrl(platform, entry.title) },
            contentDescription = null,
            modifier = Modifier.size(width = alto * coverAspectRatio(platform.name, entry.region), height = alto),
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
                        shape = Tokens.Shape.control,
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
                    shape = Tokens.Shape.control,
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
