package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gmoqa.fullset.data.PlatformInfo
import com.gmoqa.fullset.data.RegionFilter

/** Nombre corto de cada key de región usada en `released` (mismas keys que RegionFilter). */
private val REGION_LABELS = linkedMapOf(
    "ntsc-j" to "Japan",
    "ntsc" to "N. America",
    "pal" to "Europe",
)

/**
 * Ficha técnica de una plataforma en una hoja inferior. Diseño en tres bloques: cabecera con la
 * identidad de la consola, año de lanzamiento por región en tarjetas (resaltando [region], la del
 * usuario) y specs de hardware en tiles con icono. Se abre desde la ⓘ de [PlatformBandHeader].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformInfoSheet(
    platform: String,
    info: PlatformInfo,
    region: RegionFilter,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        contentWindowInsets = { WindowInsetsZero },
    ) {
        PlatformInfoContent(
            platform = platform,
            info = info,
            region = region,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 36.dp),
        )
    }
}

/**
 * Cuerpo de la ficha (cabecera + descripción + release por región + hardware). Compartido por el
 * [PlatformInfoSheet] (modal, desde Add game) y por `PlatformScreen` (como header de su vista).
 */
@Composable
fun PlatformInfoContent(
    platform: String,
    info: PlatformInfo,
    region: RegionFilter,
    modifier: Modifier = Modifier,
    /** Cuántos tenés y de cuántos, para mostrar tu avance. Null en el modal, que no sabe de eso. */
    owned: Int? = null,
    total: Int? = null,
    /**
     * Si se muestra lo explicativo: el subtítulo del encabezado y la descripción.
     *
     * En el modal ⓘ de Add game **es el motivo de abrirlo**, así que va. Como encabezado de la vista
     * de consola no: ahí entraste a ver **tus juegos**, y tres líneas sobre qué fue la N64 son algo
     * que ya sabés y que hay que scrollear cada vez.
     */
    showAbout: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header(platform, info, showAbout)

        if (showAbout && info.description.isNotBlank()) {
            Text(
                info.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }

        if (owned != null && total != null && total > 0) Progreso(owned, total)

        if (info.released.isNotEmpty()) ReleaseRow(info, region)

    }
}

// ---------------------------------------------------------------- tu avance

/**
 * Cuánto de esta consola tenés.
 *
 * Va **arriba de todo** porque es el único dato de esta pantalla que es tuyo: el resto —cuándo
 * salió, qué formato usa— es igual para cualquiera. Antes vivía abajo, en letra chica al lado de
 * "Games", detrás de media pantalla de specs.
 *
 * **Sin barra ni porcentaje**: completar la biblioteca de una consola no es una meta realista —son
 * cientos o miles de cartuchos— y tanto la barra casi vacía como el "4.9%" convertían una colección
 * en un fracaso. El número solo informa; los otros dos juzgaban.
 */
@Composable
private fun Progreso(owned: Int, total: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            owned.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            " of $total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

// ---------------------------------------------------------------- cabecera

@Composable
private fun Header(platform: String, info: PlatformInfo, showAbout: Boolean) {
    val band = platformBandColor(platform) ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.Shape.large)
            .background(band)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Ícono del control + nombre de la consola.
            PlatformLabel(
                platform = platform,
                iconSize = 28.dp,
                tint = Color.White,
                nameStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                nameColor = Color.White,
            )
            // El formato va acá y no en un azulejo aparte: es una propiedad de la consola igual
            // que la generación, y una sección "Hardware" con un solo dato adentro se veía huérfana.
            val subtitle = listOfNotNull(
                info.manufacturer.ifBlank { null },
                info.generation?.let { "${it}${ordinal(it)} generation" },
                info.media.ifBlank { null },
            ).joinToString(" · ")
            if (showAbout && subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.Overlay.icon,
                )
            }
        }
    }
}

// ------------------------------------------------- lanzamiento por región

@Composable
private fun ReleaseRow(info: PlatformInfo, region: RegionFilter) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Release")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            REGION_LABELS.forEach { (key, label) ->
                val year = info.released[key]
                ReleaseCard(
                    label = label,
                    year = year,
                    mine = key == region.key && year != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(label: String, year: Int?, mine: Boolean, modifier: Modifier) {
    // La versión de tu región resalta con el color primario; las demás van neutras.
    val bg = if (mine) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val accent = if (mine) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(Tokens.Shape.medium)
            .background(bg)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (mine) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            year?.toString() ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (year == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else accent,
        )
    }
}

// -------------------------------------------------------- specs hardware

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private val WindowInsetsZero = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
