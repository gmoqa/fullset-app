package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.Platform
import com.gmoqa.fullset.data.RegionFilter

/**
 * Las regiones que esta consola tiene **de verdad**, en el orden del enum.
 *
 * Devuelve vacío cuando hay una sola (o ninguna declarada, como las que todavía usan el formato
 * viejo de un único catálogo): ahí no hay nada que elegir y ofrecerlo sería mentir — todas las
 * opciones mostrarían la misma lista, porque `catalogFor` cae al catálogo por defecto.
 */
fun Platform.selectableRegions(): List<RegionFilter> {
    val declared = RegionFilter.entries.filter { it.label in catalogs }
    return if (declared.size > 1) declared else emptyList()
}

/**
 * Chip para cambiar de región mientras agregás un juego: arrancás en la tuya (la de Settings) y
 * podés pasar a otra sin salir del flujo — comprar un import japonés no debería obligar a ir a
 * Settings, cambiar la preferencia global y volver.
 *
 * El cambio es **solo para este alta**: no toca tu región por defecto.
 */
@Composable
fun RegionPicker(
    current: RegionFilter,
    options: List<RegionFilter>,
    onSelect: (RegionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .clip(Tokens.Shape.pill)
                .background(Tokens.Overlay.chip)
                .clickable { open = true }
                .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current.label,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.Overlay.text,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Change region",
                tint = Tokens.Overlay.text,
                modifier = Modifier.size(Tokens.Size.iconSmall),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { region ->
                DropdownMenuItem(
                    text = { Text(region.label) },
                    trailingIcon = {
                        if (region == current) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { open = false; onSelect(region) },
                )
            }
        }
    }
}
