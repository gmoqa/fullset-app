package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Qué se baja y qué no.
 *
 * Es la parte de la sincronización que se puede probar sin red ni disco, y también la parte donde un
 * error se paga caro: bajar de menos deja catálogos viejos para siempre, y bajar de más gasta datos
 * del usuario en cada arranque.
 */
class CatalogSyncTest {

    private fun archivo(path: String, sha: String, bytes: Int = 10) =
        CatalogSync.ArchivoRemoto(path = path, bytes = bytes, sha256 = sha)

    private fun manifest(vararg files: CatalogSync.ArchivoRemoto, schema: Int = 1, version: String = "v1") =
        CatalogSync.Manifest(schema = schema, version = version, files = files.toList())

    @Test
    fun sinNadaDescargadoSeComparaContraLoHorneado() {
        val remoto = manifest(archivo("catalogs/ps5-usa.json", "nuevo"), archivo("catalogs/nes-usa.json", "igual"))
        val horneado = manifest(archivo("catalogs/ps5-usa.json", "viejo"), archivo("catalogs/nes-usa.json", "igual"))

        val pendientes = CatalogSync.aDescargar(remoto, horneado, CatalogSync.Estado())
        assertEquals(listOf("catalogs/ps5-usa.json"), pendientes.map { it.path },
            "solo el que cambió; bajar el que ya está igual es gastar datos ajenos")
    }

    @Test
    fun loYaDescargadoNoSeVuelveABajar() {
        val remoto = manifest(archivo("catalogs/ps5-usa.json", "nuevo"))
        val horneado = manifest(archivo("catalogs/ps5-usa.json", "viejo"))
        val estado = CatalogSync.Estado(descargados = mapOf("catalogs/ps5-usa.json" to "nuevo"))

        assertTrue(CatalogSync.aDescargar(remoto, horneado, estado).isEmpty())
    }

    @Test
    fun loDescargadoManda_aunqueLoHorneadoCoincidaConOtroHash() {
        // Lo que está en juego es lo descargado, no lo horneado: si el remoto avanzó otra vez, hay
        // que bajarlo de nuevo aunque el APK siga trayendo la versión original.
        val remoto = manifest(archivo("catalogs/ps5-usa.json", "v3"))
        val horneado = manifest(archivo("catalogs/ps5-usa.json", "v1"))
        val estado = CatalogSync.Estado(descargados = mapOf("catalogs/ps5-usa.json" to "v2"))

        assertEquals(listOf("catalogs/ps5-usa.json"), CatalogSync.aDescargar(remoto, horneado, estado).map { it.path })
    }

    @Test
    fun unArchivoNuevoEnElRemotoSeBaja() {
        // Una consola agregada después de publicar el APK: no está horneada en ningún lado.
        val remoto = manifest(archivo("catalogs/switch-usa.json", "abc"))
        val pendientes = CatalogSync.aDescargar(remoto, manifest(), CatalogSync.Estado())
        assertEquals(listOf("catalogs/switch-usa.json"), pendientes.map { it.path })
    }

    @Test
    fun unaEntradaSinHashSeIgnora() {
        // Sin `sha256` no hay forma de saber si cambió ni de verificar lo que llegue. Bajarla sería
        // pisar un catálogo bueno con algo que no se puede comprobar.
        val remoto = manifest(archivo("catalogs/roto.json", ""))
        assertTrue(CatalogSync.aDescargar(remoto, manifest(), CatalogSync.Estado()).isEmpty())
    }

    @Test
    fun elEsquemaSoportadoEsElQueDeclaraElManifestDelRepo() {
        // Si esto se desincroniza, o la app se niega a actualizar para siempre, o intenta leer un
        // formato que no entiende. Las dos son peores que no tener la función.
        assertEquals(1, CatalogSync.SCHEMA_SOPORTADO)
    }
}
