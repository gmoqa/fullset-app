package com.gmoqa.fullset.data

import app.cash.sqldelight.db.SqlDriver

/** Driver SQLite de cada plataforma (AndroidSqliteDriver en Android, NativeSqliteDriver en iOS). */
expect fun createSqlDriver(): SqlDriver
