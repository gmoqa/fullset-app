package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.domain.lineaDeTiempo
import com.gmoqa.fullset.data.coverModel

/**
 * Tu historia como jugador: los juegos ordenados por **cuándo los jugaste por primera vez**, no por
 * cuándo salieron ni cuándo los compraste.
 *
 * Es una pantalla aparte y no un modo de Collection porque el alcance es distinto: Collection es la
 * colección **física** y deja afuera los digitales a propósito, pero un juego digital se jugó igual
 * y le corresponde su lugar en la línea de tiempo.
 *
 * Solo entran los que tienen la fecha cargada. La precisión es variable —"1994", "1994-06" o
 * "1994-06-08"— así que se agrupa por año, que es la única parte que todos tienen.
 */
@Composable
fun TimelineScreen(
    games: List<Game>,
    onBack: () -> Unit,
    onOpenGame: (Long) -> Unit,
) {
    // La regla vive en `domain/LineaDeTiempo.kt`, con sus tests.
    val linea = remember(games) { lineaDeTiempo(games) }
    val porAnio = linea.porAnio
    val total = linea.total
    val conMes = linea.conMes

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Timeline",
            subtitle = if (total == 0) "Nothing dated yet"
            else "$total games · ${porAnio.size} years",
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        if (total == 0) {
            EmptyTimeline()
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                porAnio.forEach { (anio, delAnio) ->
                    item(key = "y$anio") { YearHeader(anio, delAnio.size) }
                    items(delAnio, key = { it.id }) { game ->
                        TimelineRow(game, conMes) { onOpenGame(game.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimeline() {
    Box(Modifier.fillMaxSize().padding(Tokens.Space.giant), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No first-played dates yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open a game and set “First played” to place it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.Space.md),
            )
        }
    }
}

@Composable
private fun YearHeader(anio: String, cuantos: Int) {
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.xxl)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.md),
        ) {
            Text(anio, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (cuantos == 1) "1 game" else "$cuantos games",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(top = Tokens.Space.md, start = Tokens.Space.xxl, end = Tokens.Space.xxl))
    }
}

@Composable
private fun TimelineRow(game: Game, conMes: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.Space.xxl, vertical = Tokens.Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xl),
    ) {
        // El día/mes solo se muestra si la fecha lo tiene: inventar "01" donde el usuario escribió
        // apenas el año sería fabricar precisión que nadie confirmó. Y la columna solo se reserva si
        // alguna fila de la lista la va a usar, para que las demás sigan alineadas entre sí.
        if (conMes) {
            Text(
                text = monthDayLabel(game.firstPlayed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(46.dp),
            )
        }
        Box(
            Modifier
                .size(Tokens.Size.pickerThumb)
                .clip(RoundedCornerShape(Tokens.Space.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            game.coverModel?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                game.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (game.digital) "${game.platform} · DIGITAL" else game.platform,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** "Jun 8", "Jun" o "" según lo que traiga la fecha ISO de precisión variable. */
private fun monthDayLabel(iso: String): String {
    if (iso.length < 7) return ""
    val mes = MESES.getOrNull(iso.substring(5, 7).toIntOrNull()?.minus(1) ?: -1) ?: return ""
    if (iso.length < 10) return mes
    return "$mes ${iso.substring(8, 10).trimStart('0')}"
}

private val MESES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
