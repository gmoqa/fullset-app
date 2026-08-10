package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.coverModel
import com.gmoqa.fullset.data.PlatformInfo
import com.gmoqa.fullset.data.RegionFilter

/**
 * Vista propia de una plataforma: la **ficha técnica como header** ([PlatformInfoContent]) y, debajo,
 * el **catálogo completo de la consola ordenado por año de lanzamiento** (no alfabético), marcando
 * cuáles tenés (a color, con ✓) y cuáles no (en gris). Reemplaza al modal de ficha cuando entrás
 * desde una franja de Collection. Si la plataforma no trae catálogo (PS5…), lista solo tus juegos.
 */
@Composable
fun PlatformScreen(
    platform: String,
    info: PlatformInfo?,
    region: RegionFilter,
    games: List<Game>,
    onOpenGame: (Long) -> Unit,
    onBack: () -> Unit,
    catalog: List<CatalogEntry> = emptyList(),
    onAddGame: (CatalogEntry) -> Unit = {},
) {
    // Fusiona el catálogo con tu colección: cada entrada del catálogo sabe si la tenés (match por
    // slug); tus juegos que no están en el catálogo (altas a mano, slug que no matchea) se agregan
    // igual. Orden cronológico ascendente; sin año va al final; desempate por título.
    val rows = remember(games, catalog) {
        val ownedBySlug = games.filter { it.slug.isNotBlank() }.associateBy { it.slug }
        val fromCatalog = catalog.map { e ->
            val owned = ownedBySlug[e.slug]
            PlatformRow(
                key = "cat:${e.slug.ifBlank { e.title }}",
                title = owned?.name ?: e.title,
                year = e.year ?: owned?.releaseYear,
                releaseDate = e.releaseDate,
                subtitle = listOfNotNull(e.genre.ifBlank { null }, e.publisher.ifBlank { null })
                    .joinToString(" · "),
                coverModel = owned?.coverModel ?: e.coverUrl.ifBlank { null },
                ownedId = owned?.id,
                entry = e,
            )
        }
        val catalogSlugs = catalog.mapNotNull { it.slug.ifBlank { null } }.toSet()
        val extras = games.filter { it.slug.isBlank() || it.slug !in catalogSlugs }.map { g ->
            PlatformRow(
                key = "own:${g.id}",
                title = g.name,
                year = g.releaseYear,
                subtitle = listOfNotNull(g.genre.ifBlank { null }, g.publisher.ifBlank { null })
                    .joinToString(" · "),
                coverModel = g.coverModel,
                ownedId = g.id,
            )
        }
        // Orden cronológico real por la fecha precisa (ISO ordena cronológicamente); sin fecha usa el
        // año; sin nada va al final. Así "1991-06" queda antes que "1991-12" dentro del mismo año.
        (fromCatalog + extras)
            .sortedWith(compareBy({ it.releaseDate.ifBlank { it.year?.toString() ?: "9999" } }, { it.title.lowercase() }))
    }
    // Por juego **distinto**, no por fila: al juntar las regiones, una misma copia matchea por slug
    // en la lista americana y en la japonesa, y contando filas "3 of 381" pasaba a decir "6 of 381"
    // sin que hubieras agregado nada.
    val ownedCount = rows.mapNotNull { it.ownedId }.distinct().size
    // Con catálogo: "X de Y" (completitud). Sin catálogo (PS5…): solo el conteo de los que tenés.
    val countLabel = if (catalog.isEmpty()) "${rows.size} · by release"
    else "$ownedCount of ${rows.size} · by release"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                if (info != null) {
                    PlatformInfoContent(
                        platform = platform,
                        info = info,
                        region = region,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                    )
                } else {
                    // Plataforma sin ficha: solo la franja como encabezado.
                    PlatformBandHeader(platform = platform, count = null)
                }
                // Encabezado de la sección de juegos.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        "Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    "No games from this platform yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            items(rows, key = { it.key }) { row ->
                PlatformGameRow(
                    region = region,
                    row = row,
                    platform = platform,
                    onOpenGame = onOpenGame,
                    onAddGame = onAddGame,
                )
            }
        }
    }
}

/** Una fila del catálogo de la consola, ya resuelta a "poseído o no". */
private data class PlatformRow(
    val key: String,
    val title: String,
    val year: Int?,
    /** Fecha ISO de precisión variable del catálogo ("" si el juego no está en el catálogo). */
    val releaseDate: String = "",
    val subtitle: String,
    val coverModel: Any?,
    /** Non-null → lo tenés (abre su detalle). Null → falta (en gris, con botón para agregar). */
    val ownedId: Long?,
    /** La entrada del catálogo, para poder agregarla si falta. Null en juegos fuera del catálogo. */
    val entry: CatalogEntry? = null,
)

/**
 * Fila de juego: carátula + título/subtítulo, con el **año destacado a la derecha** (el criterio de
 * orden). Los que tenés van a color con un ✓ en la carátula y son clickeables; los que faltan van en
 * gris (grayscale) y atenuados, sin acción.
 */
@Composable
private fun PlatformGameRow(
    row: PlatformRow,
    platform: String,
    /** Para reservar el alto correcto: el packaging cambiaba según el mercado. */
    region: RegionFilter,
    onOpenGame: (Long) -> Unit,
    onAddGame: (CatalogEntry) -> Unit,
) {
    val owned = row.ownedId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (owned) Modifier.clickable { onOpenGame(row.ownedId!!) } else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoverThumb(
                model = row.coverModel,
                contentDescription = row.title,
                grayscale = !owned,
                // El aspecto sale de la región **de esta fila**, no de la global: la lista mezcla
                // las regiones de la consola y en algunas la caja cambió de forma —la Sega CD
                // japonesa es un jewel case cuadrado y la americana una caja alta—. Con un solo
                // número, media lista queda con banda a los costados. Sin entrada de catálogo (un
                // alta a mano) se usa la región que tenés puesta, que es el mejor dato disponible.
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(coverAspectRatio(platform, row.entry?.region ?: region.label)),
            )
            if (owned) {
                // ✓ en la esquina para los que tenés (disco de color con halo para leerse siempre).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "In your collection",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (owned) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatReleaseDate(row.releaseDate, row.year),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            color = when {
                row.releaseDate.isBlank() && row.year == null ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                owned -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 12.dp).widthIn(max = 104.dp),
        )
        // Los que faltan se agregan directo a la colección desde acá; al agregarlo, la fila pasa
        // sola a "poseído" (✓) porque la lista es reactiva.
        if (!owned && row.entry != null) {
            IconButton(onClick = { onAddGame(row.entry) }) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "Add to collection",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
