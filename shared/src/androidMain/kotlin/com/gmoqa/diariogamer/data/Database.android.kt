package com.gmoqa.diariogamer.data

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gmoqa.diariogamer.db.FullsetDatabase

actual fun createSqlDriver(): SqlDriver = AndroidSqliteDriver(
    schema = FullsetDatabase.Schema,
    context = AndroidApp.context,
    name = "diario_gamer.db",
    callback = object : AndroidSqliteDriver.Callback(FullsetDatabase.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true) // conserva ON DELETE CASCADE
        }
    },
)
