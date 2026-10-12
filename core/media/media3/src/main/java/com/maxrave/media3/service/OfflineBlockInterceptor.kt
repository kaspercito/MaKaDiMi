package com.maxrave.media3.service

import com.maxrave.common.OfflineMode
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Bloquea las peticiones de red del reproductor y de las descargas mientras el modo offline está activo.
 * Las canciones ya descargadas o en caché no pasan por aquí (se leen de disco), así que siguen sonando.
 */
class OfflineBlockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (OfflineMode.enabled) throw IOException(OfflineMode.BLOCKED_MESSAGE)
        return chain.proceed(chain.request())
    }
}
