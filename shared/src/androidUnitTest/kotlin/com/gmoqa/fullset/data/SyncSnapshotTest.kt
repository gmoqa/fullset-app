package com.gmoqa.fullset.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gmoqa.fullset.db.FullsetDatabase
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El merge de respaldo es la lógica más delicada de la app: un bug acá pierde colección o notas.
 * Corre sobre una BD SQLite real en memoria (driver JDBC, solo JVM) con el repo de verdad.
 */
class SyncSnapshotTest {

    private fun repo(): DiaryRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FullsetDatabase.Schema.create(driver)
        return DiaryRepository(driver, MapSettings())
    }

    /** Colección chica de referencia: un juego con todo (notas, firstPlayed, flags) y wishlist. */
    private fun seedSource(): DiaryRepository {
        val repo = repo()
        val id = repo.addGame(
            name = "Super Metroid", platform = "SNES", coverUrl = "https://covers/sm.png",
            createdAt = 1000, region = "NTSC-U", releaseYear = 1994, genre = "Action",
            condition = "complete", slug = "super-metroid", publisher = "Nintendo",
            serial = "SNS-RI-USA",
        )
        repo.setPlaying(id, true)
        repo.setBacklog(id, true)
        repo.setFirstPlayed(id, "1996-07")
        repo.addNote(id, "Primera nota", createdAt = 2000)
        repo.addNote(id, "Transcripción de voz", createdAt = 3000, durationMs = 4500)
        repo.addGame(name = "Sonic 2", platform = "Sega Genesis", createdAt = 1500, slug = "sonic-2")
        repo.addToWishlist("SNES", "EarthBound", "earthbound", "", addedAt = 4000)
        return repo
    }

    @Test
    fun importarEnBaseVaciaTraeTodo() {
        val snapshot = seedSource().exportSnapshot()
        val dest = repo()

        val result = dest.importSnapshot(snapshot)

        assertEquals(2, result.newGames)
        assertEquals(2, result.newNotes)
        assertEquals(1, result.newWishlist)

        val metroid = dest.games().first { it.name == "Super Metroid" }
        assertEquals("SNES", metroid.platform)
        assertEquals("complete", metroid.condition)
        assertEquals("1996-07", metroid.firstPlayed)
        assertEquals(1994, metroid.releaseYear)
        assertTrue(metroid.playing)
        assertTrue(metroid.backlog)
        assertEquals(1000, metroid.createdAt)

        val notes = dest.notes(metroid.id).sortedBy { it.createdAt }
        assertEquals(listOf("Primera nota", "Transcripción de voz"), notes.map { it.text })
        // La nota de voz conserva su duración (aunque el audio en sí no viaja).
        assertEquals(4500, notes[1].durationMs)

        assertEquals(listOf("EarthBound"), dest.wishlist().map { it.game })
    }

    @Test
    fun reimportarEsIdempotente() {
        val snapshot = seedSource().exportSnapshot()
        val dest = repo()
        dest.importSnapshot(snapshot)

        val second = dest.importSnapshot(snapshot)

        assertTrue(second.nothingNew)
        assertEquals(2, dest.games().size)
        assertEquals(1, dest.wishlist().size)
    }

    @Test
    fun elMergeNuncaPisaLoLocal() {
        val dest = repo()
        // El mismo juego ya existe acá con otra condición y su propia nota.
        val localId = dest.addGame(
            name = "Super Metroid", platform = "SNES", slug = "super-metroid",
            condition = "loose", createdAt = 500,
        )
        dest.addNote(localId, "Nota local", createdAt = 100)

        val result = dest.importSnapshot(seedSource().exportSnapshot())

        // El juego no se duplica y lo local queda intacto (condición y firstPlayed sin tocar).
        assertEquals(1, result.newGames) // solo Sonic 2
        val metroid = dest.games().first { it.name == "Super Metroid" }
        assertEquals("loose", metroid.condition)
        assertEquals("", metroid.firstPlayed)
        // Las notas del snapshot que faltaban sí entran; la local sobrevive.
        val texts = dest.notes(localId).map { it.text }
        assertEquals(3, texts.size)
        assertTrue("Nota local" in texts)
    }

    @Test
    fun mismaClaveNaturalPorSlugAunqueCambieElNombre() {
        val dest = repo()
        // Renombrado a mano en este dispositivo, pero mismo slug → es el mismo juego.
        dest.addGame(name = "Metroid 3", platform = "SNES", slug = "super-metroid")

        val result = dest.importSnapshot(seedSource().exportSnapshot())

        assertEquals(1, result.newGames) // solo Sonic 2; Super Metroid ya estaba por slug
        assertEquals(2, dest.games().size)
    }

    @Test
    fun jsonIdaYVuelta() {
        val snapshot = seedSource().exportSnapshot()

        val decoded = syncSnapshotFromJson(snapshot.toJson())

        assertEquals(snapshot.games, decoded.games)
        assertEquals(snapshot.wishlist, decoded.wishlist)
    }

    @Test
    fun jsonViejoSinFirstPlayedSigueImportando() {
        // Un backup exportado antes de que existiera el campo: debe decodificar con default "".
        val legacy = """{"version":1,"exportedAt":9,"games":[{"name":"Sonic 2","platform":"Sega Genesis"}]}"""

        val dest = repo()
        val result = dest.importSnapshot(syncSnapshotFromJson(legacy))

        assertEquals(1, result.newGames)
        assertEquals("", dest.games().single().firstPlayed)
    }
}
