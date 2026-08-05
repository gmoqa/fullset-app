package com.gmoqa.fullset.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.resources.Res
import com.gmoqa.fullset.resources.ic_shelf
import org.jetbrains.compose.resources.painterResource

/**
 * Primera apertura: qué querés llevar.
 *
 * Se pregunta una vez y **no se puede equivocar**: la elección solo cambia qué secciones se
 * muestran, nunca qué se guarda, y se cambia después en Settings. Por eso no hay botón de "saltar"
 * ni de "atrás" — cualquiera de las dos respuestas es válida y reversible, así que ofrecer una
 * salida solo agregaría una decisión más.
 *
 * Dos tarjetas cuadradas, con el ícono de la sección que representan: el estante es el mismo de
 * Collection y la libreta el de las notas, así que la respuesta se lee de un vistazo sin tener que
 * leer una descripción.
 */
@Composable
fun OnboardingScreen(onPick: (TrackingMode) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = Tokens.Space.huge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ancho tope: las tarjetas son cuadradas, así que sin límite en una tablet cada una
            // ocupa media pantalla y queda un cuadrado gigante con un ícono chiquito perdido en el
            // medio. Con esto se ven igual en teléfono y no se desbordan en pantalla ancha.
            Column(Modifier.widthIn(max = Tokens.Size.contentMax)) {
                Text(
                    "What do you want to keep?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.giant),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xl),
                ) {
                    ChoiceCard(
                        painter = painterResource(Res.drawable.ic_shelf),
                        title = "Collection",
                        subtitle = "and diary",
                        onClick = { onPick(TrackingMode.COLLECTION_AND_DIARY) },
                    )
                    ChoiceCard(
                        vector = Icons.Filled.EditNote,
                        title = "Diary",
                        subtitle = "only",
                        onClick = { onPick(TrackingMode.DIARY_ONLY) },
                    )
                }
                Text(
                    "Change it anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.xxl),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
