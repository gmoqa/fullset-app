package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Búsqueda difusa de la colección: cada caso documentado en el KDoc de GameSearch. */
class GameSearchTest {

    private var nextId = 1L
    private fun game(name: String, platform: String = "SNES") = Game(
        id = nextId++, name = name, platform = platform,
        coverUrl = "", coverPath = "", playing = false, backlog = false, createdAt = 0,
    )

    private val shelf = listOf(
        game("Pokémon Red"),
        game("The Legend of Zelda: A Link to the Past"),
        game("Streets of Rage", platform = "Sega Genesis"),
        game("Super Metroid"),
        game("Final Fantasy VII", platform = "PlayStation"),
    )

    private fun names(query: String) = GameSearch.filter(shelf, query).map { it.name }

    @Test
    fun consultaVaciaDevuelveTodo() = assertEquals(shelf, GameSearch.filter(shelf, "  "))

    @Test
    fun ignoraAcentos() = assertTrue("Pokémon Red" in names("pokemon"))

    @Test
    fun palabrasEnCualquierOrden() = assertTrue("Streets of Rage" in names("rage streets"))

    @Test
    fun toleraUnErrorDeTipeo() = assertTrue("Super Metroid" in names("metroyd"))

    @Test
    fun siglasPorSubsecuencia() = assertTrue("Final Fantasy VII" in names("ffvii"))

    @Test
    fun buscaPorPlataforma() = assertEquals(listOf("Streets of Rage"), names("genesis"))

    @Test
    fun sinCoincidenciasDevuelveVacio() = assertTrue(names("chrono trigger").isEmpty())
}
