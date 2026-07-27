package com.gmoqa.diariogamer.data

import android.content.Context

/**
 * Holder del Context de la aplicación, para que el módulo `:shared` pueda leer assets (y en el
 * futuro DB/prefs) en Android sin recibir el Context en cada clase. Se inicializa una sola vez en
 * `FullsetApp.onCreate()`.
 */
object AndroidApp {
    lateinit var context: Context
        private set

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }
}

actual fun readTextAsset(path: String): String? =
    runCatching { AndroidApp.context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
