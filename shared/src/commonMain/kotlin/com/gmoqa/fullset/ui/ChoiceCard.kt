package com.gmoqa.fullset.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/**
 * Tarjeta cuadrada para elegir entre pocas opciones equivalentes, una al lado de la otra.
 *
 * Cuadrada y con el mismo peso: ninguna parece la principal por ser más grande, y el ícono hace que
 * la respuesta se lea de un vistazo sin depender del texto. La usan la pregunta del primer arranque
 * y el alta desde Playing, que son la misma decisión con distinto contenido.
 *
 * Va dentro de un [RowScope] porque el ancho lo reparte la fila; el alto sale del `aspectRatio`.
 */
@Composable
fun RowScope.ChoiceCard(
    title: String,
    subtitle: String,
    painter: Painter? = null,
    vector: ImageVector? = null,
    // Último para poder pasarlo como lambda final: `ChoiceCard(...) { ... }`.
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(Tokens.Shape.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Tokens.Shape.large)
            .clickable(onClick = onClick)
            .padding(Tokens.Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconMod = Modifier.size(Tokens.Size.onboardingIcon)
        val tint = MaterialTheme.colorScheme.primary
        when {
            painter != null -> Icon(painter, contentDescription = null, modifier = iconMod, tint = tint)
            vector != null -> Icon(vector, contentDescription = null, modifier = iconMod, tint = tint)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Tokens.Space.xl),
            textAlign = TextAlign.Center,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
