package com.gmoqa.diariogamer.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gmoqa.diariogamer.data.SteamGridGame

/**
 * Alta de un juego **digital** (no lo poseés), desde Playing. A diferencia del alta física, la
 * plataforma es texto libre (no hay grilla ni catálogo): estas consolas modernas se cargan a mano.
 * Va directo a Playing con el badge amarillo; nunca a Collection. Carátula opcional (buscador/galería).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDigitalGameScreen(
    coverSearchEnabled: Boolean,
    onSearchGames: suspend (String) -> List<SteamGridGame>,
    onCoversFor: suspend (Int) -> List<String>,
    onCancel: () -> Unit,
    onAdd: (platform: String, title: String, coverUrl: String, coverUri: Uri?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("PlayStation 5") }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add digital game") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "A digital game isn't part of your collection — it lives here in Playing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = platform,
                onValueChange = { platform = it },
                label = { Text("Platform") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CoverPickerField(
                queryTitle = title,
                aspect = coverAspectRatio(platform),
                searchEnabled = coverSearchEnabled,
                onSearchGames = onSearchGames,
                onCoversFor = onCoversFor,
                onGamePicked = { title = it },
                coverUri = coverUri,
                coverUrl = coverUrl,
                onChange = { url, uri -> coverUrl = url; coverUri = uri },
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onAdd(platform.trim(), title.trim(), coverUrl, coverUri) },
                enabled = title.isNotBlank() && platform.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to Playing")
            }
        }
    }
}
