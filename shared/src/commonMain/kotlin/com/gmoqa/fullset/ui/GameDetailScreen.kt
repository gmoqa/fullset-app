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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.gmoqa.fullset.data.Condition
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
                onShareText = { shareText(vm.gameNotesText(gameId)) },
                onShareJson = { shareText(vm.gameNotesJson(gameId)) },
                onSetCondition = { vm.setCondition(gameId, it?.key ?: "") },
                onSetFirstPlayed = { vm.setFirstPlayed(gameId, it) },
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
    onShareText: () -> Unit,
    onShareJson: () -> Unit,
    onSetCondition: (Condition?) -> Unit,
    onSetFirstPlayed: (String) -> Unit,
) {
    var shareMenu by remember { mutableStateOf(false) }
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
                modifier = Modifier.matchParentSize().blur(Tokens.Size.heroBlur),
            )
        }
        // Scrim: oscuro arriba (para los iconos) y abajo (para el texto).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Tokens.Overlay.scrimTop,
                        0.45f to Tokens.Overlay.scrimMid,
                        1f to Tokens.Overlay.scrimBottom,
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
                Box {
                    IconButton(onClick = { shareMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share notes", tint = Color.White)
                    }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share as text") },
                            onClick = { shareMenu = false; onShareText() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share as JSON") },
                            onClick = { shareMenu = false; onShareJson() },
                        )
                    }
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
                        // Condición editable: tocá para elegir loose/boxed/complete; el punto de
                        // color se refleja en Collection (la lista es reactiva).
                        EditableConditionChip(game?.conditionState, onSetCondition)
                        // Primera vez que lo jugaste: EL dato de diario. Editable, con precisión
                        // variable (el año alcanza; mes y día si los recordás).
                        FirstPlayedChip(game?.firstPlayed ?: "", onSetFirstPlayed)
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
                        .height(if (compactWidth) Tokens.Size.heroCoverCompact else Tokens.Size.heroCover),
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
                                .background(Tokens.Overlay.placeholder),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = Tokens.Overlay.placeholderIcon,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Chip de condición editable: punto de color + label (o "Condition" si vacío) → menú de opciones. */
@Composable
private fun EditableConditionChip(current: Condition?, onSelect: (Condition?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(Tokens.Shape.pill)
                .background(Tokens.Overlay.chip)
                .clickable { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(current.dot)))
                Spacer(Modifier.width(6.dp))
                Text(current.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
            } else {
                Text("Condition", style = MaterialTheme.typography.labelMedium, color = Tokens.Overlay.textDim)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Set condition", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Condition.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.dot))) },
                    onClick = { open = false; onSelect(c) },
                )
            }
            DropdownMenuItem(
                text = { Text("None") },
                onClick = { open = false; onSelect(null) },
            )
        }
    }
}

/** Chip "First played": cuándo lo jugaste por primera vez (editable; vacío = "First played"). */
@Composable
private fun FirstPlayedChip(current: String, onSet: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(Tokens.Shape.pill)
            .background(Tokens.Overlay.chip)
            .clickable { open = true }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Event,
            contentDescription = "First played",
            tint = Color.White.copy(alpha = if (current.isBlank()) 0.75f else 1f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        if (current.isBlank()) {
            Text("First played", style = MaterialTheme.typography.labelMedium, color = Tokens.Overlay.textDim)
        } else {
            Text(formatReleaseDate(current), style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
        }
    }
    if (open) {
        FirstPlayedDialog(
            initial = current,
            onDismiss = { open = false },
            onSave = { open = false; onSet(it) },
        )
    }
}

/**
 * Diálogo de "primera vez jugado", con precisión variable: el año alcanza; mes y día son opcionales
 * (de un juego de la infancia rara vez se recuerda el día exacto). Guarda ISO parcial
 * ("1994" | "1994-06" | "1994-06-08"); "" = borrar el dato.
 */
@Composable
private fun FirstPlayedDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val parts = initial.split("-")
    var year by remember { mutableStateOf(parts.getOrNull(0)?.toIntOrNull()) }
    var month by remember { mutableStateOf(parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 }) }
    var day by remember { mutableStateOf(parts.getOrNull(2)?.toIntOrNull()) }

    // De la era Pong a hoy: cubre cualquier primera partida posible.
    val thisYear = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year }
    val years = remember(thisYear) { (thisYear downTo 1970).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("First played") },
        text = {
            Column {
                Text(
                    "When did you first play it? Year is enough — add month and day if you remember.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DatePartPicker(
                        label = "Year",
                        value = year?.toString() ?: "—",
                        options = years.map { it.toString() },
                        onPick = { i ->
                            year = years[i]
                            // El día puede quedar inválido al cambiar el año (29 de febrero).
                            if (month != null) day = day?.coerceAtMost(daysInMonth(years[i], month!!))
                        },
                    )
                    DatePartPicker(
                        label = "Month",
                        value = month?.let { MONTH_ABBR[it - 1] } ?: "—",
                        options = listOf("—") + MONTH_ABBR,
                        onPick = { i ->
                            if (i == 0) { month = null; day = null } else {
                                month = i
                                day = day?.coerceAtMost(daysInMonth(year ?: 2000, i))
                            }
                        },
                    )
                    DatePartPicker(
                        label = "Day",
                        value = day?.toString() ?: "—",
                        options = listOf("—") + (1..daysInMonth(year ?: 2000, month ?: 1)).map { it.toString() },
                        onPick = { i -> day = if (i == 0) null else i },
                        // Sin mes no hay día: la precisión crece de a un nivel.
                        enabled = month != null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = year != null,
                onClick = { onSave(partialIso(year!!, month, day)) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = { onSave("") }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Selector de una parte de la fecha: label chico + valor actual → menú de opciones. */
@Composable
private fun DatePartPicker(
    label: String,
    value: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(Tokens.Shape.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = enabled) { open = true }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                options.forEachIndexed { i, opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; onPick(i) })
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
            .clip(Tokens.Shape.pill)
            .background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else Tokens.Overlay.chip
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
            .clip(Tokens.Shape.pill)
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
