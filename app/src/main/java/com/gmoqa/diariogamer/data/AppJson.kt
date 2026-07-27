package com.gmoqa.diariogamer.data

import kotlinx.serialization.json.Json

/** Instancia JSON compartida (multiplataforma). Tolerante a claves extra y nulos. */
internal val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
