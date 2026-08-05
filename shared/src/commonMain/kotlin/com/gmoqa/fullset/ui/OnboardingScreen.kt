package com.gmoqa.fullset.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.TrackingMode

/**
 * Primera apertura: qué querés llevar.
 *
 * Se pregunta una vez y **no se puede equivocar**: la elección solo cambia qué secciones se
 * muestran, nunca qué se guarda, y se cambia después en Settings. Por eso no hay botón de "saltar"
 * ni de "atrás" — cualquiera de las dos respuestas es válida y reversible, así que ofrecer una
 * salida solo agregaría una decisión más.
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
        ) {
            Text(
                "What do you want to keep?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "You can change this later in Settings — nothing is deleted either way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.Space.md, bottom = Tokens.Space.giant),
            )
            ModeCard(
                title = "Collection and diary",
                body = "Shelves by console with what you own, plus a wishlist — and notes, " +
                    "photos and voice memos on every game.",
                onClick = { onPick(TrackingMode.COLLECTION_AND_DIARY) },
            )
            ModeCard(
                title = "Diary only",
                body = "Just what you're playing and what's next, with notes, photos and voice " +
                    "memos. No shelves, no wishlist.",
                onClick = { onPick(TrackingMode.DIARY_ONLY) },
                modifier = Modifier.padding(top = Tokens.Space.xl),
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Space.xxl))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(Tokens.Space.xxl),
            )
            .clickable(onClick = onClick)
            .padding(Tokens.Space.xxl),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Tokens.Space.sm),
        )
    }
}
