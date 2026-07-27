package com.gmoqa.diariogamer.db.shared

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.gmoqa.diariogamer.db.FullsetDatabase
import com.gmoqa.diariogamer.db.FullsetQueries
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<FullsetDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = FullsetDatabaseImpl.Schema

internal fun KClass<FullsetDatabase>.newInstance(driver: SqlDriver): FullsetDatabase =
    FullsetDatabaseImpl(driver)

private class FullsetDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), FullsetDatabase {
  override val fullsetQueries: FullsetQueries = FullsetQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 11

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE games (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    platform TEXT NOT NULL DEFAULT '',
          |    cover_url TEXT NOT NULL DEFAULT '',
          |    cover_path TEXT NOT NULL DEFAULT '',
          |    playing INTEGER NOT NULL DEFAULT 0,
          |    backlog INTEGER NOT NULL DEFAULT 0,
          |    created_at INTEGER NOT NULL,
          |    region TEXT NOT NULL DEFAULT '',
          |    release_year INTEGER,
          |    genre TEXT NOT NULL DEFAULT '',
          |    condition TEXT NOT NULL DEFAULT '',
          |    -- Vínculo con el catálogo oficial: `slug` es el identificador del juego ahí.
          |    slug TEXT NOT NULL DEFAULT '',
          |    publisher TEXT NOT NULL DEFAULT '',
          |    -- Código de producto de la copia física (SCUS-94163, MK-01077-00…).
          |    serial TEXT NOT NULL DEFAULT '',
          |    -- 1 = juego digital (no lo poseés): no cuenta como colección física. Ver 10.sqm.
          |    digital INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE notes (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    game_id INTEGER NOT NULL,
          |    text TEXT NOT NULL,
          |    created_at INTEGER NOT NULL,
          |    -- Nota de voz: ruta del WAV grabado y su duración. Vacío/0 = nota escrita a mano.
          |    -- La transcripción (cuando exista) se guarda en `text`, como cualquier otra nota.
          |    audio_path TEXT NOT NULL DEFAULT '',
          |    duration_ms INTEGER NOT NULL DEFAULT 0,
          |    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE photos (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    game_id INTEGER NOT NULL,
          |    path TEXT NOT NULL,
          |    caption TEXT NOT NULL DEFAULT '',
          |    created_at INTEGER NOT NULL,
          |    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE wishlist (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    platform TEXT NOT NULL,
          |    game TEXT NOT NULL,
          |    slug TEXT NOT NULL DEFAULT '',
          |    cover_url TEXT NOT NULL DEFAULT '',
          |    added_at INTEGER NOT NULL,
          |    UNIQUE(platform, game)
          |)
          """.trimMargin(), 0)
      driver.execute(null, "CREATE INDEX idx_notes_game ON notes(game_id)", 0)
      driver.execute(null, "CREATE INDEX idx_photos_game ON photos(game_id)", 0)
      return QueryResult.Unit
    }

    private fun migrateInternal(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
    ): QueryResult.Value<Unit> {
      if (oldVersion <= 1 && newVersion > 1) {
      }
      if (oldVersion <= 2 && newVersion > 2) {
      }
      if (oldVersion <= 3 && newVersion > 3) {
      }
      if (oldVersion <= 4 && newVersion > 4) {
      }
      if (oldVersion <= 5 && newVersion > 5) {
      }
      if (oldVersion <= 6 && newVersion > 6) {
        driver.execute(null, "ALTER TABLE games ADD COLUMN region TEXT NOT NULL DEFAULT ''", 0)
        driver.execute(null, "ALTER TABLE games ADD COLUMN release_year INTEGER", 0)
        driver.execute(null, "ALTER TABLE games ADD COLUMN genre TEXT NOT NULL DEFAULT ''", 0)
        driver.execute(null, "ALTER TABLE games ADD COLUMN condition TEXT NOT NULL DEFAULT ''", 0)
      }
      if (oldVersion <= 7 && newVersion > 7) {
        driver.execute(null, "ALTER TABLE notes ADD COLUMN audio_path TEXT NOT NULL DEFAULT ''", 0)
        driver.execute(null, "ALTER TABLE notes ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0",
            0)
      }
      if (oldVersion <= 8 && newVersion > 8) {
        driver.execute(null, "ALTER TABLE games ADD COLUMN slug TEXT NOT NULL DEFAULT ''", 0)
        driver.execute(null, "ALTER TABLE games ADD COLUMN publisher TEXT NOT NULL DEFAULT ''", 0)
      }
      if (oldVersion <= 9 && newVersion > 9) {
        driver.execute(null, "ALTER TABLE games ADD COLUMN serial TEXT NOT NULL DEFAULT ''", 0)
      }
      if (oldVersion <= 10 && newVersion > 10) {
        driver.execute(null, "ALTER TABLE games ADD COLUMN digital INTEGER NOT NULL DEFAULT 0", 0)
      }
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
      var lastVersion = oldVersion

      callbacks.filter { it.afterVersion in oldVersion until newVersion }
      .sortedBy { it.afterVersion }
      .forEach { callback ->
        migrateInternal(driver, oldVersion = lastVersion, newVersion = callback.afterVersion + 1)
        callback.block(driver)
        lastVersion = callback.afterVersion + 1
      }

      if (lastVersion < newVersion) {
        migrateInternal(driver, lastVersion, newVersion)
      }
      return QueryResult.Unit
    }
  }
}
