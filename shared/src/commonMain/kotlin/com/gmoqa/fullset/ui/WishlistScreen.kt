package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gmoqa.fullset.data.WishlistItem

@Composable
fun WishlistScreen(
    items: List<WishlistItem>,
    onAddWishlist: () -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var menuAbierto by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Wishlist",
                subtitle = if (items.isEmpty()) null else "${items.size} wanted",
                trailing = {
                    // "Clear" **borra la lista entera** y estaba justo donde Collection y Playing
                    // ponen "Add game": venías de otra pestaña con la memoria muscular puesta ahí y
                    // el toque de agregar te vaciaba la wishlist. Ahora el alta está en su lugar de
                    // siempre y lo destructivo queda un toque más adentro, en el menú.
                    if (items.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuAbierto = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                                DropdownMenuItem(
                                    text = { Text("Clear wishlist") },
                                    onClick = { menuAbierto = false; showClearDialog = true },
                                )
                            }
                        }
                    }
                    AddGameButton(onClick = onAddWishlist, description = "Add to wishlist")
                },
            )

            if (items.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.FavoriteBorder,
                    title = "Your wishlist is empty",
                    subtitle = "Tap + to add games you want to get.",
                )
            } else {
                // Mismas estanterías por plataforma que Collection, pero con las carátulas en B/N
                // (aún no las tienes). El header usa el logo mono como en Collection.
                val shelves = items.groupBy { it.platform }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shelves.forEach { (platform, list) ->
                        item(key = "header::$platform") {
                            PlatformBandHeader(
                                platform = platform,
                                count = list.size,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        item(key = "row::$platform") {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(list, key = { it.id }) { item ->
                                    WishlistTile(
                                        item = item,
                                        modifier = Modifier.width(Tokens.Size.coverTile),
                                        onRemove = { onRemove(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear wishlist?") },
            text = { Text("This removes all wishlisted games.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WishlistTile(
    item: WishlistItem,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
) {
    Column(modifier = modifier) {
        Box {
            CoverArtImage(
                model = item.coverUrl.ifBlank { null },
                contentDescription = item.game,
                grayscale = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Botón quitar (×) sobre la esquina de la carátula.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from wishlist",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Dos líneas siempre: la fila mide lo que su tile más alto, y un título largo la agrandaba.
        Text(
            item.game,
            style = MaterialTheme.typography.titleSmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}
