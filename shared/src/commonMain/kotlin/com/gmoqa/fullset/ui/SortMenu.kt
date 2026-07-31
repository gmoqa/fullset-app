package com.gmoqa.fullset.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
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
import com.gmoqa.fullset.data.SortOrder

/**
 * Botón de orden para las listas agrupadas por consola (Collection, Backlog): abre un menú con los
 * criterios disponibles y marca el activo. El orden se aplica **dentro de cada estante**, así que la
 * colección se sigue viendo por consola.
 */
@Composable
fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort: ${current.label}",
                // Resaltado cuando NO es el orden por defecto, para que se note que hay uno activo.
                tint = if (current == SortOrder.DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    trailingIcon = {
                        if (order == current) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { open = false; onSelect(order) },
                )
            }
        }
    }
}
