package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gmoqa.fullset.DiaryViewModel
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.Note
import com.gmoqa.fullset.data.coverModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    vm: DiaryViewModel,
    gameId: Long,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    // Estado reactivo: al togglear/agregar/borrar, los Flows re-emiten y la UI se refresca sola.
    val game by remember(gameId) { vm.gameFlow(gameId) }.collectAsStateWithLifecycle(null)
    val notes by remember(gameId) { vm.notesFlow(gameId) }.collectAsStateWithLifecycle(emptyList())
    val photos by remember(gameId) { vm.photosFlow(gameId) }.collectAsStateWithLifecycle(emptyList())

    // Nota de voz: se graba desde acá (tab Notes) y queda como una nota más del juego.
    val recordingFor by vm.recordingFor.collectAsStateWithLifecycle()
    val recordElapsedMs by vm.recordElapsedMs.collectAsStateWithLifecycle()
    val recordAmplitude by vm.recordAmplitude.collectAsStateWithLifecycle()
    val isRecording = recordingFor == gameId
    // Transcripción: qué notas están corriendo ahora y si hay modelo instalado.
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    val installedModel by vm.installedModel.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddNote by remember { mutableStateOf(false) }
    // Compartir las notas del juego como JSON (para pegarlas en un LLM, guardarlas, etc.).
    val shareText = rememberTextSharer()
    // Nota en edición (texto): notas escritas y de voz. Sirve para corregir una transcripción.
    var editingNote by remember { mutableStateOf<Note?>(null) }

    val startRecording = rememberMicPermission { vm.startVoiceNote(gameId) }

    val pickCover = rememberImagePicker { image -> if (image != null) vm.setCover(gameId, image) }
    val pickPhoto = rememberImagePicker { image -> if (image != null) vm.addPhoto(gameId, image) }

    Scaffold { _ ->
        // El hero va a sangre por arriba (detrás del status bar).
        Column(modifier = Modifier.fillMaxSize()) {
            HeroHeader(
                game = game,
                onBack = onBack,
                onDelete = { showDeleteDialog = true },
                onTogglePlaying = { vm.setPlaying(gameId, game?.playing != true) },
                onToggleBacklog = { vm.setBacklog(gameId, game?.backlog != true) },
                onChangeCover = { pickCover() },
                onResetCover = { vm.clearCustomCover(gameId) },
                onShare = { shareText(vm.gameNotesJson(gameId)) },
            )

            // Notas escritas, de voz y fotos en una sola línea de tiempo.
            val feed = remember(notes, photos) { diaryFeed(notes, photos) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (feed.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.EditNote,
                        title = "Your diary is empty",
                        subtitle = "Write a note, record your voice, or add a photo.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(feed, key = { it.key }) { entry ->
                            DiaryEntryCard(
                                entry = entry,
                                isTranscribing = entry is DiaryEntry.Voice &&
                                    entry.note.id in transcribing,
                                canTranscribe = installedModel != null,
                                onDelete = {
                                    when (entry) {
                                        is DiaryEntry.Snapshot -> vm.deletePhoto(entry.photo.id)
                                        is DiaryEntry.Written -> vm.deleteNote(entry.note.id)
                                        is DiaryEntry.Voice -> vm.deleteNote(entry.note.id)
                                    }
                                },
                                onTranscribe = {
                                    if (entry is DiaryEntry.Voice) {
                                        vm.transcribeNote(entry.note.id, entry.note.audioPath)
                                    }
                                },
                                onEdit = when (entry) {
                                    is DiaryEntry.Written -> ({ editingNote = entry.note })
                                    is DiaryEntry.Voice -> ({ editingNote = entry.note })
                                    is DiaryEntry.Snapshot -> null
                                },
                            )
                        }
                    }
                }
            }

            // Mientras grabás, la barra de grabación ocupa el lugar del compositor: la acción
            // empieza y termina en el mismo sitio.
            if (isRecording) {
                RecordingBar(
                    elapsedMs = recordElapsedMs,
                    amplitude = recordAmplitude,
                    onSave = { vm.stopVoiceNote() },
                    onDiscard = { vm.cancelVoiceNote() },
                )
            } else {
                DiaryComposer(
                    onWrite = { showAddNote = true },
                    onRecord = { startRecording() },
                    onPhoto = { pickPhoto() },
                )
            }
        }
    }

    if (showAddNote) {
        NoteDialog(
            title = "New note",
            onDismiss = { showAddNote = false },
            onConfirm = { text ->
                vm.addNote(gameId, text)
                showAddNote = false
            },
        )
    }
    editingNote?.let { note ->
        NoteDialog(
            title = "Edit note",
            initialText = note.text,
            onDismiss = { editingNote = null },
            onConfirm = { text ->
                vm.editNote(note.id, text)
                editingNote = null
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"${game?.name ?: "this game"}\"?") },
            text = { Text("Its notes and photos will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGame(gameId)
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Header "hero": carátula difuminada de fondo + carátula nítida + título, metadatos y toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroHeader(
    game: Game?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlaying: () -> Unit,
    onToggleBacklog: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
    onShare: () -> Unit,
) {
    val model = game?.coverModel
    val hasCustomCover = game?.coverPath?.isNotBlank() == true
    val compactWidth = isCompactWidth()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(32.dp),
            )
        }
        // Scrim: oscuro arriba (para los iconos) y abajo (para el texto).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.50f),
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    )
                ),
        )

        Column(
            // Sin alto mínimo: el hero mide lo que ocupa su contenido. Forzar 320dp dejaba un
            // hueco muerto entre los iconos y el título cuando el contenido era más chico.
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share notes", tint = Color.White)
                }
                if (hasCustomCover) {
                    IconButton(onClick = onResetCover) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset cover", tint = Color.White)
                    }
                }
                IconButton(onClick = onChangeCover) {
                    Icon(Icons.Filled.Image, contentDescription = "Change cover", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete game", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                // Arriba, no abajo: alineado al fondo, la carátula (más alta que el texto) empujaba
                // el título hacia abajo y dejaba aire muerto bajo los iconos.
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    // El ícono del control + nombre de la plataforma encabeza el bloque, sobre el título.
                    game?.platform?.takeIf { it.isNotBlank() }?.let { plat ->
                        PlatformLabel(
                            platform = plat,
                            iconSize = 20.dp,
                            tint = Color.White,
                            nameStyle = MaterialTheme.typography.titleSmall,
                            nameColor = Color.White,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Text(
                        game?.name ?: "Game",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        game?.releaseYear?.let { MetaChip(it.toString()) }
                        game?.publisher?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                        game?.genre?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                        game?.condition?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                        // Código impreso en el cartucho/disco: identifica la copia física.
                        game?.serial?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                    }
                    // FlowRow: en pantallas angostas los toggles bajan a otra línea en vez de cortarse.
                    FlowRow(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HeroToggle("Playing", Icons.Filled.SportsEsports, game?.playing == true, onTogglePlaying)
                        HeroToggle("Backlog", Icons.Filled.Bookmark, game?.backlog == true, onToggleBacklog)
                    }
                }
                // Carátula con ancho acotado (weight): no aplasta el texto en pantallas angostas
                // cuando es apaisada. La imagen se ajusta (Fit) dentro del hueco, alineada abajo-der.
                // En teléfonos cede más espacio todavía, para que el título no caiga a 3 líneas.
                Box(
                    modifier = Modifier
                        .weight(if (compactWidth) 0.6f else 0.8f)
                        .height(if (compactWidth) 150.dp else 180.dp),
                    // Arriba, para que acompañe al logo y al título: alineada al fondo quedaba
                    // colgando con un hueco encima cuando el ancho es el que la limita.
                    contentAlignment = Alignment.TopEnd,
                ) {
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = game?.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.TopEnd,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(0.72f)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, accent: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.18f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Pill de toggle (Playing/Backlog) estilizado para verse sobre la carátula del hero. */
@Composable
private fun HeroToggle(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun NoteDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialText: String = "",
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What happened in the game?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
