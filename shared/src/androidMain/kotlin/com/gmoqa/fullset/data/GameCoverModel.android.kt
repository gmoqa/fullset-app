package com.gmoqa.fullset.data

import java.io.File

// Coil en Android acepta un File directo para la carátula guardada localmente.
actual fun localCoverModel(path: String): Any = File(path)
