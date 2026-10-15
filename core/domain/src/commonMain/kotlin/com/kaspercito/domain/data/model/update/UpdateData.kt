package com.kaspercito.domain.data.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    /** URL de descarga directa del APK dentro del Release (null si no hay). */
    val assetUrl: String? = null,
    /** Nombre del archivo APK del Release. */
    val assetName: String? = null,
)