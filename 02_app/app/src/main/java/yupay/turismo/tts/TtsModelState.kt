package yupay.turismo.tts

/**
 * Estado observable de un modelo concreto del catálogo. Se expone como `StateFlow<TtsModelState>`
 * por [yupay.turismo.tts.download.TtsDownloadRepository.stateOf].
 */
sealed interface TtsModelState {
    /** No está en disco. La tarjeta muestra el botón "Descargar". */
    data object NotDownloaded : TtsModelState

    /** Descarga/verificación/extracción en curso. [progress] en 0f..1f (−1 = indeterminado). */
    data class Downloading(val progress: Float) : TtsModelState

    /**
     * Descarga detenida con checkpoint guardado (los ficheros parciales se conservan).
     * [progress] es el avance al pausar (0f..1f). [waitingForNetwork] distingue:
     *  - `false` → el usuario pulsó "Pausar" (botón Reanudar disponible si hay conexión).
     *  - `true`  → encolada pero sin internet (solo botón "Anular"; reanuda sola al volver la red).
     */
    data class Paused(val progress: Float, val waitingForNetwork: Boolean) : TtsModelState

    /** Descargado y verificado, pero NO es el modelo activo de su idioma. Muestra "Activar"/"Eliminar". */
    data object Downloaded : TtsModelState

    /** Descargado y, además, marcado como activo para su idioma. Muestra "En uso"/"Eliminar". */
    data object Active : TtsModelState

    /** Falló la descarga, la verificación SHA-256 o la extracción. */
    data class Error(val message: String) : TtsModelState

    /** ¿El modelo está presente y usable en disco? (Descargado o Activo.) */
    val isReady: Boolean get() = this is Downloaded || this is Active
}
