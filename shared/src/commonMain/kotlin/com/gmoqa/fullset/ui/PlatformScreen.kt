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
import com.gmoqa.fullset.domain.PlatformRow
import com.gmoqa.fullset.domain.completitudDe
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
    // La regla vive en `domain/Completitud.kt`, con sus tests: cruza el catálogo con tu colección,
    // ordena por lanzamiento y cuenta los poseídos **distintos**.
    val completitud = remember(games, catalog) { completitudDe(catalog, games) }
    val rows = completitud.filas
    // El "X de Y" vive arriba, con su barra. Repetirlo acá era decir dos veces lo mismo a diez
    // píxeles de distancia; sin catálogo (PS5…) no hay avance que mostrar y sí el conteo.
    val countLabel = if (catalog.isEmpty()) "${rows.size} games" else "by release"

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
                        // Tu avance en esta consola, arriba de todo: es el único dato de la ficha
                        // que es tuyo. Sin catálogo (la PS5) no hay contra qué medirse y no se
                        // muestra la barra.
                        // Como encabezado no va lo explicativo: entraste a ver tus juegos.
                        showAbout = false,
                        owned = completitud.poseidos,
                        total = rows.size.takeIf { catalog.isNotEmpty() },
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
