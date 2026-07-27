package com.gmoqa.diariogamer.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class FullsetQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllGames(mapper: (
    id: Long,
    name: String,
    platform: String,
    cover_url: String,
    cover_path: String,
    playing: Long,
    backlog: Long,
    created_at: Long,
    region: String,
    release_year: Long?,
    genre: String,
    condition: String,
    slug: String,
    publisher: String,
    serial: String,
    digital: Long,
    note_count: Long,
    photo_count: Long,
  ) -> T): Query<T> = Query(-85_258_163, arrayOf("games", "notes", "photos"), driver, "Fullset.sq",
      "selectAllGames", """
  |SELECT g.id, g.name, g.platform, g.cover_url, g.cover_path, g.playing, g.backlog, g.created_at,
  |    g.region, g.release_year, g.genre, g.condition, g.slug, g.publisher, g.serial, g.digital,
  |    (SELECT COUNT(*) FROM notes n WHERE n.game_id = g.id) AS note_count,
  |    (SELECT COUNT(*) FROM photos p WHERE p.game_id = g.id) AS photo_count
  |FROM games g
  |ORDER BY g.created_at DESC
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!,
      cursor.getLong(9),
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getString(12)!!,
      cursor.getString(13)!!,
      cursor.getString(14)!!,
      cursor.getLong(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectAllGames(): Query<SelectAllGames> = selectAllGames { id, name, platform,
      cover_url, cover_path, playing, backlog, created_at, region, release_year, genre, condition,
      slug, publisher, serial, digital, note_count, photo_count ->
    SelectAllGames(
      id,
      name,
      platform,
      cover_url,
      cover_path,
      playing,
      backlog,
      created_at,
      region,
      release_year,
      genre,
      condition,
      slug,
      publisher,
      serial,
      digital,
      note_count,
      photo_count
    )
  }

  public fun <T : Any> selectGameById(id: Long, mapper: (
    id: Long,
    name: String,
    platform: String,
    cover_url: String,
    cover_path: String,
    playing: Long,
    backlog: Long,
    created_at: Long,
    region: String,
    release_year: Long?,
    genre: String,
    condition: String,
    slug: String,
    publisher: String,
    serial: String,
    digital: Long,
    note_count: Long,
    photo_count: Long,
  ) -> T): Query<T> = SelectGameByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!,
      cursor.getLong(9),
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getString(12)!!,
      cursor.getString(13)!!,
      cursor.getString(14)!!,
      cursor.getLong(15)!!,
      cursor.getLong(16)!!,
      cursor.getLong(17)!!
    )
  }

  public fun selectGameById(id: Long): Query<SelectGameById> = selectGameById(id) { id_, name,
      platform, cover_url, cover_path, playing, backlog, created_at, region, release_year, genre,
      condition, slug, publisher, serial, digital, note_count, photo_count ->
    SelectGameById(
      id_,
      name,
      platform,
      cover_url,
      cover_path,
      playing,
      backlog,
      created_at,
      region,
      release_year,
      genre,
      condition,
      slug,
      publisher,
      serial,
      digital,
      note_count,
      photo_count
    )
  }

  public fun lastInsertRowId(): ExecutableQuery<Long> = Query(765_308_277, driver, "Fullset.sq",
      "lastInsertRowId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  public fun selectCoverPath(id: Long): Query<String> = SelectCoverPathQuery(id) { cursor ->
    cursor.getString(0)!!
  }

  public fun <T : Any> selectNotes(game_id: Long, mapper: (
    id: Long,
    game_id: Long,
    text: String,
    created_at: Long,
    audio_path: String,
    duration_ms: Long,
  ) -> T): Query<T> = SelectNotesQuery(game_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectNotes(game_id: Long): Query<Notes> = selectNotes(game_id) { id, game_id_, text,
      created_at, audio_path, duration_ms ->
    Notes(
      id,
      game_id_,
      text,
      created_at,
      audio_path,
      duration_ms
    )
  }

  public fun selectNoteAudioPath(id: Long): Query<String> = SelectNoteAudioPathQuery(id) { cursor ->
    cursor.getString(0)!!
  }

  public fun selectAllNoteAudioPaths(): Query<String> = Query(-1_308_233_666, arrayOf("notes"),
      driver, "Fullset.sq", "selectAllNoteAudioPaths",
      "SELECT audio_path FROM notes WHERE audio_path != ''") { cursor ->
    cursor.getString(0)!!
  }

  public fun <T : Any> selectPhotos(game_id: Long, mapper: (
    id: Long,
    game_id: Long,
    path: String,
    caption: String,
    created_at: Long,
  ) -> T): Query<T> = SelectPhotosQuery(game_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun selectPhotos(game_id: Long): Query<Photos> = selectPhotos(game_id) { id, game_id_,
      path, caption, created_at ->
    Photos(
      id,
      game_id_,
      path,
      caption,
      created_at
    )
  }

  public fun selectPhotoPath(id: Long): Query<String> = SelectPhotoPathQuery(id) { cursor ->
    cursor.getString(0)!!
  }

  public fun <T : Any> selectWishlist(mapper: (
    id: Long,
    platform: String,
    game: String,
    slug: String,
    cover_url: String,
    added_at: Long,
  ) -> T): Query<T> = Query(1_465_524_338, arrayOf("wishlist"), driver, "Fullset.sq",
      "selectWishlist",
      "SELECT id, platform, game, slug, cover_url, added_at FROM wishlist ORDER BY added_at DESC") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectWishlist(): Query<Wishlist> = selectWishlist { id, platform, game, slug,
      cover_url, added_at ->
    Wishlist(
      id,
      platform,
      game,
      slug,
      cover_url,
      added_at
    )
  }

  public fun insertGame(
    name: String,
    platform: String,
    cover_url: String,
    created_at: Long,
    region: String,
    release_year: Long?,
    genre: String,
    condition: String,
    slug: String,
    publisher: String,
    serial: String,
    digital: Long,
  ) {
    driver.execute(1_856_207_964, """
        |INSERT INTO games(name, platform, cover_url, created_at, region, release_year, genre, condition,
        |    slug, publisher, serial, digital)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 12) {
          bindString(0, name)
          bindString(1, platform)
          bindString(2, cover_url)
          bindLong(3, created_at)
          bindString(4, region)
          bindLong(5, release_year)
          bindString(6, genre)
          bindString(7, condition)
          bindString(8, slug)
          bindString(9, publisher)
          bindString(10, serial)
          bindLong(11, digital)
        }
    notifyQueries(1_856_207_964) { emit ->
      emit("games")
    }
  }

  public fun linkCatalogByName(
    slug: String,
    publisher: String,
    serial: String,
    `value`: Long?,
    name: String,
    platform: String,
  ) {
    driver.execute(-381_677_584, """
        |UPDATE games SET slug = ?, publisher = ?, serial = ?, release_year = COALESCE(release_year, ?)
        |WHERE name = ? AND platform = ?
        """.trimMargin(), 6) {
          bindString(0, slug)
          bindString(1, publisher)
          bindString(2, serial)
          bindLong(3, value)
          bindString(4, name)
          bindString(5, platform)
        }
    notifyQueries(-381_677_584) { emit ->
      emit("games")
    }
  }

  public fun updateMetadataByName(
    region: String,
    release_year: Long?,
    genre: String,
    condition: String,
    name: String,
  ) {
    driver.execute(1_300_286_859,
        """UPDATE games SET region = ?, release_year = ?, genre = ?, condition = ? WHERE name = ?""",
        5) {
          bindString(0, region)
          bindLong(1, release_year)
          bindString(2, genre)
          bindString(3, condition)
          bindString(4, name)
        }
    notifyQueries(1_300_286_859) { emit ->
      emit("games")
    }
  }

  public fun normalizeCondition(new: String, old: String) {
    driver.execute(-575_198_817, """UPDATE games SET condition = ? WHERE condition = ?""", 2) {
          bindString(0, new)
          bindString(1, old)
        }
    notifyQueries(-575_198_817) { emit ->
      emit("games")
    }
  }

  public fun setConditionByPlatform(
    condition: String,
    name: String,
    platform: String,
  ) {
    driver.execute(204_570_164,
        """UPDATE games SET condition = ? WHERE name = ? AND platform = ?""", 3) {
          bindString(0, condition)
          bindString(1, name)
          bindString(2, platform)
        }
    notifyQueries(204_570_164) { emit ->
      emit("games")
    }
  }

  public fun setPlaying(playing: Long, id: Long) {
    driver.execute(-924_428_419, """UPDATE games SET playing = ? WHERE id = ?""", 2) {
          bindLong(0, playing)
          bindLong(1, id)
        }
    notifyQueries(-924_428_419) { emit ->
      emit("games")
    }
  }

  public fun setBacklog(backlog: Long, id: Long) {
    driver.execute(-778_065_844, """UPDATE games SET backlog = ? WHERE id = ?""", 2) {
          bindLong(0, backlog)
          bindLong(1, id)
        }
    notifyQueries(-778_065_844) { emit ->
      emit("games")
    }
  }

  public fun updateCoverPath(cover_path: String, id: Long) {
    driver.execute(1_878_480_450, """UPDATE games SET cover_path = ? WHERE id = ?""", 2) {
          bindString(0, cover_path)
          bindLong(1, id)
        }
    notifyQueries(1_878_480_450) { emit ->
      emit("games")
    }
  }

  public fun updateCoverPathByName(cover_path: String, name: String) {
    driver.execute(-848_822_172, """UPDATE games SET cover_path = ? WHERE name = ?""", 2) {
          bindString(0, cover_path)
          bindString(1, name)
        }
    notifyQueries(-848_822_172) { emit ->
      emit("games")
    }
  }

  public fun updateCoverUrlByName(cover_url: String, name: String) {
    driver.execute(232_550_868, """UPDATE games SET cover_url = ? WHERE name = ?""", 2) {
          bindString(0, cover_url)
          bindString(1, name)
        }
    notifyQueries(232_550_868) { emit ->
      emit("games")
    }
  }

  public fun renameGameByName(name: String, name_: String) {
    driver.execute(1_768_684_867, """UPDATE games SET name = ? WHERE name = ?""", 2) {
          bindString(0, name)
          bindString(1, name_)
        }
    notifyQueries(1_768_684_867) { emit ->
      emit("games")
    }
  }

  public fun setPlayingByName(name: String) {
    driver.execute(-1_906_185_889, """UPDATE games SET playing = 1 WHERE name = ?""", 1) {
          bindString(0, name)
        }
    notifyQueries(-1_906_185_889) { emit ->
      emit("games")
    }
  }

  public fun deleteGame(id: Long) {
    driver.execute(-1_640_335_794, """DELETE FROM games WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(-1_640_335_794) { emit ->
      emit("games")
      emit("notes")
      emit("photos")
    }
  }

  public fun insertNote(
    game_id: Long,
    text: String,
    created_at: Long,
    audio_path: String,
    duration_ms: Long,
  ) {
    driver.execute(1_856_430_172,
        """INSERT INTO notes(game_id, text, created_at, audio_path, duration_ms) VALUES (?, ?, ?, ?, ?)""",
        5) {
          bindLong(0, game_id)
          bindString(1, text)
          bindLong(2, created_at)
          bindString(3, audio_path)
          bindLong(4, duration_ms)
        }
    notifyQueries(1_856_430_172) { emit ->
      emit("notes")
    }
  }

  public fun updateNoteText(text: String, id: Long) {
    driver.execute(-361_795_143, """UPDATE notes SET text = ? WHERE id = ?""", 2) {
          bindString(0, text)
          bindLong(1, id)
        }
    notifyQueries(-361_795_143) { emit ->
      emit("notes")
    }
  }

  public fun deleteNote(id: Long) {
    driver.execute(-1_640_113_586, """DELETE FROM notes WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(-1_640_113_586) { emit ->
      emit("notes")
    }
  }

  public fun insertPhoto(
    game_id: Long,
    path: String,
    caption: String,
    created_at: Long,
  ) {
    driver.execute(1_716_394_760,
        """INSERT INTO photos(game_id, path, caption, created_at) VALUES (?, ?, ?, ?)""", 4) {
          bindLong(0, game_id)
          bindString(1, path)
          bindString(2, caption)
          bindLong(3, created_at)
        }
    notifyQueries(1_716_394_760) { emit ->
      emit("photos")
    }
  }

  public fun deletePhoto(id: Long) {
    driver.execute(697_720_662, """DELETE FROM photos WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(697_720_662) { emit ->
      emit("photos")
    }
  }

  public fun insertWishlist(
    platform: String,
    game: String,
    slug: String,
    cover_url: String,
    added_at: Long,
  ) {
    driver.execute(1_836_359_983,
        """INSERT OR IGNORE INTO wishlist(platform, game, slug, cover_url, added_at) VALUES (?, ?, ?, ?, ?)""",
        5) {
          bindString(0, platform)
          bindString(1, game)
          bindString(2, slug)
          bindString(3, cover_url)
          bindLong(4, added_at)
        }
    notifyQueries(1_836_359_983) { emit ->
      emit("wishlist")
    }
  }

  public fun deleteWishlist(id: Long) {
    driver.execute(-1_539_747_295, """DELETE FROM wishlist WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(-1_539_747_295) { emit ->
      emit("wishlist")
    }
  }

  public fun clearWishlist() {
    driver.execute(-412_143_743, """DELETE FROM wishlist""", 0)
    notifyQueries(-412_143_743) { emit ->
      emit("wishlist")
    }
  }

  private inner class SelectGameByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("games", "notes", "photos", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("games", "notes", "photos", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(664_485_265, """
    |SELECT g.id, g.name, g.platform, g.cover_url, g.cover_path, g.playing, g.backlog, g.created_at,
    |    g.region, g.release_year, g.genre, g.condition, g.slug, g.publisher, g.serial, g.digital,
    |    (SELECT COUNT(*) FROM notes n WHERE n.game_id = g.id) AS note_count,
    |    (SELECT COUNT(*) FROM photos p WHERE p.game_id = g.id) AS photo_count
    |FROM games g
    |WHERE g.id = ?
    """.trimMargin(), mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Fullset.sq:selectGameById"
  }

  private inner class SelectCoverPathQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("games", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("games", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(129_327_983, """SELECT cover_path FROM games WHERE id = ?""", mapper, 1)
        {
      bindLong(0, id)
    }

    override fun toString(): String = "Fullset.sq:selectCoverPath"
  }

  private inner class SelectNotesQuery<out T : Any>(
    public val game_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("notes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("notes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_033_363_764, """
    |SELECT id, game_id, text, created_at, audio_path, duration_ms
    |FROM notes WHERE game_id = ? ORDER BY created_at DESC
    """.trimMargin(), mapper, 1) {
      bindLong(0, game_id)
    }

    override fun toString(): String = "Fullset.sq:selectNotes"
  }

  private inner class SelectNoteAudioPathQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("notes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("notes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-483_913_028, """SELECT audio_path FROM notes WHERE id = ?""", mapper,
        1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Fullset.sq:selectNoteAudioPath"
  }

  private inner class SelectPhotosQuery<out T : Any>(
    public val game_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("photos", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("photos", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_339_573_650,
        """SELECT id, game_id, path, caption, created_at FROM photos WHERE game_id = ? ORDER BY created_at DESC""",
        mapper, 1) {
      bindLong(0, game_id)
    }

    override fun toString(): String = "Fullset.sq:selectPhotos"
  }

  private inner class SelectPhotoPathQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("photos", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("photos", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_596_561_514, """SELECT path FROM photos WHERE id = ?""", mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Fullset.sq:selectPhotoPath"
  }
}
