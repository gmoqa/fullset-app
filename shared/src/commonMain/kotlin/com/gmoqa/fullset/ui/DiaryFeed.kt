package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gmoqa.fullset.data.Note
import com.gmoqa.fullset.data.Photo
import com.gmoqa.fullset.data.localCoverModel

/**
 * Una entrada del diario del juego. Notas escritas, notas de voz y fotos comparten una única
 * línea de tiempo: separarlas en pestañas partía en dos algo que se lee mejor junto.
 */
internal sealed interface DiaryEntry {
    val key: String
    val createdAt: Long

    data class Written(val note: Note) : DiaryEntry {
        override val key get() = "note-${note.id}"
        override val createdAt get() = note.createdAt
    }

    data class Voice(val note: Note) : DiaryEntry {
        override val key get() = "voice-${note.id}"
        override val createdAt get() = note.createdAt
    }

    data class Snapshot(val photo: Photo) : DiaryEntry {
        override val key get() = "photo-${photo.id}"
        override val createdAt get() = photo.createdAt
    }
}

/** Mezcla notas y fotos en una sola línea de tiempo, lo más nuevo primero. */
internal fun diaryFeed(notes: List<Note>, photos: List<Photo>): List<DiaryEntry> =
    (notes.map { if (it.isVoice) DiaryEntry.Voice(it) else DiaryEntry.Written(it) } +
        photos.map { DiaryEntry.Snapshot(it) })
        .sortedByDescending { it.createdAt }

/**
 * Tarjeta de una entrada. Misma caja para los tres tipos (fecha arriba, acción de borrar a la
 * derecha) para que el diario se lea como una sola cosa y no como tres listas distintas.
 */
@Composable
internal fun DiaryEntryCard(
    entry: DiaryEntry,
    isTranscribing: Boolean,
    canTranscribe: Boolean,
    onDelete: () -> Unit,
    onTranscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDateTime(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Box(modifier = Modifier.padding(end = 12.dp)) {
                when (entry) {
                    is DiaryEntry.Written -> Text(
                        entry.note.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    is DiaryEntry.Voice -> VoiceEntryBody(
                        note = entry.note,
                        isTranscribing = isTranscribing,
                        canTranscribe = canTranscribe,
                        onTranscribe = onTranscribe,
                    )

                    is DiaryEntry.Snapshot -> AsyncImage(
                        model = localCoverModel(entry.photo.path),
                        contentDescription = entry.photo.caption.ifBlank { "Photo" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceEntryBody(
    note: Note,
    isTranscribing: Boolean,
    canTranscribe: Boolean,
    onTranscribe: () -> Unit,
) {
    Column {
        VoiceNotePlayer(path = note.audioPath, durationMs = note.durationMs)
        when {
            note.text.isNotBlank() -> Text(
                note.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp),
            )

            isTranscribing -> Text(
                "Transcribing…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Con modelo instalado se puede reintentar a mano (p. ej. notas viejas).
            canTranscribe -> TextButton(onClick = onTranscribe) { Text("Transcribe") }

            else -> Text(
                "No transcript — download a model in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Única zona para sumar al diario: escribir, grabar o agregar una foto. Reemplaza a los botones
 * flotantes sueltos y a la pestaña de fotos, que dispersaban la misma acción en tres lugares.
 */
@Composable
internal fun DiaryComposer(
    onWrite: () -> Unit,
    onRecord: () -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComposerAction(Icons.Filled.Mic, "Record voice note", onRecord)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onWrite)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "Add to your diary…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ComposerAction(Icons.Filled.AddPhotoAlternate, "Add photo", onPhoto)
        }
    }
}

@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    }
}
