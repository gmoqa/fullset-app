package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.TrackingMode

/**
 * Lo que está en el Backlog, que **depende del modo**.
 *
 * En "Collection + diary" el Backlog es de tu colección física, así que solo entran los poseídos. En
 * "Diary only" no hay colección y los juegos se cargan como digitales: filtrar por físicos dejaría
 * el Backlog **siempre vacío**, así que ahí entran todos.
 */
fun pendientes(todos: List<Game>, fisicos: List<Game>, modo: TrackingMode): List<Game> =
    (if (modo == TrackingMode.DIARY_ONLY) todos else fisicos).filter { it.backlog }
