package com.gmoqa.fullset.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// UI de las notas de voz: grabación en curso y reproducción de una nota ya guardada.
// Vive aparte del detalle del juego, que sólo las invoca.

/**
 * Barra que aparece mientras grabás: punto rojo + tiempo, nivel de entrada del micrófono y las dos
 * acciones. Se guarda como una nota más del juego (la transcripción llega después).
 */
@Composable
internal fun RecordingBar(
    elapsedMs: Long,
    amplitude: Float,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE5484D)))
        Text(
            formatDuration(elapsedMs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
        LevelBar(amplitude = amplitude, modifier = Modifier.weight(1f))
        TextButton(onClick = onDiscard) { Text("Discard") }
        Button(onClick = onSave, shape = Tokens.Shape.control) { Text("Save") }
    }
}

/** Nivel de entrada: confirma visualmente que el micrófono está tomando audio. */
@Composable
internal fun LevelBar(amplitude: Float, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(targetValue = amplitude.coerceIn(0f, 1f), label = "level")
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(Tokens.Shape.pill)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(level.coerceAtLeast(0.02f))
                .clip(Tokens.Shape.pill)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Reproductor mínimo de una nota de voz: play/pause + duración. Prepara el MediaPlayer en el primer
 * toque (el WAV es local, abre al instante) y lo libera al salir de la composición.
 */
@Composable
internal fun VoiceNotePlayer(path: String, durationMs: Long, modifier: Modifier = Modifier) {
    var playing by remember(path) { mutableStateOf(false) }
    var prepared by remember(path) { mutableStateOf(false) }
    val player = remember(path) { AudioClip(path) }

    DisposableEffect(path) {
        player.setOnCompletion { playing = false }
        onDispose { runCatching { player.release() } }
    }

    Row(
        modifier = modifier
            .clip(Tokens.Shape.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                runCatching {
                    if (!prepared) {
                        player.prepare()
                        prepared = true
                    }
                    if (player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        player.start()
                        playing = true
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause voice note" else "Play voice note",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}
