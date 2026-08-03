package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.gmoqa.fullset.data.Game

/**
 * Al compartir una imagen hacia la app desde afuera, pregunta a **cuál de los juegos que estás
 * jugando** adjuntarla.
 *
 * Se limita a los de Playing a propósito: compartir una captura o una foto del televisor es algo
 * que pasa mientras jugás, y ofrecer la colección entera —cientos de juegos— convertiría un gesto
 * de dos toques en una búsqueda. Si no hay ninguno en Playing lo dice y no deja elegir, en vez de
 * mostrar una lista vacía sin explicación.
 *
 * Al elegir, el diálogo **no se cierra solo**: muestra a qué juego quedó adjuntada. Como el gesto
 * arranca fuera de la app, cerrarse sin decir nada dejaba al usuario sin saber si funcionó.
 */
@Composable
fun AttachSharedPhotoDialog(
    playing: List<Game>,
    onPick: (Game) -> Unit,
    onDismiss: () -> Unit,
) {
    var attached by remember { mutableStateOf<Game?>(null) }
    val done = attached
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Tokens.Shape.dialog,
        title = {
            Text(
                when {
                    done != null -> "Photo added"
                    playing.isEmpty() -> "Nothing in Playing"
                    else -> "Attach photo to"
                }
            )
        },
        text = {
            if (done != null) {
                Text("Saved to ${done.name}.", style = MaterialTheme.typography.bodyMedium)
            } else if (playing.isEmpty()) {
                Text(
                    "Mark a game as Playing first, then share the photo again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = Tokens.Size.dialogList),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs),
                ) {
                    items(playing, key = { it.id }) { game ->
                        PlayingRow(game) { onPick(game); attached = game }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (done != null || playing.isEmpty()) "OK" else "Cancel")
            }
        },
    )
}

@Composable
private fun PlayingRow(game: Game, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.Shape.menu)
            .clickable(onClick = onClick)
            .padding(vertical = Tokens.Space.xs, horizontal = Tokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
    ) {
        Box(
            Modifier
                .size(Tokens.Size.pickerThumb)
                .clip(RoundedCornerShape(Tokens.Space.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (game.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = game.coverUrl,
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
                game.platform,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
