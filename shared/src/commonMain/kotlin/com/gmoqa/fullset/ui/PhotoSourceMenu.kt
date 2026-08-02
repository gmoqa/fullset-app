package com.gmoqa.fullset.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Botón para agregar una imagen desde la **cámara o la galería**.
 *
 * Sacar la foto, guardarla y volver a buscarla eran tres pasos para algo que pasa mientras jugás, así
 * que la cámara va primero en el menú. Si la plataforma no puede capturar ([CameraCapture.available]
 * en false) el botón dispara la galería directo, sin menú: un menú de una sola opción es ruido.
 */
@Composable
fun PhotoSourceButton(
    icon: ImageVector,
    description: String,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    cameraAvailable: Boolean,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (cameraAvailable) open = true else onPickFromGallery() }) {
            Icon(icon, contentDescription = description, tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                onClick = { open = false; onTakePhoto() },
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                onClick = { open = false; onPickFromGallery() },
            )
        }
    }
}
