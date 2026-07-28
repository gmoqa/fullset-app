package com.gmoqa.fullset

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gmoqa.fullset.data.AndroidApp

/**
 * Configura el `ImageLoader` singleton de Coil 3 (registra el fetcher de red OkHttp).
 * Coil 3 no lo activa solo; hay que declararlo aquí. También inicializa el holder de Context del
 * módulo `:shared` (para que pueda leer assets).
 */
class FullsetApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AndroidApp.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
