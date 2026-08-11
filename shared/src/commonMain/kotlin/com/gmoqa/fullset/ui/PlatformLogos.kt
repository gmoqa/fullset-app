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
import androidx.compose.material.icons.filled.SportsEsports
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
    /**
     * Cómo se rotula la consola cuando tuvo **dos nombres según el mercado**, con el japonés
     * primero: es el original —todas salieron antes allá— y además es el que corresponde a la
     * mitad de la lista que de otro modo quedaría mal nombrada.
     *
     * Es **solo el rótulo**. La identidad sigue siendo [name], que es lo que queda guardado en cada
     * juego y la clave de este mapa: renombrarla dejaría huérfanos los juegos ya cargados, sin
     * color ni glifo.
     */
    val displayName: String? = null,
)

// Los íconos de control viven como recursos de Compose Multiplatform (composeResources/drawable);
// se resuelven por nombre desde el mapa generado, igual para Android e iOS.
@OptIn(ExperimentalResourceApi::class)
private fun pad(name: String): DrawableResource? = Res.allDrawableResources[name]

// `coverAspect` = ancho/alto típico de las carátulas de la plataforma (medido de Libretro; es muy
// consistente dentro de cada una). Reserva un alto estable en las listas para que el tile no salte
// al pasar del placeholder a la imagen cargada, sin bandas negras (el aspecto coincide con el real).
private val PLATFORM_STYLES: Map<String, PlatformStyle> = mapOf(
    // El color es el negro cálido de la consola —plástico negro sobre el panel de madera—, y siendo
    // la única de 2ª generación abre la grilla siendo también la más oscura.
    // Aspecto 0.735 medido sobre 38 carátulas al azar, con p10 y p90 los dos en 0.74: rarísimo de
    // parejo, la caja de la 2600 no cambió nunca.
    "Atari 2600" to PlatformStyle(pad("ic_pad_atari2600"), Color(0xFF33261F), 0.74f),          // negro cálido
    "NES" to PlatformStyle(pad("ic_pad_nes"), Color(0xFF472A28), 0.70f, "Famicom / NES"),                       // rojo ladrillo
    "Sega Master System" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF3A2530), 0.70f), // granate
    "Super Nintendo" to PlatformStyle(pad("ic_pad_snes"), Color(0xFF302C48), 1.41f, "Super Famicom / Super Nintendo"),          // índigo
    "Nintendo 64" to PlatformStyle(pad("ic_pad_n64"), Color(0xFF243A2A), 1.40f),              // verde
    // El aspecto es parejo en las tres regiones (medido ~0.71).
    "GameCube" to PlatformStyle(pad("ic_pad_gamecube"), Color(0xFF3B2A55), 0.71f),            // violeta
    "PlayStation" to PlatformStyle(pad("ic_pad_playstation"), Color(0xFF26262E), 1.00f),      // gris
    "Sega Genesis" to PlatformStyle(pad("ic_pad_genesis"), Color(0xFF383840), 0.71f, "Mega Drive / Genesis"),         // gris
    "Sega CD" to PlatformStyle(pad("ic_pad_genesis"), Color(0xFF1B3A6B), 0.68f, "Mega-CD / Sega CD"),              // azul (usa pad de Genesis)
    // Add-ons del Genesis: comparten su control. El 32X usa caja de cartón como el Genesis.
    "Sega 32X" to PlatformStyle(pad("ic_pad_genesis"), Color(0xFF1F3A38), 0.73f, "Super 32X / Sega 32X"),             // verde petróleo
    // Familia 8-bit: comparten el control del Master System.
    "Sega Game Gear" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF3A2A45), 0.71f), // violeta
    "SG-1000" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF453220), 0.74f),        // marrón
    // NEC no tiene pad propio todavía: el de Master System es el mando de dos botones de la misma
    // generación, que es lo que era el de la PC Engine. Aspecto 1.00 medido sobre 40 al azar de
    // cada uno de los cuatro catálogos —HuCard y CD, japonés y americano, todos dan cuadrado—.
    "TurboGrafx-16" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF4A2A1E), 1.00f, "PC Engine / TurboGrafx-16"),  // naranja quemado
    "TurboGrafx-CD" to PlatformStyle(pad("ic_pad_master_system"), Color(0xFF2E3350), 1.00f, "CD-ROM² / TurboGrafx-CD"),  // azul acero
    "PlayStation 5" to PlatformStyle(pad("ic_pad_playstation5"), Color(0xFF1E2C5C), 0.80f),   // azul marino
    // Jewel case alta y angosta (medido en Libretro = 0.59; antes estaba en 0.72 y dejaba banda).
    "Sega Saturn" to PlatformStyle(pad("ic_pad_saturn"), Color(0xFF2A2E45), 0.60f),           // slate
    "Dreamcast" to PlatformStyle(pad("ic_pad_dreamcast"), Color(0xFF24384A), 1.00f),          // steel blue (jewel case cuadrada, medido en Libretro = 1.00)
    "PlayStation 2" to PlatformStyle(pad("ic_pad_playstation2"), Color(0xFF1E2038), 0.71f),   // azul oscuro
    // Sus tapas vienen de SteamGridDB, que las entrega todas en 600×900 = 0.67.
    "PlayStation 3" to PlatformStyle(pad("ic_pad_playstation3"), Color(0xFF1A1A1A), 0.67f),   // negro piano
    // Verde de la pantalla del Game Boy original (#9BBC0F) oscurecido: cae en 74°, el centro del
    // hueco más grande que quedaba en la paleta (29°→136°), y es el color de la familia.
    // Aspecto **por región**: la caja japonesa es apaisada y la americana/europea cuadrada.
    "Game Boy Advance" to PlatformStyle(pad("ic_pad_gba"), Color(0xFF37401A), 1.00f),          // verde DMG
    // Ciruela: la paleta tenía su hueco más grande entre el granate del Master System y el violeta
    // de la Game Gear, y la DS Lite en Noble Pink es un modelo real y reconocible.
    // Aspecto 1.11 medido sobre 40 al azar (p10 1.09, p90 1.12): apaisada como la 3DS.
    "Nintendo DS" to PlatformStyle(pad("ic_pad_ds"), Color(0xFF3E2540), 1.11f),                // ciruela
    // Controllercons no trae DS ni 3DS, así que estos dos glifos los dibujamos nosotros
    // (`tools/draw_3ds_glyph.py`): la consola abierta, que es como se la reconoce.
    // **Aspecto 1.13, apaisado**: es la única del dataset más ancha que alta. La caja de 3DS se
    // guarda acostada. Medido sobre 40 al azar, p10 1.09 y p90 1.14.
    "Nintendo 3DS" to PlatformStyle(pad("ic_pad_3ds"), Color(0xFF17414A), 1.13f),             // aqua blue
)

/**
 * Cómo se rotula la consola. Con los dos nombres si tuvo dos —`Mega Drive / Genesis`—, y si no, el
 * suyo tal cual.
 *
 * Recibe el nombre **guardado** y no un [Platform] porque quien rotula casi siempre tiene solo eso:
 * la franja de una estantería se arma agrupando juegos por su campo `platform`, que es texto.
 */
fun platformDisplayName(platform: String): String =
    PLATFORM_STYLES[platform]?.displayName ?: platform

/** Color de la franja del header de cada plataforma (o `null` → color neutro del tema). */
fun platformBandColor(platform: String): Color? = PLATFORM_STYLES[platform]?.bandColor

/**
 * Aspecto (ancho/alto) de las carátulas **según la región**, porque el mismo juego venía en cajas
 * distintas según el mercado: la Saturn japonesa usaba jewel case cuadrado y la americana una caja
 * alta y angosta. Usar un solo número dejaba la mitad de las carátulas flotando en un hueco.
 *
 * Medido sobre las imágenes de libretro-thumbnails de cada catálogo (mediana de una muestra).
 */
private val COVER_ASPECT_BY_REGION: Map<Pair<String, String>, Float> = mapOf(
    ("Sega Saturn" to "NTSC-U") to 0.60f,   // caja alta
    ("Sega Saturn" to "NTSC-J") to 1.00f,   // jewel case cuadrado
    ("Sega Saturn" to "PAL") to 0.65f,
    // Medido sobre 43 carátulas al azar del catálogo: mediana 0.60, cuartiles 0.59–0.71. El 0.68
    // que había era un promedio entre la caja alta y el jewel case que no le quedaba bien a
    // ninguno: reservaba más ancho del que la caja alta necesita y las dejaba con hueco a los
    // lados. La caja alta es la abrumadora mayoría, así que manda ella.
    ("Sega CD" to "NTSC-U") to 0.60f,
    ("Sega CD" to "NTSC-J") to 1.00f,
    // PAL es genuinamente **bimodal** —cuartiles 0.71 y 1.29 sobre 45 medidas—: en Europa
    // convivieron la caja alta y la ancha. 1.18 es la mediana; ningún número le va a quedar bien a
    // las dos mitades, y no hay dato en el catálogo que distinga una de otra.
    ("Sega CD" to "PAL") to 1.18f,
    // La caja japonesa de GBA es un cartón **apaisado**; la americana y la europea, casi cuadradas.
    // Medido sobre 90 al azar: 35/35 cuadradas en NTSC-U, 31/31 en PAL, 20 de 24 anchas en NTSC-J.
    ("Game Boy Advance" to "NTSC-J") to 1.59f,
    ("Game Boy Advance" to "NTSC-U") to 1.00f,
    ("Game Boy Advance" to "PAL") to 1.00f,
)

/**
 * Aspecto (ancho/alto) típico de las carátulas. Se usa para reservar un alto estable en las listas
 * (placeholder e imagen comparten alto → sin salto al cargar). Con [region] se afina por mercado
 * donde el packaging cambió; sin ella, el valor general de la plataforma.
 */
fun coverAspectRatio(platform: String, region: String = ""): Float =
    COVER_ASPECT_BY_REGION[platform to region]
        ?: PLATFORM_STYLES[platform]?.coverAspect
        ?: 0.72f

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
    /** Control extra a la derecha (p. ej. el selector de región al agregar un juego). */
    trailing: (@Composable () -> Unit)? = null,
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
                    // El rótulo, no la identidad: ver [platformDisplayName]. Este camino dibuja su
                    // propio texto en vez de usar [PlatformLabel] —no lleva ícono— así que hay que
                    // acordarse de los dos.
                    platformDisplayName(platform).ifBlank { "Unknown" },
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
                        color = Tokens.Overlay.textDim,
                        modifier = Modifier
                            .clip(Tokens.Shape.pill)
                            .background(Tokens.Overlay.chipDim)
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Tokens.Overlay.textDim,
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
                color = Tokens.Overlay.textDim,
                modifier = Modifier
                    .clip(Tokens.Shape.pill)
                    .background(Tokens.Overlay.chipDim)
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailing()
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Platform info",
                    tint = Tokens.Overlay.icon,
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
                tint = Tokens.Overlay.textDim,
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
            // El rótulo, no la identidad: las consolas que tuvieron dos nombres se leen con los dos.
            platformDisplayName(platform).ifBlank { "Unknown" },
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
    modifier: Modifier = Modifier,
    /**
     * Qué dibujar cuando esa consola no tiene glifo propio. Por defecto, un mando genérico **con el
     * mismo tinte**.
     *
     * El tinte va acá y no en cada llamada porque exigirlo afuera es pedir que nadie se olvide, y
     * alguien se olvidó: la GameCube caía a este mando —le faltaba el suyo— y el fallback lo pintaba
     * en blanco puro mientras los otros diecisiete iban atenuados. Un solo cubo gritando en la
     * grilla y ninguna forma de notarlo leyendo el sitio del error. Hoy ninguna plataforma con
     * catálogo cae acá, pero el default sigue siendo la red de contención de la próxima.
     */
    fallback: @Composable () -> Unit = {
        Icon(
            Icons.Filled.SportsEsports,
            contentDescription = platform,
            tint = tint,
            modifier = modifier.size(size),
        )
    },
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
