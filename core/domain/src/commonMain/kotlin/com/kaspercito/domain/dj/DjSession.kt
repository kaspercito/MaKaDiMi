package com.kaspercito.domain.dj

/**
 * Estado en memoria de la sesión del DJ (no se guarda en disco a propósito).
 *
 *  - [tasteProfile]: resumen de los gustos del oyente (artistas y canciones favoritas) que la hoja
 *    del DJ calcula al pulsar "Empezar DJ" y que el locutor usa para personalizar sus comentarios.
 *  - [consumeFreshProcess]: el DJ solo debe sonar cuando TÚ lo enciendes desde el botón DJ. Como el
 *    interruptor se guarda en disco, al abrir la app se apaga una sola vez por proceso.
 */
object DjSession {
    @Volatile
    var tasteProfile: String? = null

    /**
     * Gustos en el formato que espera el modelo que sugiere canciones (artistas más escuchados y
     * muestra de favoritas). Se guarda para poder pedir MÁS canciones del mismo estilo cuando la
     * cola del DJ se va vaciando, sin tener que volver a consultar la base de datos.
     */
    @Volatile
    var suggestionTaste: String? = null

    @Volatile
    private var processHandled = false

    /** Devuelve true solo la primera vez que se llama en este proceso. */
    fun consumeFreshProcess(): Boolean {
        if (processHandled) return false
        processHandled = true
        return true
    }
}
