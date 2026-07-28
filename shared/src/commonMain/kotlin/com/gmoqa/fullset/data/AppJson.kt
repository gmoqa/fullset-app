package com.gmoqa.fullset.data

import kotlinx.serialization.json.Json

/** Instancia JSON compartida (multiplataforma). Tolerante a claves extra y nulos. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
