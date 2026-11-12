package com.kaspercito.makadimi.expect

// En escritorio la actualización sigue abriendo la página de Releases.
actual fun canInstallUpdateInApp(): Boolean = false

actual suspend fun downloadAndInstallUpdate(
    url: String,
    fileName: String,
    onProgress: (Float) -> Unit,
): UpdateInstallResult = UpdateInstallResult.Failed
