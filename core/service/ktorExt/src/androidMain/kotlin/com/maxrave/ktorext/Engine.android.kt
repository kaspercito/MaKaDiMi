package com.maxrave.ktorext

import com.maxrave.common.OfflineMode
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Corta cualquier petición mientras el modo offline está activo. Lanza una IOException normal, que es lo
 * mismo que verían las capas superiores sin conexión, así que ya saben tratarla.
 */
private object OfflineBlockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (OfflineMode.enabled) throw IOException(OfflineMode.BLOCKED_MESSAGE)
        return chain.proceed(chain.request())
    }
}

actual fun getEngine(): HttpClientEngineFactory<HttpClientEngineConfig> =
    object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
            OkHttp.create {
                block()
                addInterceptor(OfflineBlockInterceptor)
            }
    }
