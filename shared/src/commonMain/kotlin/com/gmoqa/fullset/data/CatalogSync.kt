package com.gmoqa.fullset.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Mantiene los catálogos al día **sin publicar un APK nuevo**.
 *
 * El repositorio público hace de backend. No hay servidor que mantener: son archivos estáticos
 * servidos por el CDN de GitHub, y el camino de escritura es un pull request. Un catálogo mejorado
 * llega a los teléfonos cuando se mergea, no cuando alguien publica una versión.
 *
 * Esto habilita el modelo que las consolas modernas necesitan: **empezar con una lista incompleta e
 * irla completando semana a semana**. Con los catálogos horneados en el APK, una lista a medio
 * hacer se congelaba hasta la próxima release; acá crece sola.
 *
 * ## Cómo funciona
 *
 * El APK sigue trayendo los catálogos horneados, y esos son el **piso**: sin red, sin permiso o con
 * el servidor caído, la app funciona igual que siempre. Encima puede haber una copia descargada, que
 * gana cuando existe.
 *
 * `manifest.json` es lo que hace esto posible y fue escrito para esto: trae el `sha256` de cada
 * archivo, así que se puede preguntar *"¿cambió?"* bajando 24 KB en vez de 18 MB.
 *
 * ## Lo que se decidió, y por qué
 *
 * - **No se calcula SHA-256 en el cliente.** El hash sirve para dos cosas y solo una necesita
 *   calcularlo. Para *detectar cambios* alcanza con recordar el hash que **declaró el manifest**
 *   cuando se bajó cada archivo: se comparan dos textos. Para *verificar integridad* se usan los
 *   `bytes` del manifest más el hecho de que un JSON truncado no parsea — y HTTPS ya cubre la
 *   manipulación en tránsito. Implementar SHA-256 en `commonMain` habría sido código sin dueño.
 *
 * - **Un `schema` mayor al conocido detiene todo.** Lo pedía el propio generador del manifest: si el
 *   formato cambia, una app vieja tiene que **quedarse con lo horneado** en vez de intentar leer algo
 *   que no entiende. Es la diferencia entre una app que no se actualiza y una que se rompe sola.
 *
 * - **Al actualizar el APK se descarta lo descargado.** Si no, una copia vieja bajada con la versión
 *   anterior le ganaría a un catálogo horneado más nuevo, y la app mostraría datos peores después de
 *   actualizar. Cuesta volver a bajar lo que cambió una vez; la alternativa es un error silencioso y
 *   difícil de ver.
 *
 * - **`platforms.json` se escribe último y solo si todo lo demás salió bien.** Lleva los conteos por
 *   consola y qué archivo usa cada una. Aplicarlo con catálogos a medio bajar haría que la app
 *   prometiera consolas que no tiene o contara juegos que no llegaron.
 */
object CatalogSync {

    /**
     * Sube cada vez que se aplicó una actualización.
     *
     * `GameCatalog` cachea por archivo, así que sin esto la app seguiría mostrando los catálogos
     * viejos hasta reiniciarse — justo lo que esta función viene a evitar. La UI la observa y vuelve
     * a construir el catálogo cuando cambia.
     */
    val revision = MutableStateFlow(0)

    /** La forma de manifest que este código entiende. Ver [Resultado.EsquemaNuevo]. */
    const val SCHEMA_SOPORTADO = 1

    private const val BASE =
        "https://raw.githubusercontent.com/gmoqa/fullset-app/main/data/catalogs/"

    /** El registro va último: describe a los demás. */
    private const val REGISTRO = "catalogs/platforms.json"

    @Serializable
    data class ArchivoRemoto(val path: String, val bytes: Int = 0, val sha256: String = "")

    @Serializable
    data class Manifest(
        val schema: Int = 0,
        val version: String = "",
        val files: List<ArchivoRemoto> = emptyList(),
    )

    /** Lo que quedó guardado de la última sincronización que funcionó. */
    @Serializable
    data class Estado(
        /** Versión del manifest **horneado** cuando se bajó esto. Si cambia, lo bajado se descarta. */
        val bakedVersion: String = "",
        /** `path -> sha256` que declaraba el manifest para lo que efectivamente está en disco. */
        val descargados: Map<String, String> = emptyMap(),
        /** Cuándo se consultó por última vez, en milisegundos. Evita preguntar en cada arranque. */
        val ultimoChequeo: Long = 0,
    )

    sealed interface Resultado {
        /** Ya estaba todo al día, o no pasó suficiente tiempo desde el último chequeo. */
        data object SinCambios : Resultado
        data class Actualizado(val archivos: Int) : Resultado
        /** El repositorio usa un formato que esta versión de la app no entiende. */
        data class EsquemaNuevo(val remoto: Int) : Resultado
        data class Falló(val motivo: String) : Resultado
    }

    /**
     * Decide qué hay que bajar. **Función pura**: no toca red ni disco, así que se puede probar.
     *
     * Un archivo entra a la lista si el hash que declara el manifest remoto difiere del que está en
     * juego hoy — que es el descargado si lo hay, y si no el horneado.
     */
    fun aDescargar(remoto: Manifest, horneado: Manifest, estado: Estado): List<ArchivoRemoto> {
        val porHorneado = horneado.files.associate { it.path to it.sha256 }
        return remoto.files.filter { archivo ->
            val vigente = estado.descargados[archivo.path] ?: porHorneado[archivo.path]
            archivo.sha256.isNotBlank() && archivo.sha256 != vigente
        }
    }

    /**
     * Corre una sincronización. Pensada para llamarse en segundo plano: **nunca lanza**, y ante
     * cualquier problema devuelve [Resultado.Falló] dejando lo que había intacto.
     *
     * [ahora] y [cadaMs] hacen el throttle explícito en vez de esconder un reloj adentro: así el
     * test decide cuánto tiempo pasó.
     */
    suspend fun sincronizar(
        ahora: Long,
        cadaMs: Long = 24 * 60 * 60 * 1000L,
        forzar: Boolean = false,
    ): Resultado = withContext(ioDispatcher) {
        val horneado = leerHorneado() ?: return@withContext Resultado.Falló("sin manifest horneado")
        var estado = leerEstado()

        // El APK se actualizó: lo bajado con la versión anterior puede ser más viejo que lo que
        // ahora trae horneado, así que se descarta en vez de arriesgar mostrar datos peores.
        if (estado.bakedVersion != horneado.version) {
            borrarDescargados()
            estado = Estado(bakedVersion = horneado.version)
            guardarEstado(estado)
        }

        if (!forzar && ahora - estado.ultimoChequeo < cadaMs) return@withContext Resultado.SinCambios

        val cliente = createHttpClient()
        try {
            val remoto = AppJson.decodeFromString<Manifest>(cliente.get(BASE + "manifest.json").bodyAsText())
            if (remoto.schema > SCHEMA_SOPORTADO) {
                // No es un error: es una app vieja haciendo lo correcto.
                guardarEstado(estado.copy(ultimoChequeo = ahora))
                return@withContext Resultado.EsquemaNuevo(remoto.schema)
            }

            val pendientes = aDescargar(remoto, horneado, estado)
            if (pendientes.isEmpty()) {
                guardarEstado(estado.copy(ultimoChequeo = ahora))
                return@withContext Resultado.SinCambios
            }

            // El registro va al final: si algún catálogo falla, mejor no prometer consolas que no
            // están. Se ordena, no se filtra.
            val orden = pendientes.sortedBy { if (it.path == REGISTRO) 1 else 0 }
            val logrados = mutableMapOf<String, String>()
            for (archivo in orden) {
                if (archivo.path == REGISTRO && logrados.size < orden.size - 1) break
                val cuerpo = runCatching { cliente.get(BASE + nombre(archivo.path)).bodyAsText() }
                    .getOrNull() ?: break
                if (!esVálido(cuerpo, archivo)) break
                if (!FileStore.writeTextAtomic(rutaLocal(archivo.path), cuerpo)) break
                logrados[archivo.path] = archivo.sha256
            }

            guardarEstado(
                estado.copy(
                    descargados = estado.descargados + logrados,
                    ultimoChequeo = ahora,
                ),
            )
            if (logrados.isEmpty()) {
                Resultado.Falló("no se pudo bajar ninguno")
            } else {
                revision.value += 1
                Resultado.Actualizado(logrados.size)
            }
        } catch (e: Exception) {
            Resultado.Falló(e.message ?: "error de red")
        } finally {
            runCatching { cliente.close() }
        }
    }

    /**
     * Si lo que llegó es lo que se esperaba.
     *
     * Se mira el **largo** y que **parsee**, no el hash: una descarga cortada —que es el modo de
     * falla real— falla las dos cosas, y HTTPS ya garantiza que nadie lo cambió en el camino.
     */
    private fun esVálido(cuerpo: String, archivo: ArchivoRemoto): Boolean {
        if (archivo.bytes > 0 && cuerpo.encodeToByteArray().size != archivo.bytes) return false
        return runCatching { AppJson.parseToJsonElement(cuerpo) }.isSuccess
    }

    /** El contenido efectivo de un catálogo: lo descargado si está, y si no lo horneado. */
    fun leerCatalogo(path: String): String? =
        FileStore.readText(rutaLocal("catalogs/$path")) ?: readTextAsset("catalogs/$path")

    private fun nombre(path: String) = path.removePrefix("catalogs/")
    private fun rutaLocal(path: String) = "${FileStore.catalogsDir}/${nombre(path)}"

    private fun leerHorneado(): Manifest? =
        readTextAsset("catalogs/manifest.json")
            ?.let { runCatching { AppJson.decodeFromString<Manifest>(it) }.getOrNull() }

    private fun leerEstado(): Estado =
        FileStore.readText("${FileStore.catalogsDir}/.estado.json")
            ?.let { runCatching { AppJson.decodeFromString<Estado>(it) }.getOrNull() }
            ?: Estado()

    private fun guardarEstado(estado: Estado) {
        FileStore.writeTextAtomic(
            "${FileStore.catalogsDir}/.estado.json",
            AppJson.encodeToString(Estado.serializer(), estado),
        )
    }

    private fun borrarDescargados() {
        FileStore.listFilePaths(FileStore.catalogsDir).forEach { FileStore.delete(it) }
    }
}
