package com.gmoqa.fullset.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gmoqa.fullset.db.FullsetDatabase
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `fillFromCatalog` completa la colección cuando los catálogos mejoran. La regla no negociable es
 * que **solo rellena huecos**: lo que el usuario cargó a mano es la verdad y no se pisa nunca.
 */
class CatalogRefreshTest {

    private fun repo(): DiaryRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FullsetDatabase.Schema.create(driver)
        return DiaryRepository(driver, MapSettings())
    }

    @Test
    fun completaLosCamposVacios() {
        val repo = repo()
        val id = repo.addGame(name = "Sonic the Hedgehog", platform = "Sega Genesis", slug = "sonic-the-hedgehog")

        repo.fillFromCatalog(id, serial = "1001", publisher = "Sega", genre = "Platform", year = 1991)

        val game = repo.games().single()
        assertEquals("1001", game.serial)
        assertEquals("Sega", game.publisher)
        assertEquals("Platform", game.genre)
        assertEquals(1991, game.releaseYear)
    }

    @Test
    fun nuncaPisaLoQueYaEstaCargado() {
        val repo = repo()
        val id = repo.addGame(
            name = "Sonic the Hedgehog", platform = "Sega Genesis", slug = "sonic-the-hedgehog",
            releaseYear = 1992, genre = "Mi género", publisher = "Mi editora", serial = "MI-SERIAL",
        )

        repo.fillFromCatalog(id, serial = "1001", publisher = "Sega", genre = "Platform", year = 1991)

        val game = repo.games().single()
        assertEquals("MI-SERIAL", game.serial)
        assertEquals("Mi editora", game.publisher)
        assertEquals("Mi género", game.genre)
        assertEquals(1992, game.releaseYear)
    }

    @Test
    fun completaSoloElHuecoDejandoElResto() {
        val repo = repo()
        // Caso real de la colección: tiene año y editora, le falta el catalog number.
        val id = repo.addGame(
            name = "Ecco the Dolphin", platform = "Sega CD", slug = "ecco-the-dolphin",
            releaseYear = 1993, publisher = "Sega",
        )

        repo.fillFromCatalog(id, serial = "4408", publisher = "Otra", genre = "Action", year = 1900)

        val game = repo.games().single()
        assertEquals("4408", game.serial)       // estaba vacío → se completa
        assertEquals("Action", game.genre)      // estaba vacío → se completa
        assertEquals("Sega", game.publisher)    // ya estaba → intacto
        assertEquals(1993, game.releaseYear)    // ya estaba → intacto
    }

    @Test
    fun unValorVacioDelCatalogoNoBorraNada() {
        val repo = repo()
        val id = repo.addGame(
            name = "Sonic CD", platform = "Sega CD", slug = "sonic-cd",
            genre = "Platform", publisher = "Sega", serial = "4407",
        )

        // El catálogo no siempre trae todo (la editora está al 0% en los catálogos Sega).
        repo.fillFromCatalog(id, serial = "", publisher = "", genre = "", year = null)

        val game = repo.games().single()
        assertEquals("4407", game.serial)
        assertEquals("Sega", game.publisher)
        assertEquals("Platform", game.genre)
    }

    @Test
    fun borraLosSerialesDeOtraRegion() {
        val repo = repo()
        // El catálogo SNES traía códigos europeos y japoneses en una lista NTSC-U.
        val euro = repo.addGame(name = "Super Metroid", platform = "Super Nintendo", serial = "SNSP-RI")
        val jp = repo.addGame(name = "SimAnt", platform = "Super Nintendo", serial = "SHVC-AN")
        val ok = repo.addGame(name = "Super Mario World", platform = "Super Nintendo", serial = "SNS-MW-USA")
        val other = repo.addGame(name = "Sonic", platform = "Sega Genesis", serial = "SNSP-XX")

        repo.clearForeignSerials("Super Nintendo", "SNSP-%", "SHVC-%")

        val bySerial = repo.games().associate { it.id to it.serial }
        assertEquals("", bySerial[euro])
        assertEquals("", bySerial[jp])
        assertEquals("SNS-MW-USA", bySerial[ok], "el serial correcto no se toca")
        assertEquals("SNSP-XX", bySerial[other], "solo afecta a la plataforma indicada")
    }

    @Test
    fun corrigeElSerialViejoPeroRespetaTuEdicion() {
        val repo = repo()
        // Los dos Lost Vikings compartían SNS-LV por el match laxo del generador legacy.
        val stale = repo.addGame(name = "Lost Vikings 2", platform = "Super Nintendo",
            slug = "lost-vikings-2", serial = "SNS-LV")
        // Este lo corrigió el usuario a mano antes de que llegara la migración.
        val edited = repo.addGame(name = "The Lost Vikings", platform = "Super Nintendo",
            slug = "the-lost-vikings", serial = "LO QUE DICE MI CARTUCHO")

        repo.updateSerialIfEquals("Super Nintendo", "lost-vikings-2", "SNS-LV", "SNS-ALVE-USA")
        repo.updateSerialIfEquals("Super Nintendo", "the-lost-vikings", "SNS-LV", "SNS-LV-USA")

        val bySerial = repo.games().associate { it.id to it.serial }
        assertEquals("SNS-ALVE-USA", bySerial[stale], "el valor viejo se corrige")
        assertEquals("LO QUE DICE MI CARTUCHO", bySerial[edited], "tu edición manda")
    }
}
