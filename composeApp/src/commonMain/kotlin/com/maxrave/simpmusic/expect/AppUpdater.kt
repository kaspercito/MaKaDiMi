package com.maxrave.simpmusic.expect

/** Resultado de intentar instalar una actualización desde dentro de la app. */
enum class UpdateInstallResult {
    /** El APK se descargó y Android abrió su instalador. */
    InstallerOpened,

    /** Falta el permiso "instalar apps desconocidas"; se abrió la pantalla para activarlo. */
    NeedsPermission,

    /** Algo falló (sin internet, archivo dañado...). */
    Failed,
}

/** True si esta plataforma puede descargar e instalar el APK sin abrir el navegador (solo Android). */
expect fun canInstallUpdateInApp(): Boolean

/**
 * Descarga el APK de [url] y abre el instalador de Android.
 * [onProgress] recibe un valor de 0f a 1f (0f si el servidor no dice el tamaño).
 */
expect suspend fun downloadAndInstallUpdate(
    url: String,
    fileName: String,
    onProgress: (Float) -> Unit,
): UpdateInstallResult
