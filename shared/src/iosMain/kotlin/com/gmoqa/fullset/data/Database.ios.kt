package com.gmoqa.fullset.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.gmoqa.fullset.db.FullsetDatabase

actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(
    schema = FullsetDatabase.Schema,
    name = "diario_gamer.db",
    // Mantiene ON DELETE CASCADE (igual que Android).
    onConfiguration = { config ->
        config.copy(extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true))
    },
)
