package com.gmoqa.fullset.ui

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/**
 * Ancho por debajo del cual la UI se aprieta (teléfonos angostos): la barra de navegación muestra
 * solo iconos y los botones con texto pasan a ser de icono.
 *
 * Es un **piso**, no una clasificación de dispositivo: un S22 mide 411dp y ya queda por encima. Lo
 * que de verdad cambia con el ancho —cuántas columnas, hasta dónde estirar el texto— se decide con
 * el ancho disponible en cada pantalla, no con este booleano, porque entre 411dp y los 1069dp de una
 * tablet en horizontal hay demasiada diferencia para un solo escalón.
 */
internal const val COMPACT_WIDTH_DP = 400

/**
 * `true` cuando la ventana es angosta (teléfono): la barra de navegación pasa a solo iconos. El ancho
 * de pantalla es una métrica de plataforma, así que va por [expect]/[actual] (Android: `LocalConfiguration`;
 * iOS: `LocalWindowInfo`).
 */
@Composable
expect fun isCompactWidth(): Boolean

/**
 * Header grande de pantalla (estilo Material 3 "large title"): un título prominente con un
 * subtítulo contextual opcional (conteos) y acciones al final. Reemplaza al `TopAppBar` de
 * título simple, que solo repetía la etiqueta del bottom nav. Aplica `statusBarsPadding` (las
 * páginas van a sangre bajo el status bar; este header es el único inset superior).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Acción al principio, para las pantallas que se abren sobre otra (flecha de atrás). */
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // Con flecha, el padding inicial lo pone el propio IconButton: sumar los 20dp dejaría
            // el título corrido respecto de las pantallas que no la tienen.
            .padding(start = if (leading == null) 20.dp else 4.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Carátula que respeta su proporción original (alto = ancho/aspecto), con placeholder de mando
 * mientras carga o si falla. [grayscale] la muestra en blanco y negro (equivalente a
 * `filter: grayscale(100%)` de CSS; se usa en la wishlist). Coil 3: `painter.state` es StateFlow.
 */
@Composable
fun CoverArtImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
) {
    val filter = if (grayscale) {
        remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    } else null
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val state by painter.state.collectAsState()
        if (state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent(modifier = Modifier.fillMaxWidth(), colorFilter = filter)
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}

/** Estado vacío genérico (título + subtítulo centrados). Usado por varias pantallas. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Icono grande en un disco tenue: da un punto focal al vacío sin ser una ilustración.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (action != null) {
                Spacer(Modifier.height(24.dp))
                action()
            }
        }
    }
}

/**
 * Miniatura de carátula de tamaño fijo (lo define [modifier]): fondo con placeholder de mando y,
 * si [model] no es null (File local o URL), la carátula recortada encima.
 */
@Composable
fun CoverThumb(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
) {
    val filter = if (grayscale) {
        remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    } else null
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.SportsEsports,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Botón "Add game" de la cabecera. **Uno solo para las tres secciones** que dan de alta —Collection,
 * Playing y Wishlist—: estaba copiado en cada una, con el comentario duplicado y todo, y así es como
 * terminan divergiendo.
 *
 * En pantalla angosta se queda con el "+": junto al título y la lupa, el texto no entra sin achicar
 * todo lo demás.
 */
@Composable
fun AddGameButton(onClick: () -> Unit, description: String = "Add game", label: String = "Add game") {
    if (isCompactWidth()) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(Icons.Filled.Add, contentDescription = description)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            shape = Tokens.Shape.control,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}
