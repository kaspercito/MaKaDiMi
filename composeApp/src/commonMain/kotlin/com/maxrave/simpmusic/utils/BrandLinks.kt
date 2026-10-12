package com.maxrave.simpmusic.utils

/**
 * Único lugar donde se define el "dueño" de la app MaKaDiMi.
 *
 * Cambia [GITHUB_USER] (y [REPO_NAME] si tu repositorio se llama distinto) y todos los
 * enlaces de la app (Acerca de, Ajustes, diálogo de reseña, Discord Rich Presence...) se actualizan solos.
 */
object BrandLinks {
    const val APP_NAME = "MaKaDiMi"

    /** Tu usuario de GitHub. */
    const val GITHUB_USER = "kaspercito"

    /** Nombre de tu repositorio de la app. */
    const val REPO_NAME = "MaKaDiMi"

    const val GITHUB_PROFILE = "https://github.com/$GITHUB_USER"
    const val REPO_URL = "$GITHUB_PROFILE/$REPO_NAME"
    const val ISSUES_URL = "$REPO_URL/issues"
    const val RELEASES_URL = "$REPO_URL/releases"

    /** Proyecto original en el que se basa MaKaDiMi (GPL-3.0). Se mantiene como atribución. */
    const val UPSTREAM_URL = "https://github.com/maxrave-dev/SimpMusic"
}
