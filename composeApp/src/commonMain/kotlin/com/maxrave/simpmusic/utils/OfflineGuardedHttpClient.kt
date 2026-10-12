package com.maxrave.simpmusic.utils

import com.maxrave.common.OfflineMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.io.IOException

/** Corta las peticiones mientras el modo offline está activo (mismo efecto que el bloqueo de OkHttp). */
val OfflineGuardPlugin =
    createClientPlugin("OfflineGuard") {
        onRequest { _, _ ->
            if (OfflineMode.enabled) throw IOException(OfflineMode.BLOCKED_MESSAGE)
        }
    }

/**
 * Cliente Ktor para cargar imágenes en las pantallas. Úsalo siempre dentro de `remember { ... }`:
 * crear uno nuevo en cada recomposición (como se hacía antes) abre un motor y sus hilos por cada
 * repintado y descarta la caché del cargador de imágenes.
 */
fun offlineGuardedHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(OfflineGuardPlugin)
    }
