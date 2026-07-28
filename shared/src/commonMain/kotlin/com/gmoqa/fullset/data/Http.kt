package com.gmoqa.fullset.data

import io.ktor.client.HttpClient

/** Cliente HTTP con el engine de cada plataforma (OkHttp en Android, Darwin en iOS). */
expect fun createHttpClient(): HttpClient
