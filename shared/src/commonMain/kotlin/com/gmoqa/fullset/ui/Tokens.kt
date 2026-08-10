package com.gmoqa.fullset.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * **Los estilos de la app, en un solo lugar.** Es el equivalente a las variables de un archivo SASS:
 * cambiás un valor acá y se propaga a toda la UI, sin ir archivo por archivo.
 *
 * Lo que va acá son los **tokens compartidos** —espaciados, formas, capas sobre carátula, tamaños—,
 * no cada medida puntual: un padding que existe una sola vez en una pantalla se queda ahí, porque
 * darle nombre global no aclara nada. La regla práctica: si un valor se repite o expresa una
 * decisión de diseño ("el alto de un tile", "cuánto se oscurece una carátula"), va acá.
 *
 * Los colores de marca (primary/secondary) viven en [Theme.kt] porque el tema de Material los
 * necesita en su forma de `ColorScheme`; los de cada consola en `PlatformLogos.kt`, porque son
 * datos de la plataforma más que estilo de la app.
 */
object Tokens {

    /**
     * Escala de espaciado. Todo múltiplo de 2 y creciendo suave: alcanzan para el 90% de los
     * paddings y separaciones sin inventar números sueltos.
     */
    object Space {
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 6.dp
        val md = 8.dp
        val lg = 10.dp
        val xl = 12.dp
        val xxl = 16.dp
        val xxxl = 20.dp
        val huge = 24.dp
        val giant = 32.dp
    }

    /**
     * Escala de esquinas, **única para toda la app**: la usan nuestros componentes y también los de
     * Material, porque `Theme.kt` se la pasa al `MaterialTheme` (ver `AppShapes`). Sin eso, Material
     * aplicaba sus defaults —4dp en los menús desplegables— y convivían dos redondeos distintos en
     * la misma pantalla.
     *
     * [pill] es la cápsula de chips y toggles; el resto crece de a poco hasta [dialog].
     */
    object Shape {
        val pill = RoundedCornerShape(50)
        /**
         * Controles que se tocan: botones y segmentados. Material 3 los hace **pastilla completa**
         * por defecto y ese redondeo no sale de [AppShapes], así que hay que pasarlo a mano en cada
         * uno. Los chips y badges siguen siendo [pill]: son etiquetas, no controles, y ahí la
         * pastilla lee como "dato" en vez de "tocá acá".
         */
        val control = RoundedCornerShape(12.dp)
        val small = RoundedCornerShape(8.dp)
        /** Menús desplegables y superficies flotantes chicas. */
        val menu = RoundedCornerShape(12.dp)
        val medium = RoundedCornerShape(14.dp)
        val large = RoundedCornerShape(18.dp)
        val xlarge = RoundedCornerShape(20.dp)
        /** Diálogos: el redondeo grande de Material 3, que ya se veía bien. */
        val dialog = RoundedCornerShape(28.dp)
    }

    /**
     * Capas sobre una carátula. Son blancos y negros con alpha en vez de colores del tema porque van
     * **encima de una imagen**, no del fondo de la app: ahí el contraste lo da la propia carátula y
     * el color del tema se perdería. Por eso no cambian entre claro y oscuro.
     */
    object Overlay {
        /** Texto principal sobre carátula (título, nombre de consola). */
        val text = Color.White
        /** Texto secundario: metadatos, contadores, placeholders. */
        val textDim = Color.White.copy(alpha = 0.75f)
        /** Íconos de acción sobre la carátula. */
        val icon = Color.White.copy(alpha = 0.85f)
        /** Fondo de un chip o pill sobre la carátula. */
        val chip = Color.White.copy(alpha = 0.18f)
        /** Igual pero más tenue: badges de conteo, toggles apagados. */
        val chipDim = Color.White.copy(alpha = 0.14f)
        /** Hueco donde iría una carátula que no cargó. */
        val placeholder = Color.White.copy(alpha = 0.10f)
        /** El ícono de control que se dibuja dentro de ese hueco. */
        val placeholderIcon = Color.White.copy(alpha = 0.5f)

        /** Velo del hero: oscurece arriba para los íconos y abajo para el texto. */
        val scrimTop = Color.Black.copy(alpha = 0.50f)
        val scrimMid = Color.Black.copy(alpha = 0.30f)
        val scrimBottom = Color.Black.copy(alpha = 0.88f)
    }

    /** Medidas de la estantería y de los íconos recurrentes. */
    object Size {
        /** Ancho del tile de carátula; en teléfonos angostos entra uno menos, por eso el compacto. */
        val coverTile = 140.dp
        val coverTileCompact = 120.dp
        /** Alto de la carátula del hero en el detalle. */
        val heroCover = 180.dp
        /**
         * En pantalla ancha la tapa tiene sitio de sobra —283dp en la tablet— pero el alto la
         * ataba a 180dp, y como la imagen se ajusta, el alto es el que manda: usaba dos tercios
         * del hueco que tenía.
         */
        val heroCoverWide = 260.dp
        val heroCoverCompact = 150.dp
        /** Ícono chico dentro de un chip o botón. */
        val iconSmall = 18.dp
        /** Ícono de acción en headers y barras. */
        val icon = 22.dp
        /** Desenfoque del fondo del hero. */
        val heroBlur = 32.dp
        /** Miniatura de carátula en una lista de selección (elegir juego al compartir una foto). */
        val pickerThumb = 40.dp
        /** Alto máximo de una lista dentro de un diálogo, para que el botón no quede fuera. */
        val dialogList = 320.dp
        /** Ícono grande de una tarjeta de elección (la pregunta del primer arranque). */
        val choiceIcon = 44.dp
        /** Ancho tope de un bloque centrado, para que no se estire en tablet. */
        val contentMax = 420.dp

        /**
         * Ancho máximo de una columna de **texto corrido**. Más que esto y el renglón se pasa de los
         * ~75 caracteres donde el ojo todavía encuentra el principio de la línea siguiente. Es más
         * generoso que [contentMax] porque acá hay descripciones, no controles sueltos.
         */
        val readableMax = 640.dp
    }
}
