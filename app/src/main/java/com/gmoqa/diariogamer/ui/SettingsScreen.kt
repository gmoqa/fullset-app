package com.gmoqa.diariogamer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gmoqa.diariogamer.ModelDownloadState
import com.gmoqa.diariogamer.data.RegionFilter
import com.gmoqa.diariogamer.data.ThemeMode
import com.gmoqa.diariogamer.data.TranscriptionLanguage
import com.gmoqa.diariogamer.data.WhisperModel

// Toda la pantalla se arma con tres piezas —[SettingsSection], [SettingsRow] y [SettingsChoice]—
// para que cada opción se vea y se alinee igual. Márgenes compartidos:
private val GUTTER = 20.dp     // margen lateral de todo el contenido
private val ICON_SLOT = 40.dp  // ancho reservado al icono: alinea los textos aunque no haya uno

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    regionFilter: RegionFilter,
    onRegionChange: (RegionFilter) -> Unit,
    exportCsv: () -> String,
    installedModel: WhisperModel?,
    modelDownload: ModelDownloadState,
    onDownloadModel: (WhisperModel) -> Unit,
    onCancelModelDownload: () -> Unit,
    onDeleteModel: (WhisperModel) -> Unit,
    onDismissModelError: () -> Unit,
    transcriptionLanguage: TranscriptionLanguage,
    onLanguageChange: (TranscriptionLanguage) -> Unit,
    previewEmpty: Boolean = false,
    // Solo en builds debug: si es null, la sección Developer no se muestra.
    onPreviewEmptyChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    // "Guardar como" del sistema (SAF): el usuario elige carpeta/nombre; escribimos el CSV ahí.
    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportCsv().toByteArray(Charsets.UTF_8))
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Collection exported" else "Export failed",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Settings", subtitle = "fullset · v1.0")

        SettingsSection("Appearance")
        SettingsChoice(
            label = "Theme",
            options = listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
            ),
            selected = themeMode,
            onSelect = onThemeChange,
        )

        SectionDivider()

        SettingsSection("Library")
        SettingsChoice(
            label = "Default region",
            description = "NTSC-U only for now.",
            options = RegionFilter.entries.map { it to it.label },
            selected = regionFilter,
            onSelect = onRegionChange,
            isEnabled = { it.supported },
        )

        SectionDivider()

        SettingsSection("Voice notes", "Transcribed here — nothing leaves this device.")
        VoiceModels(
            installedModel = installedModel,
            download = modelDownload,
            onDownload = onDownloadModel,
            onCancel = onCancelModelDownload,
            onDelete = onDeleteModel,
            onDismissError = onDismissModelError,
        )
        SettingsChoice(
            label = "Input language",
            description = "Picking one beats Auto on short notes.",
            options = TranscriptionLanguage.entries.map { it to it.label },
            selected = transcriptionLanguage,
            onSelect = onLanguageChange,
        )

        SectionDivider()

        SettingsSection("Data")
        SettingsRow(
            icon = Icons.Filled.FileDownload,
            title = "Export collection",
            subtitle = "Save as CSV.",
            onClick = { csvSaver.launch("fullset-collection.csv") },
        )

        if (onPreviewEmptyChange != null) {
            SectionDivider()
            SettingsSection("Developer")
            SettingsRow(
                icon = Icons.Filled.VisibilityOff,
                title = "Preview empty state",
                subtitle = "Temporarily hide all games to check the empty screens.",
                onClick = { onPreviewEmptyChange(!previewEmpty) },
                trailing = {
                    Switch(checked = previewEmpty, onCheckedChange = onPreviewEmptyChange)
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
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
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
