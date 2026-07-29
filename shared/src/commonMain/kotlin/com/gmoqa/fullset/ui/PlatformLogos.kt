package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.resources.Res
import com.gmoqa.fullset.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

/**
 * Metadatos visuales por plataforma, en UN solo lugar (editable acá).
 * - `padIcon`: drawable del **ícono del control** de la consola (Controllercons, SIL OFL 1.1 — ver
 *   Settings → Credits). Reemplazan a los logos de marca (que eran marca registrada). `null` → la
 *   plataforma se muestra solo con su nombre en texto.
 * - `bandColor`: color de la franja del header de estantería.
 */
private data class PlatformStyle(
    val padIcon: DrawableResource?,
    val bandColor: Color,
    val coverAspect: Float,
)

// Los íconos de control viven como recursos de Compose Multiplatform (composeResources/drawable);
// se resuelven por nombre desde el mapa generado, igual para Android e iOS.
@OptIn(ExperimentalResourceApi::class)
private fun pad(name: String): DrawableResource? = Res.allDrawableResources[name]

// `coverAspect` = ancho/alto típico de las carátulas de la plataforma (medido de Libretro; es muy
// consistente dentro de cada una). Reserva un alto estable en las listas para que el tile no salte
// al pasar del placeholder a la imagen cargada, sin bandas negras (el aspecto coincide con el real).
private val PLATFORM_STYLES: Map<String, PlatformStyle> = mapOf(
    "NES" to PlatformStyle(pad("ic_pad_nes"), Color(0xFF472A28), 0.70f),                       // rojo ladrillo
    "Sega Master System" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF3A2530), 0.70f), // granate
    "Super Nintendo" to PlatformStyle(pad("ic_pad_snes"), Color(0xFF302C48), 1.41f),          // índigo
    "Nintendo 64" to PlatformStyle(pad("ic_pad_n64"), Color(0xFF243A2A), 1.40f),              // verde
    "PlayStation" to PlatformStyle(pad("ic_pad_playstation"), Color(0xFF26262E), 1.00f),      // gris
    "Sega Genesis" to PlatformStyle(pad("ic_pad_genesis"), Color(0xFF383840), 0.71f),         // gris
    "Sega CD" to PlatformStyle(pad("ic_pad_genesis"), Color(0xFF1B3A6B), 0.59f),              // azul (usa pad de Genesis)
    "PlayStation 5" to PlatformStyle(pad("ic_pad_playstation5"), Color(0xFF1E2C5C), 0.80f),   // azul marino
    "Sega Saturn" to PlatformStyle(pad("ic_pad_saturn"), Color(0xFF2A2E45), 0.72f),           // slate
    "Dreamcast" to PlatformStyle(pad("ic_pad_dreamcast"), Color(0xFF24384A), 1.00f),          // steel blue (jewel case cuadrada, medido en Libretro = 1.00)
    "PlayStation 2" to PlatformStyle(pad("ic_pad_playstation2"), Color(0xFF1E2038), 0.70f),   // azul oscuro
)

/** Color de la franja del header de cada plataforma (o `null` → color neutro del tema). */
fun platformBandColor(platform: String): Color? = PLATFORM_STYLES[platform]?.bandColor

/**
 * Aspecto (ancho/alto) típico de las carátulas de la plataforma. Se usa para reservar un alto
 * estable en las listas (placeholder e imagen comparten alto → sin salto al cargar). Default
 * vertical para plataformas sin datos.
 */
fun coverAspectRatio(platform: String): Float = PLATFORM_STYLES[platform]?.coverAspect ?: 0.72f

/**
 * Header de estantería: una **franja a todo el ancho** pintada con [platformBandColor], con el ícono
 * del control + el nombre de la plataforma a la izquierda y un **badge contador** ([count]) sutil a
 * la derecha. Reutilizado por Collection/Backlog, Wishlist y Add game. Si [count] es null no se
 * dibuja el badge (p. ej. PS5, que no tiene catálogo que contar).
 *
 * Cuando la franja hace de **encabezado de una vista** (no de una fila dentro de una lista) se le
 * pasa [onBack] —dibuja la flecha de volver **dentro** de la franja— y [windowInsets] con la barra
 * de estado, para que el color se extienda por detrás de ella. En las listas ambos van por defecto.
 */
@Composable
fun PlatformBandHeader(
    platform: String,
    count: Int?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** Motivo de "comienzo": muestra solo la mitad derecha del control, a sangre contra el borde
     *  izquierdo (sin padding), en vez del ícono chico + nombre inline. Se usa en las franjas de
     *  estantería (Collection/Backlog). */
    bleedIcon: Boolean = false,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    val band = platformBandColor(platform) ?: MaterialTheme.colorScheme.surfaceVariant
    val header = onBack != null

    // Franjas de Collection/Backlog: banda de color con el nombre + contador. (Antes llevaba un patrón
    // del ícono de control de fondo; se quitó porque no aportaba.)
    if (bleedIcon) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(band)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .clipToBounds()
                .windowInsetsPadding(windowInsets),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    platform.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (count != null) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(band)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clipToBounds()
            // El color va antes del inset: así pinta también detrás de la barra de estado cuando
            // la franja es el encabezado. La flecha aporta su propia altura, por eso menos padding.
            .windowInsetsPadding(windowInsets)
            .padding(
                start = if (header) 4.dp else 16.dp,
                end = 16.dp,
                top = if (header) 4.dp else 10.dp,
                bottom = if (header) 4.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        PlatformLabel(
            platform = platform,
            iconSize = 22.dp,
            tint = Color.White,
            nameStyle = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Platform info",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Franja navegable (Collection → vista de plataforma): chevron como pista de "entrá acá".
        if (onClick != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Etiqueta de plataforma = **ícono del control + nombre**. El ícono da color/tema; el nombre
 * identifica (un control genérico no dice qué consola es, a diferencia del viejo wordmark). Si la
 * plataforma no tiene ícono, muestra solo el nombre.
 */
@Composable
fun PlatformLabel(
    platform: String,
    iconSize: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.titleSmall,
    nameColor: Color = tint,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PlatformGlyph(platform = platform, size = iconSize, tint = tint, fallback = {})
        if (PLATFORM_STYLES[platform]?.padIcon != null) Spacer(Modifier.width(8.dp))
        Text(
            platform.ifBlank { "Unknown" },
            style = nameStyle,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Solo el **ícono del control** de la plataforma, tintado con [tint]. Si la plataforma no tiene
 * ícono asignado, renderiza [fallback]. Usado por los cubos del paso 1 de Add game (que ya muestran
 * el nombre en su caption) y por [PlatformLabel].
 */
@Composable
fun PlatformGlyph(
    platform: String,
    size: Dp,
    tint: Color,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = PLATFORM_STYLES[platform]?.padIcon
    if (icon == null) {
        fallback()
        return
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = platform,
        tint = tint,
        modifier = modifier.size(size),
    )
}
