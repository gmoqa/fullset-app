package com.gmoqa.fullset.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.ModelDownloadState
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.data.TranscriptionLanguage
import com.gmoqa.fullset.data.WhisperModel

// Toda la pantalla se arma con tres piezas —[SettingsSection], [SettingsRow] y [SettingsChoice]—
// para que cada opción se vea y se alinee igual. Márgenes compartidos:
private val GUTTER = 20.dp     // margen lateral de todo el contenido
private val ICON_SLOT = 40.dp  // ancho reservado al icono: alinea los textos aunque no haya uno

/**
 * Lo que Settings **muestra**.
 *
 * Estado y acciones van separados a propósito: la pantalla recibía 30 parámetros sueltos, y con esa
 * cantidad la firma dejó de ser un contrato para volverse una lista de compras — agregar un ajuste
 * obligaba a tocar cuatro firmas en cascada. Partido en dos, la pantalla se lee como "esto es lo que
 * muestro, esto es lo que puedo hacer", que es la pregunta que uno se hace al abrir un archivo que
 * no escribió.
 */
data class SettingsUiState(
    val trackingMode: TrackingMode,
    val themeMode: ThemeMode,
    val regionFilter: RegionFilter,
    val showLabels: Boolean,
    val showConsoleTitles: Boolean,
    val deleteAudioAfterTranscription: Boolean,
    val photoCount: Int,
    val syncStatus: String?,
    val installedModel: WhisperModel?,
    val modelDownload: ModelDownloadState,
    val transcriptionLanguage: TranscriptionLanguage,
    val previewEmpty: Boolean = false,
)

/** Lo que Settings **puede hacer**. */
data class SettingsActions(
    val onTrackingModeChange: (TrackingMode) -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onRegionChange: (RegionFilter) -> Unit,
    val onShowLabelsChange: (Boolean) -> Unit,
    val onShowConsoleTitlesChange: (Boolean) -> Unit,
    val onDeleteAudioChange: (Boolean) -> Unit,
    val exportCsv: () -> String,
    val backupJson: () -> String,
    val backupArchive: () -> BackupArchive,
    val onRestore: (RestoredBackup) -> Unit,
    val onClearSyncStatus: () -> Unit,
    val onDownloadModel: (WhisperModel) -> Unit,
    val onCancelModelDownload: () -> Unit,
    val onDeleteModel: (WhisperModel) -> Unit,
    val onDismissModelError: () -> Unit,
    val onLanguageChange: (TranscriptionLanguage) -> Unit,
    /** Buscar catálogos nuevos ahora, salteando el límite diario. */
    val onCheckCatalogs: () -> Unit,
    /** Solo en builds debug: si es null, la sección Developer no se muestra. */
    val onPreviewEmptyChange: ((Boolean) -> Unit)? = null,
)


@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val exportCollection = rememberCollectionExporter(actions.exportCsv)
    val backup = rememberBackupExporter(actions.backupJson)
    val backupAll = rememberArchiveExporter(actions.backupArchive)
    val restore = rememberBackupImporter(actions.onRestore)
    var askBackupScope by remember { mutableStateOf(false) }
    var deleteAudio by remember { mutableStateOf(state.deleteAudioAfterTranscription) }

    // Tope de ancho para **toda** la pantalla, título incluido: son opciones con descripción, y
    // estiradas a lo ancho de una tablet en horizontal quedan renglones de más de cien caracteres,
    // donde el ojo pierde el principio de la línea siguiente. El tope va acá afuera y no solo en el
    // contenido porque si no el título queda contra el borde izquierdo y las opciones en el medio,
    // como si fueran de dos pantallas distintas.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
          // `widthIn` **antes** de `fillMaxHeight`: al revés el tope llega tarde, cuando el ancho
          // ya quedó fijado al del padre.
          modifier = Modifier.widthIn(max = Tokens.Size.readableMax).fillMaxHeight(),
      ) {
        // El header no puede irse con el scroll: es el único que aplica el inset del status
        // bar, así que al subir deja el contenido pasando por debajo del reloj y la batería.
        // Fijo, como en el resto de las pantallas.
        ScreenHeader(title = "Settings", subtitle = "Fullset · v1.0")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection(
                "What you keep",
                "“Diary only” hides Collection and Wishlist. Nothing is deleted — switch back and " +
                    "everything is there again.",
            )
            SettingsChoice(
                label = "Sections",
                options = listOf(
                    TrackingMode.COLLECTION_AND_DIARY to "Collection + diary",
                    TrackingMode.DIARY_ONLY to "Diary only",
                ),
                selected = state.trackingMode,
                onSelect = actions.onTrackingModeChange,
            )
            SectionDivider()

            SettingsSection("Appearance")
            SettingsChoice(
                label = "Theme",
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                ),
                selected = state.themeMode,
                onSelect = actions.onThemeChange,
            )

            SectionDivider()

            SettingsSection("Collection")
            SettingsRow(
                icon = Icons.Filled.Title,
                title = "Show game titles",
                subtitle = "Names under each cover.",
                onClick = { actions.onShowLabelsChange(!state.showLabels) },
                trailing = { Switch(checked = state.showLabels, onCheckedChange = actions.onShowLabelsChange) },
            )
            SettingsRow(
                icon = Icons.Filled.ViewAgenda,
                title = "Show console headers",
                subtitle = "Platform name bands between shelves.",
                onClick = { actions.onShowConsoleTitlesChange(!state.showConsoleTitles) },
                trailing = { Switch(checked = state.showConsoleTitles, onCheckedChange = actions.onShowConsoleTitlesChange) },
            )

            SectionDivider()

            SettingsSection("Library")
            SettingsChoice(
                label = "Default region",
                description = "Which regional list you browse. Not every console has all three — " +
                "the picker in each list shows what it ships with.",
                options = RegionFilter.entries.map { it to it.label },
                selected = state.regionFilter,
                onSelect = actions.onRegionChange,
                isEnabled = { it.supported },
            )
            SettingsRow(
                icon = Icons.Filled.CloudDownload,
                title = "Check for catalog updates",
                subtitle = "Game lists come from the public repo and improve over time. " +
                    "Checked once a day; nothing about your collection is sent.",
                onClick = { actions.onCheckCatalogs() },
            )

            SectionDivider()

            SettingsSection("Voice notes", "Transcribed here — nothing leaves this device.")
            VoiceModels(
                installedModel = state.installedModel,
                download = state.modelDownload,
                onDownload = actions.onDownloadModel,
                onCancel = actions.onCancelModelDownload,
                onDelete = actions.onDeleteModel,
                onDismissError = actions.onDismissModelError,
            )
            SettingsChoice(
                label = "Input language",
                description = "Picking one beats Auto on short notes.",
                options = TranscriptionLanguage.entries.map { it to it.label },
                selected = state.transcriptionLanguage,
                onSelect = actions.onLanguageChange,
            )
            SettingsRow(
                icon = Icons.Filled.MicOff,
                title = "Delete recording after transcribing",
                subtitle = "Keep only the text — frees space and keeps recordings off any sync.",
                onClick = { deleteAudio = !deleteAudio; actions.onDeleteAudioChange(deleteAudio) },
                trailing = {
                    Switch(
                        checked = deleteAudio,
                        onCheckedChange = { deleteAudio = it; actions.onDeleteAudioChange(it) },
                    )
                },
            )

            SectionDivider()

            SettingsSection("Data")
            SettingsRow(
                icon = Icons.Filled.FileDownload,
                title = "Export collection",
                subtitle = "Save as CSV.",
                onClick = { exportCollection() },
            )
            SettingsRow(
                icon = Icons.Filled.Backup,
                title = "Back up to a file",
                subtitle = if (state.photoCount > 0) "Your lists and notes, with or without photos."
                else "Save your lists + notes as a .json to restore later.",
                // Sin fotos que respaldar no hay nada que preguntar: el JSON ES el respaldo completo.
                onClick = { if (state.photoCount > 0) askBackupScope = true else backup() },
            )
            SettingsRow(
                icon = Icons.Filled.Restore,
                title = "Restore from a file",
                subtitle = "Merge a backup (.json or .zip) into your collection — never deletes.",
                onClick = { restore() },
            )
            if (state.syncStatus != null) {
                SettingsRow(
                    title = state.syncStatus,
                    onClick = actions.onClearSyncStatus,
                    trailing = { TextButton(onClick = actions.onClearSyncStatus) { Text("OK") } },
                )
            }

            if (actions.onPreviewEmptyChange != null) {
                SectionDivider()
                SettingsSection("Developer")
                SettingsRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Preview empty state",
                    subtitle = "Temporarily hide all games to check the empty screens.",
                    onClick = { actions.onPreviewEmptyChange(!state.previewEmpty) },
                    trailing = {
                        Switch(checked = state.previewEmpty, onCheckedChange = actions.onPreviewEmptyChange)
                    },
                )
            }

            SectionDivider()

            // Los créditos van al pie, de corrido y en gris: son atribución, no una opción más.
            Text(
                "Covers from Libretro Thumbnails · lists from Wikipedia and No-Intro · controller icons " +
                    "from Controllercons by Kieran McClung (SIL OFL 1.1) · speech to text by Whisper via " +
                    "whisper.cpp (MIT). Box art and titles belong to their owners. Personal use.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = GUTTER, vertical = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }

    if (askBackupScope) {
        BackupScopeDialog(
            photoCount = state.photoCount,
            onDismiss = { askBackupScope = false },
            onDataOnly = { askBackupScope = false; backup() },
            onEverything = { askBackupScope = false; backupAll() },
        )
    }
}

/**
 * Qué incluir en el respaldo. Existe porque los dos pesan órdenes de magnitud distintos: el de datos
 * son unos KB y conviene hacerlo seguido; el completo son cientos de MB y es para cada tanto. El ZIP
 * lleva **el mismo JSON** más las fotos, así que restaurar cualquiera de los dos es el mismo camino.
 */
@Composable
private fun BackupScopeDialog(
    photoCount: Int,
    onDismiss: () -> Unit,
    onDataOnly: () -> Unit,
    onEverything: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What to back up") },
        text = {
            Column {
                Text(
                    "Your lists, notes and dates are just a few KB. Photos are the heavy part.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                BackupChoice(
                    title = "Data only",
                    detail = "Lists, notes, dates and condition — a small .json",
                    onClick = onDataOnly,
                )
                Spacer(Modifier.height(8.dp))
                BackupChoice(
                    title = "Everything",
                    detail = "The same data plus your $photoCount " +
                        (if (photoCount == 1) "photo" else "photos") + " — a .zip",
                    onClick = onEverything,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BackupChoice(title: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Tokens.Shape.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ------------------------------------------------------------- Piezas base

/** Encabezado de sección, con su descripción siempre en el mismo lugar: debajo del título. */
@Composable
private fun SettingsSection(title: String, description: String? = null) {
    Column(modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 16.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Fila estándar: icono opcional, título + subtítulo, y a la derecha su control.
 * El hueco del icono se reserva siempre, así los textos quedan alineados entre filas.
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = GUTTER, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.width(ICON_SLOT), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * Opción de elección única (control segmentado). Los tres selectores de la pantalla —tema,
 * región e idioma— usan este mismo componente para verse idénticos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsChoice(
    label: String,
    options: List<Pair<T, String>>,
    // Admite null: en el modelo de voz puede no haber ninguno elegido todavía.
    selected: T?,
    onSelect: (T) -> Unit,
    description: String? = null,
    isEnabled: (T) -> Boolean = { true },
) {
    Column(modifier = Modifier.padding(horizontal = GUTTER, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    enabled = isEnabled(value),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index, count = options.size, baseShape = Tokens.Shape.control,
                    ),
                ) {
                    Text(text)
                }
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
}

// --------------------------------------------------- Modelo de transcripción

/**
 * El modelo se elige con el mismo control segmentado que el tema y la región: tocar uno que no
 * está bajado lo descarga. Debajo, una única fila de estado con la acción que corresponda.
 */
@Composable
private fun VoiceModels(
    installedModel: WhisperModel?,
    download: ModelDownloadState,
    onDownload: (WhisperModel) -> Unit,
    onCancel: () -> Unit,
    onDelete: (WhisperModel) -> Unit,
    onDismissError: () -> Unit,
) {
    val busy = download is ModelDownloadState.Downloading

    SettingsChoice(
        label = "Model",
        description = "Base is faster; Small is more accurate.",
        options = WhisperModel.entries.map { it to it.label },
        selected = if (busy) (download as ModelDownloadState.Downloading).model else installedModel,
        onSelect = { model -> if (model != installedModel) onDownload(model) },
        isEnabled = { !busy },
    )

    when (download) {
        is ModelDownloadState.Downloading -> {
            SettingsRow(
                icon = Icons.Filled.CloudDownload,
                title = download.model.label,
                // Al llegar al 100% todavía queda comprobar el checksum del archivo.
                subtitle = if (download.progress >= 1f) "Verifying…"
                else "${(download.progress * 100).toInt()}% of ${download.model.sizeLabel}",
                trailing = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GUTTER + ICON_SLOT, end = GUTTER, bottom = 8.dp),
            )
        }

        is ModelDownloadState.Failed -> SettingsRow(
            icon = Icons.Filled.CloudDownload,
            title = "Download failed",
            subtitle = download.message,
            trailing = { TextButton(onClick = onDismissError) { Text("Dismiss") } },
        )

        ModelDownloadState.Idle -> if (installedModel != null) {
            SettingsRow(
                icon = Icons.Filled.CheckCircle,
                title = "${installedModel.label} ready",
                subtitle = "Installed · ${installedModel.sizeLabel}",
                trailing = { TextButton(onClick = { onDelete(installedModel) }) { Text("Remove") } },
            )
        } else {
            SettingsRow(
                icon = Icons.Filled.CloudDownload,
                title = "No model yet",
                subtitle = "Pick one above to download it.",
            )
        }
    }
}
