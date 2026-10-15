package com.kaspercito.common

/**
 * Clave de DataStore del ajuste "Modo offline" (valor "TRUE" / "FALSE", igual que el resto de ajustes booleanos guardados como texto).
 */
const val OFFLINE_MODE = "offline_mode"

/**
 * Interruptor global del modo offline.
 *
 * Cuando está activo, ninguna petición de red de la app sale al exterior (API de YouTube Music, letras,
 * imágenes, streaming de canciones no descargadas, descargas nuevas...). Lo que ya está descargado o en caché
 * sigue funcionando. Se guarda en memoria para que las capas de red lo consulten sin tocar DataStore en cada
 * petición; quien cambia el ajuste (SettingsViewModel) y quien arranca la app (MainActivity / DesktopApp)
 * lo mantienen sincronizado con el valor guardado.
 */
object OfflineMode {
    @Volatile
    var enabled: Boolean = false

    /** Mensaje de la excepción que se lanza al bloquear una petición. */
    const val BLOCKED_MESSAGE = "Offline mode is enabled: network request blocked"
}
