package com.gmoqa.diariogamer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmoqa.diariogamer.data.Game
import com.gmoqa.diariogamer.data.coverModel
import com.gmoqa.diariogamer.data.PlatformInfo
import com.gmoqa.diariogamer.data.RegionFilter

/**
 * Vista propia de una plataforma: la **ficha técnica como header** ([PlatformInfoContent]) y, debajo,
 * **tus juegos de esa plataforma ordenados por año de lanzamiento** (no alfabético). Reemplaza al
 * modal de ficha cuando entrás desde una franja de Collection.
 */
@Composable
fun PlatformScreen(
    platform: String,
    info: PlatformInfo?,
    region: RegionFilter,
    games: List<Game>,
    onOpenGame: (Long) -> Unit,
    onBack: () -> Unit,
) {
    // Orden cronológico ascendente (más antiguo primero); sin año va al final; desempate por título.
    val sorted = remember(games) {
        games.sortedWith(compareBy<Game>({ it.releaseYear ?: Int.MAX_VALUE }, { it.name.lowercase() }))
    }

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
                        "${sorted.size} · by release",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (sorted.isEmpty()) {
            item {
                Text(
                    "No games from this platform yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            items(sorted, key = { it.id }) { game ->
                PlatformGameRow(game = game, onClick = { onOpenGame(game.id) })
            }
        }
    }
}

/** Fila de juego: carátula + título/subtítulo, con el **año destacado a la derecha** (el criterio de orden). */
@Composable
private fun PlatformGameRow(game: Game, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            model = game.coverModel,
            contentDescription = game.name,
            modifier = Modifier.height(56.dp).aspectRatio(coverAspectRatio(game.platform)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                game.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                game.genre.takeIf { it.isNotBlank() },
                game.publisher.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            game.releaseYear?.toString() ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (game.releaseYear == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
